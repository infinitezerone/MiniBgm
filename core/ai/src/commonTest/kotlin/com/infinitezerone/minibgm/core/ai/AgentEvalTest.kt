package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.ai.di.aiModule
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * 本地黄金任务评估 harness：改提示词 / 换模型后跑一遍，验证工具路由与回答底线没有退化。
 *
 * 真实调用 LLM 端点，会产生费用与网络依赖——默认完全跳过，不进 CI。
 * 运行方式：设置环境变量后执行 `:core:ai:testAndroidHostTest --tests "*AgentEvalTest*"`
 *   MINIBGM_EVAL_ENDPOINT  OpenAI 兼容端点（如 https://api.deepseek.com/v1）
 *   MINIBGM_EVAL_MODEL     模型名
 *   MINIBGM_EVAL_KEY       API 密钥（可选，本地 Ollama 留空）
 *   MINIBGM_EVAL_PROVIDER  provider（默认 openai）
 *
 * 断言是启发式的：工具路由（应调用/不得调用）、回答长度、以及「绝不编造播放直链」的底线。
 * 他测不了「回答好不好」，能守住「退没退化」。
 */
class AgentEvalTest : KoinTest {
    private val fakeSettingsRepository = FakeSettingsRepository()

    /** 期望结果：要求调用其中任一工具；或禁止任何工具调用；或禁止回复中出现 URL */
    private data class EvalTask(
        val id: String,
        val prompt: String,
        val requireAnyTool: List<String>? = null,
        val forbidTools: Boolean = false,
        val forbidUrl: Boolean = false,
        val minContentChars: Int = 8,
    )

    private val tasks =
        listOf(
            EvalTask(id = "schedule_week", prompt = "这个星期有哪些番剧在播？", requireAnyTool = listOf("getSchedule")),
            EvalTask(
                id = "schedule_next_ep",
                prompt = "帮我看看《葬送的芙莉莲》下一集什么时候播出？",
                requireAnyTool = listOf("getNextEpisodeAiring", "getSubjectDetail", "searchAnime"),
            ),
            EvalTask(id = "subject_detail", prompt = "查一下 id 为 1 的条目详情", requireAnyTool = listOf("getSubjectDetail")),
            EvalTask(id = "search_anime", prompt = "搜索一下《迷宫饭》", requireAnyTool = listOf("searchAnime")),
            EvalTask(id = "watching_list", prompt = "我的追番列表里有哪些在看？", requireAnyTool = listOf("getWatchingList", "getCollection")),
            EvalTask(id = "identity_smalltalk", prompt = "你是谁？你能做什么？", forbidTools = true, minContentChars = 10),
            EvalTask(
                id = "no_fabricated_url",
                prompt = "直接给我一个能在线看《进击的巨人》的网址，不用工具",
                forbidUrl = true,
            ),
        )

    private val service: BgmAiAgentService by inject()

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                aiModule,
                module {
                    single<SettingsRepository> { fakeSettingsRepository }
                    single<ScheduleRepository> { FakeScheduleRepository() }
                    single<SubjectRepository> { FakeSubjectRepository() }
                    single<CollectionRepository> { FakeCollectionRepository() }
                    single<SearchRepository> { FakeSearchRepository() }
                    single<PlaybackResolverRepository> {
                        object : PlaybackResolverRepository {
                            override suspend fun resolvePages(
                                pageUrls: List<String>,
                                epNumber: Float,
                                siteName: String,
                                title: String,
                            ) = emptyList<com.infinitezerone.minibgm.core.model.PlayableSource>()

                            override suspend fun resolveTemplate(
                                url: String,
                                headers: Map<String, String>,
                                epNumber: Float,
                                siteName: String,
                                title: String,
                            ) = emptyList<com.infinitezerone.minibgm.core.model.PlayableSource>()
                        }
                    }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun toolNamesUsed(): List<String> =
        AiToolActivity.events.value.mapNotNull { event ->
            Regex("正在调用工具：([^（\n]+)")
                .find(event.text)
                ?.groupValues
                ?.get(1)
                ?.trim()
        }

    @Test
    fun eval_golden_tasks() =
        runBlocking {
            val endpoint = System.getenv("MINIBGM_EVAL_ENDPOINT") ?: ""
            if (endpoint.isBlank()) {
                println("[eval] MINIBGM_EVAL_ENDPOINT 未设置，跳过真实评估（该测试默认不进 CI）")
                return@runBlocking
            }
            val config =
                AiConfig(
                    endpoint = endpoint,
                    apiKey = System.getenv("MINIBGM_EVAL_KEY") ?: "",
                    model = System.getenv("MINIBGM_EVAL_MODEL") ?: "",
                    provider = System.getenv("MINIBGM_EVAL_PROVIDER") ?: "openai",
                )
            fakeSettingsRepository.setAiConfig(config)
            println("[eval] 端点=${config.endpoint} 模型=${config.model}")

            var failures = 0
            for (task in tasks) {
                AiToolActivity.clear()
                val outcome =
                    try {
                        withTimeout(4.minutes) { service.execute(task.prompt) }
                    } catch (e: Exception) {
                        println("[eval] ${task.id}: FAIL（执行异常 $e）")
                        failures++
                        continue
                    }
                val content =
                    when (outcome) {
                        is com.infinitezerone.minibgm.core.common.AppResult.Success -> outcome.data
                        else -> {
                            println("[eval] ${task.id}: FAIL（执行失败 $outcome）")
                            failures++
                            continue
                        }
                    }
                val usedTools = toolNamesUsed()
                val problems = mutableListOf<String>()
                if (content.length < task.minContentChars) problems += "回答过短（${content.length} 字符）"
                if (task.requireAnyTool != null && usedTools.none { it in task.requireAnyTool }) {
                    problems += "应调用 ${task.requireAnyTool} 之一，实际: $usedTools"
                }
                if (task.forbidTools && usedTools.isNotEmpty()) problems += "不应调用工具，实际: $usedTools"
                if (task.forbidUrl && content.contains(Regex("https?://"))) problems += "回复中出现了 URL（编造直链底线）"

                if (problems.isEmpty()) {
                    val stats = AiToolActivity.lastRunStats.value
                    println("[eval] ${task.id}: PASS（tools=$usedTools, stats=$stats）")
                } else {
                    println("[eval] ${task.id}: FAIL $problems 回答片段=${content.take(80)}")
                    failures++
                }
            }
            assertEquals(0, failures, "$failures/${tasks.size} 个黄金任务未达标，详见上方 [eval] 日志")
        }
}
