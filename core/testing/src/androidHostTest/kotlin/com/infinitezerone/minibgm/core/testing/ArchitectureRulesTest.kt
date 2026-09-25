package com.infinitezerone.minibgm.core.testing

import com.infinitezerone.minibgm.core.datastore.UserPreferences
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 对应 AGENTS.md 核心架构规范与安全边界的确定性自动化测试：
 * 1. Feature 隔离：:feature:A 绝不能依赖 :feature:B（依赖声明由根工程 ModuleBoundaryConventionPlugin
 *    在配置期做依赖图断言，本测试不再扫描 build 脚本文本）
 * 2. 单一数据源：UI 层（:feature:*）严禁 import :core:network / :core:database / :core:datastore 源码包
 *    （依赖声明同样由配置期依赖图断言把关；import 级扫描保留，作为 :core:data 未来意外改用 api 泄漏的渐变报警器）
 * 3. 纯模型层：:core:model 为纯 Kotlin，严禁引入任何 android.* / androidx.* / UI 框架依赖
 * 4. MVI 单向流：所有 ViewModel 严禁对外暴露 MutableStateFlow，必须暴露不可变 StateFlow
 * 5. 凭据隔离：UserPreferences 绝不包含任何 token / 凭据字段（Token 必须走 AndroidKeyStore）
 * 6. 传输与存储框架隔离：feature 源码严禁 import io.ktor.* 或 androidx.room.*
 * 7. 主题一致性：feature 源码严禁硬编码 Color(0x...)，必须使用 :core:designsystem 主题 token
 * 8. 裸 IO 隔离：feature 源码严禁手写底层网络传输（HttpURLConnection / java.net.*）与私有磁盘 IO（cacheDir / filesDir / FileOutputStream）
 * 9. 路由 sealed 契约：NavKey 路由必须实现 BgmRoutes.kt 中的 sealed BgmRoute（让 BgmNavState 入栈语义 when 编译期穷尽，新增路由必须显式声明层级语义）
 * 10. AI 能力边界：只有 :feature:assistant 可依赖 :core:ai，其他页面须走 AssistantRoute 预填交接
 */
class ArchitectureRulesTest {
    private val projectRoot: File by lazy {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        current ?: error("无法定位工程根目录（settings.gradle.kts 未找到），当前路径: ${File(".").canonicalPath}")
    }

    @Test
    fun feature_sources_never_import_network_or_database_packages() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()

        // 源码 import 检查（build 脚本依赖声明由配置期 ModuleBoundaryConventionPlugin 断言）。
        // 依赖当前是 implementation 封装的，这类 import 应当直接编译不过；
        // 保留本检查是为了在 :core:data 未来意外改用 api 暴露底层库时第一时间报警
        val forbiddenPackagePrefixes =
            listOf(
                "import com.infinitezerone.minibgm.core.network",
                "import com.infinitezerone.minibgm.core.database",
            )

        featureDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val relPath = sourceFile.relativeTo(projectRoot).path
                sourceFile.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (forbiddenPackagePrefixes.any { trimmed.startsWith(it) }) {
                        violations.add("$relPath:${index + 1} 违规直接引入底层库或协议 -> $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反单一数据源与分层隔离（UI 层必须通过 :core:data Repositories 协调，严禁直连 network 或 database）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun feature_sources_never_import_datastore() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()

        // 源码 import 检查（build 脚本依赖声明由配置期 ModuleBoundaryConventionPlugin 断言）
        featureDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val relPath = sourceFile.relativeTo(projectRoot).path
                sourceFile.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import com.infinitezerone.minibgm.core.datastore")) {
                        violations.add("$relPath:${index + 1} 违规直接引入 datastore -> $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反单一数据源与分层隔离（UI 层读写用户偏好必须经 :core:data SettingsRepository，严禁直接依赖或 import :core:datastore）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun core_model_is_pure_kotlin_without_android_or_ui_dependencies() {
        val modelDir = File(projectRoot, "core/model")
        assertTrue(modelDir.isDirectory, "core/model 目录未找到")

        val violations = mutableListOf<String>()
        val forbiddenPrefixes =
            listOf(
                "import android.",
                "import androidx.",
                "import com.google.android.",
            )

        modelDir
            .resolve("src")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val relPath = sourceFile.relativeTo(projectRoot).path
                sourceFile.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (forbiddenPrefixes.any { trimmed.startsWith(it) }) {
                        violations.add("$relPath:${index + 1} 包含平台/UI依赖 -> $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反 :core:model 纯 Kotlin 规则（严禁包含任何 android.*、androidx.* 或 UI 框架依赖）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun viewModels_never_expose_mutable_state_flow() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到")

        val violations = mutableListOf<String>()

        featureDir
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
            .forEach { vmFile ->
                val relPath = vmFile.relativeTo(projectRoot).path
                vmFile.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    val isProperty = trimmed.startsWith("val ") || trimmed.startsWith("var ")
                    val isPublic = !trimmed.startsWith("private ")
                    if (isProperty && isPublic) {
                        if (trimmed.contains("MutableStateFlow") || trimmed.contains(": MutableStateFlow")) {
                            violations.add("$relPath:${index + 1} 暴露了可变状态流 -> $trimmed")
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反 MVI 单向数据流规范（ViewModel 只能对外暴露只读 StateFlow，禁止暴露 MutableStateFlow）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun userPreferences_never_stores_sensitive_tokens() {
        // 反射校验 UserPreferences 所有属性名，杜绝凭据泄漏至未加密的 Preferences 中
        val fields = UserPreferences::class.java.declaredFields
        val forbiddenKeywords = listOf("token", "accesstoken", "refreshtoken", "authsecret")

        val violatedFields =
            fields.filter { field ->
                val name = field.name.lowercase()
                forbiddenKeywords.any { name.contains(it) }
            }

        if (violatedFields.isNotEmpty()) {
            fail(
                "安全边界违规：UserPreferences 中检测到敏感凭据属性: " +
                    violatedFields.map { it.name } +
                    "（根据 AGENTS.md，OAuth Token 必须通过 AndroidKeyStore 加密的 AuthTokensDataSource 独立保存！）",
            )
        }
    }

    @Test
    fun feature_sources_never_import_transport_or_storage_frameworks() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到")

        val violations = mutableListOf<String>()
        // 即便 :core:data 以 api 暴露传递依赖，UI 层也不得直接触碰传输/存储框架
        val forbiddenImportPrefixes =
            listOf(
                "import io.ktor",
                "import androidx.room",
            )

        featureDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val relPath = sourceFile.relativeTo(projectRoot).path
                sourceFile.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (forbiddenImportPrefixes.any { trimmed.startsWith(it) }) {
                        violations.add("$relPath:${index + 1} 违规引入传输/存储框架 -> $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反分层隔离（feature 只经 :core:data Repository 访问网络与数据库，严禁直接 import io.ktor / androidx.room）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun feature_sources_never_hardcode_compose_colors() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到")

        val violations = mutableListOf<String>()
        val forbiddenColorPattern = Regex("""Color\(0x""")

        featureDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val relPath = sourceFile.relativeTo(projectRoot).path
                sourceFile.readLines().forEachIndexed { index, line ->
                    if (forbiddenColorPattern.containsMatchIn(line)) {
                        violations.add("$relPath:${index + 1} 硬编码颜色 -> ${line.trim()}")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反主题一致性规范（feature 必须使用 :core:designsystem 的 MiniBgmTheme token，严禁硬编码 Color(0x...)）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun feature_sources_never_perform_raw_network_or_private_disk_io() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到")

        val violations = mutableListOf<String>()
        val forbiddenTokens =
            listOf(
                "HttpURLConnection" to "手写 HttpURLConnection 裸网络连接",
                "import java.net." to "直接引入 java.net.* 底层网络传输类",
                "java.net.URL" to "直接使用 java.net.URL",
                "java.net.Socket" to "直接使用 java.net.Socket",
                ".cacheDir" to "私自操作 context.cacheDir 磁盘缓存",
                ".filesDir" to "私自操作 context.filesDir 内部存储",
                "FileOutputStream" to "手写文件流写入",
            )

        featureDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val relPath = sourceFile.relativeTo(projectRoot).path
                sourceFile.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    for ((token, reason) in forbiddenTokens) {
                        if (line.contains(token)) {
                            violations.add("$relPath:${index + 1} $reason -> $trimmed")
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反单一数据源与离线设计原则（Feature 层严禁手写底层网络传输与私有磁盘缓存，网络与持久化必须经由 :core:data Repositories 统一调度）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun json_instances_have_a_single_home_per_scope() {
        // 每个作用域的 JSON 序列化决策只允许有一个定义处（"家"），其余一律引用：
        // - :core:network -> BgmHttpClient.jsonConfig（全仓库默认家）
        // - :core:ai      -> aiJson（LLM 协议需要 explicitNulls = false）
        // - :core:datastore -> UserPreferencesSerializer（持久化语义独立）
        // - :feature:user -> PlaybackRulesViewModel（feature 看不到 jsonConfig，红线 2）
        // 测试源集豁免（测试本就该能构造隔离/异构配置）。
        val whitelist =
            listOf(
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BgmHttpClient.kt",
                "core/ai/src/commonMain/kotlin/com/infinitezerone/minibgm/core/ai/AiJson.kt",
                "core/datastore/src/androidMain/kotlin/com/infinitezerone/minibgm/core/datastore/UserPreferencesSerializer.kt",
                "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/PlaybackRulesViewModel.kt",
            )
        val pattern = Regex("""\bJson\s*\{""")
        val violations = mutableListOf<String>()
        listOf("core", "feature", "app", "sync").forEach { dirName ->
            val dir = File(projectRoot, dirName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension == "kt" &&
                        !it.path.replace(File.separatorChar, '/').contains("/build/") &&
                        !it.name.endsWith("Test.kt")
                }.forEach { sourceFile ->
                    val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    if (relPath in whitelist) return@forEach
                    sourceFile.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        if (pattern.containsMatchIn(trimmed)) {
                            violations.add(
                                "$relPath:${index + 1} 手写 Json 实例 -> $trimmed（请引用作用域内的共享实例，" +
                                    "或把本文件加入本测试的白名单并说明理由）",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "违反 JSON 单点真值规范（同一序列化决策被复制）：\n" + violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun navigation_routes_are_sealed_and_declared_only_in_bgm_routes() {
        val routesFile =
            File(projectRoot, "core/navigation/src/main/kotlin/com/infinitezerone/minibgm/core/navigation/BgmRoutes.kt")
        assertTrue(routesFile.isFile, "BgmRoutes.kt 未找到: ${routesFile.absolutePath}")
        val content = routesFile.readText()
        assertTrue(
            content.contains("sealed interface BgmRoute : NavKey"),
            "BgmRoutes.kt 必须声明 sealed interface BgmRoute : NavKey —— sealed 层级让 BgmNavState 的" +
                "入栈语义 when 编译期穷尽：新增路由必须显式声明层级（详情层级 replace / 二级列表页同类替换 / 钻取链压栈），" +
                "否则编译不过，杜绝静默落入 push 兜底分支造成返回栈堆叠",
        )

        // 路由式声明（行尾 `: NavKey` 的 class/object）只允许出现在 BgmRoutes.kt 的 sealed 接口上；
        // 其他文件直接实现 NavKey 会绕过编译期穷尽检查
        val declarationTail = Regex("""^\s*\)?\s*:\s*NavKey\s*$""")
        val declarationInline = Regex("""^(data\s+)?(object|class)\s+\w+.*:\s*NavKey\s*$""")
        val violations = mutableListOf<String>()
        listOf("core", "feature", "app", "sync").forEach { dirName ->
            val dir = File(projectRoot, dirName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension == "kt" &&
                        it != routesFile &&
                        !it.path.replace(File.separatorChar, '/').contains("/build/")
                }.forEach { sourceFile ->
                    val relPath = sourceFile.relativeTo(projectRoot).path
                    sourceFile.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        if (declarationTail.matches(trimmed) || declarationInline.matches(trimmed)) {
                            violations.add("$relPath:${index + 1} 直接实现 NavKey（路由必须声明在 BgmRoutes.kt 并实现 BgmRoute）-> $trimmed")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail("违反路由 sealed 契约：\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun only_assistant_feature_may_invoke_the_ai_agent() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()
        // 依赖声明层面（谁可以依赖 :core:ai）由配置期 ModuleBoundaryConventionPlugin 断言；
        // 此处只保留源码 import 扫描作为纵深防御
        featureDir
            .walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file
                        .relativeTo(projectRoot)
                        .path
                        .replace(File.separatorChar, '/')
                        .let { !it.contains("/build/") } &&
                    file.parentFile?.name != "assistant"
            }.forEach { file ->
                val relPath = file.relativeTo(projectRoot).path
                file.readLines().forEachIndexed { index, line ->
                    if (line.contains("import com.infinitezerone.minibgm.core.ai.")) {
                        violations.add("$relPath:${index + 1} 直接调用智能体 -> $line")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反 AI 能力边界（ROADMAP 第 5 节）：智能体调用只能发生在 :feature:assistant 会话内；" +
                    "其他页面要触发 AI 检索必须跳转 AssistantRoute(prefillPrompt) 交接提问，" +
                    "不得在页内自建 agent 调用：\n" + violations.joinToString("\n"),
            )
        }
    }
}
