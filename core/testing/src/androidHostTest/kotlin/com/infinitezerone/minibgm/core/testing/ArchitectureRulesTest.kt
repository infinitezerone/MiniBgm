package com.infinitezerone.minibgm.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 对应 AGENTS.md 核心架构规范与安全边界的确定性自动化测试：
 * 1. Feature 隔离：:feature:A 绝不能依赖 :feature:B（依赖声明由根工程 ModuleBoundaryConventionPlugin
 *    在配置期做依赖图断言，本测试不再扫描 build 脚本文本）
 * 2. 单一数据源：UI 层（:feature:*）严禁 import :core:network / :core:database / :core:datastore 源码包
 *    （依赖声明同样由配置期依赖图断言把关；import 级扫描保留，作为 :core:data 未来意外改用 api 泄漏的渐变报警器）
 * 3. 纯模型层：:core:model 为纯 Kotlin，严禁引入任何 android.* / androidx.* / UI 框架依赖
 * 4. MVI 单向流：所有 ViewModel 严禁对外暴露可变状态（MutableStateFlow）。只允许两种对外形态：
 *    只读 StateFlow（`asStateFlow()`），或 Compose State 持有者的只读类型
 *    （`@Stable interface XxxUiState` + ViewModel 私有的可变实现，见 SeasonalGuideUiState）
 * 5. 凭据隔离：UserPreferences 绝不包含任何 token / 凭据字段（Token 必须走 AndroidKeyStore）
 * 6. 传输与存储框架隔离：feature 源码严禁 import io.ktor.* 或 androidx.room.*
 * 7. 主题一致性：feature 源码严禁硬编码 Color(0x...)，必须使用 :core:designsystem 主题 token
 * 8. 裸 IO 隔离：feature 源码严禁手写底层网络传输（HttpURLConnection / java.net.*）与私有磁盘 IO（cacheDir / filesDir / FileOutputStream）
 * 9. 路由 sealed 契约：NavKey 路由必须实现 BgmRoutes.kt 中的 sealed BgmRoute（让 BgmNavState 入栈语义 when 编译期穷尽，新增路由必须显式声明层级语义）
 * 10. AI 能力边界：只有 :feature:assistant 可依赖 :core:ai，其他页面须走 AssistantRoute 预填交接
 * 11. 排期单一真源：名单与播出时间只能来自 AniList 周排期（bangumi-data 仅作按需映射/平台表），
 *     不得回退到官方日历、bangumi-data 固定窗口或算术预测事件
 * 12. 形状令牌收口：app/feature 源码中 RoundedCornerShape 的 dp 字面量只允许 BgmShapes 阶梯
 *     （4/8/12/16/24）；off-ladder 野值（6/10/14 等）一律改用阶梯值，防止圆角体系再次漂移
 * 13. 图标令牌收口：feature 与 app 源码严禁直接 import androidx.compose.material.icons.*，
 *     所有图标必须统一经 :core:designsystem 的 BgmIcons 引用
 * 14. 现代时间规范：生产代码严禁 import java.util.Date / java.util.Calendar，统一使用 kotlinx.datetime.*
 * 15. 结构化并发规范：生产代码严禁使用 GlobalScope 裸协程，所有协程必须受控于明确生命周期作用域
 * 16. 面向接口契约：UI 源码（:feature:* 与 :app）严禁直接 import 底层实现类（*Impl），必须面向 Interface 编程
 * 17. 一次性事件生命周期感知：feature UI 源码严禁直接对 ViewModel 一次性事件流调用裸 .collect，必须使用 :core:designsystem 的 ObserveAsEvents 绑定生命周期（STARTED）
 * 18. 事件文案资源化：ViewModel 的一次性事件（ShowSnackbar）严禁内联用户可见文案（CJK 字面量），
 *     必须以 @StringRes + formatArgs 下发、UI 侧解析；服务端运行时消息经 message 字段透传（源自播放器文案硬编码复盘）
 * 19. 流媒体发流真实性：流引擎严禁派发未就绪的空 Socket 伪地址；严禁使用虚假 placeholder 桩伪装媒体流（源自磁力边下边播虚假桩复盘）
 * 20. Koin DI 平台契约完整性：platformDataModule 必须显式声明所有底层系统服务契约（ApkInstaller、UpdateRepository、TorrentRepository 等），杜绝单测假桩掩盖导致真机 NoDefinitionFoundException 闪退（源自更新服务 ApkInstaller 漏绑闪退复盘）
 * 21. 手工 DI 块的可选依赖必须显式注入：`viewModel { XxxViewModel(...) }` 手工构造块必须显式传入目标类全部
 *     「默认值为 null」的构造器参数（可选依赖）——漏传既不编译报错、Koin 也照常解析（默认值兜住）、
 *     单测又自己传 fake，最终只在真机上静默退化成「功能没接上」（源自 SubjectModule 漏传
 *     playbackHistoryRepository，让播放历史与续播兜底整条链路在真机上死掉的复盘）
 * 22. 播放页偏好必须落库、会话级状态必须不落库：判据是「稳定立场 vs 单次观看意图 / 引擎会话态」——
 *     弹幕显示 / 自动连播 / 画中画 / 关闭字幕这类稳定立场必须写 SettingsRepository 并由快照投影回读；
 *     播放速度 / 画面比例这类「这一次要多快、要什么画幅」的意图、以及单集循环这类引擎会话态（由
 *     `repeatMode` 持有）刻意不持久化（落库等于让上一部调成 2 倍速/被裁剪的画幅、开着循环静默作用到
 *     下一部毫不相干的番上）
 * 23. 跨页共用偏好必须只有一份真源：同一个评论区组件在播放页与分集详情页各持一份会话状态时，
 *     一处改成「最新」另一处仍是「热门」、重启又双双回默认——排序必须写三处共用的设备侧偏好、
 *     由各页自己的投影回读，解析统一走 CommentSortOrder.fromKey（枚举名不参与持久化；未表态时
 *     由 fallback 表达各页自己的阅读默认，例如讨论帖回帖按楼层正序）
 * 24. 弹幕关闭即零请求必须连「分集纠正」入口一起收：该入口最终会 saveDanmakuOverride +
 *     拉一次弹幕，弹幕关掉后入口仍亮着等于给「已关闭的功能」写持久化覆盖（两个入口
 *     ——控制栏 DanmakuMatchEntryButton 与设置面板行——都必须随开关收起，VM 侧再兜一层竞态闸口）
 * 25. 索引文件必须受机械上下文预算约束：`AGENTS.md` 是每轮会话都会注入的索引，新行为约束的落点
 *     永远是本测试，文档只留一句指向它；总字数与单行长度都设硬上限（想加文字必须先删等量文字），
 *     否则文档会退化成没人读、也拆不出可评审 diff 的巨型单行（源自两次规则追加只靠追加长句的复盘）
 * 26. 材质分层：feature 内容层严禁使用 BgmGlassSurface；玻璃只属于系统 chrome / 瞬态浮层，
 *     内容统一使用 BgmFloatingFrame 实体悬浮框（源自 ColorOS 17 / HyperOS 4 系统 UI 与系统应用的材质分工）
 *
 * 注：本测试套件已全面迁移至 [KotlinSourceScanner]，实现词法脱敏与结构化扫描，彻底杜绝注释误伤、字符串干扰与跨行漏判。
 */
class ArchitectureRulesTest : ArchitectureRuleTestSupport() {

    /**
     * `AGENTS.md` 是每轮会话都会被注入上下文的索引文件，失控膨胀会直接吃掉可用上下文。
     *
     * 两条机械闸口（棘轮：想加文字就必须先删等量文字）：
     * 1. 总字数预算——新行为约束的落点永远是本文件的 ArchitectureRulesTest，文档只留一句索引；
     * 2. 单行上限——把十条规则塞进同一行，既没人愿意读，也拆不出可评审的 diff。
     *
     * 历史教训：硬红线第 2 条与「播放器分集会话」曾各自长成 1.4k / 2.5k 字符的巨型单行，
     * 一次改动只能在整面文字里凭肉眼找落点。预算首次压到 11.2k 的做法是把「事故复盘叙述」搬回
     * 各红线测试的 KDoc（测试名即规则名），AGENTS 只留可执行约束 + 测试名。
     */
    @Test
    fun agents_doc_must_stay_an_index_under_context_budget() {
        val file = File(projectRoot, "AGENTS.md")
        assertTrue(file.isFile, "AGENTS.md 未找到")

        // 统一按 LF 归一：Windows 检出的 \r\n 不该算进上下文预算
        val text = file.readText().replace("\r\n", "\n")
        val violations = mutableListOf<String>()

        if (text.length > AGENTS_MD_CHAR_BUDGET) {
            violations.add(
                "总字数 ${text.length} 超出上下文预算 $AGENTS_MD_CHAR_BUDGET（超出 ${text.length - AGENTS_MD_CHAR_BUDGET} 字符）",
            )
        }
        text.split("\n").forEachIndexed { index, line ->
            if (line.length > AGENTS_MD_MAX_LINE_CHARS) {
                violations.add("第 ${index + 1} 行 ${line.length} 字符超过单行上限 $AGENTS_MD_MAX_LINE_CHARS")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "AGENTS.md 只能做索引不能做说明书：新约束先写进 ArchitectureRulesTest，文档只留一句指向它；" +
                "要加文字必须先删等量文字（确需扩写请显式上调常量并在提交说明里写明理由）。\n" +
                violations.joinToString("\n"),
        )
    }

    /** 只校验我们自己的 skill：Google 侧 vendored 的 skill 不归本仓维护 */

    private fun minibgmSkillDirs(): List<File> =

        File(projectRoot, ".agents/skills")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("minibgm-") }

    /**
     * 保守判定「这看起来是仓库内相对路径」：纯 ASCII、首段是已知顶层目录、不含生成目录。
     *
     * 宁可不查，也不要误报：文档里大量出现命令（带空格）、URL、代码片段、BBCode，
     * 把它们当路径去校验只会制造噪音，最后逼人不信这条红线。
     */
    private fun looksLikeRepoPath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || ".." in path) return false
        if (!path.matches(Regex("""^[A-Za-z0-9._/\-]+$"""))) return false
        if (' ' in path) return false
        if ('/' !in path) return false
        val first = path.substringBefore('/')
        return first in setOf("feature", "core", "app", "crates", "tools", "native", "build-logic", ".agents", "docs", "gradle")
    }

    /** 抽取反引号包裹的片段（避免用含反引号的正则，减少转义噪音） */

    private fun backtickedSpans(text: String): List<String> {
        val spans = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val open = text.indexOf((96).toChar(), index)
            if (open < 0) break
            val close = text.indexOf((96).toChar(), open + 1)
            if (close < 0) break
            spans.add(text.substring(open + 1, close))
            index = close + 1
        }
        return spans
    }


    /**
     * 文档只能说「真话」：AGENTS.md 与各 skill 用反引号引用的测试名、仓库路径、reference 文件
     * 必须真实存在。
     *
     * 为什么必须机械化：这类漂移是**静默**的——文档写着「见 xxx 红线」「细节在 references/yyy.md」，
     * 而那个测试早已改名、文件早已挪走；读者白跑一趟，且没有任何红灯。文档说谎比写长更糟。
     */
    @Test
    fun docs_must_only_reference_things_that_exist() {
        // 规则已按域拆分为 *RulesTest.kt：测试名索引必须扫目录而不是钉死单文件，
        // 否则文件结构本身会被这条元规则机械锁死
        val rulesDir =
            File(
                projectRoot,
                "core/testing/src/androidHostTest/kotlin/com/infinitezerone/minibgm/core/testing",
            )
        val knownTests =
            rulesDir
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith("RulesTest.kt") }
                .flatMap { rulesFile ->
                    Regex("""\n    fun ([a-z0-9_]+)\(\)""")
                        .findAll(rulesFile.readText())
                        .map { it.groupValues[1] }
                }.toSet()

        // 全仓测试类文件名索引：文档里点名的 `XxxTest` 必须真有这个文件
        val testClassFiles =
            projectRoot
                .walkTopDown()
                // 注意：这里**不能**跳过 test / androidHostTest 目录——测试类恰恰住在那里
                .onEnter { it.name !in setOf("build", ".git", ".jj", "node_modules", ".gradle", "target") }
                .filter { it.isFile && it.name.endsWith("Test.kt") }
                .map { it.name.removeSuffix(".kt") }
                .toSet()

        val docs = mutableListOf<Pair<File, String>>()
        File(projectRoot, "AGENTS.md").takeIf { it.isFile }?.let { docs.add(it to it.readText()) }
        minibgmSkillDirs().forEach { skillDir ->
            File(skillDir, "SKILL.md").takeIf { it.isFile }?.let { docs.add(it to it.readText()) }
            File(skillDir, "references")
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension == "md" }
                .forEach { docs.add(it to it.readText()) }
        }

        val violations = mutableListOf<String>()
        docs.forEach { (file, text) ->
            val rel = file.relativeTo(projectRoot).path
            val parent = file.parentFile
            // references/ 里的文件把 skill 目录当作「仓库根」，SKILL.md 则取自身所在目录
            val skillDir =
                when {
                    parent == null -> projectRoot
                    parent.name == "references" -> parent.parentFile ?: projectRoot
                    else -> parent
                }
            backtickedSpans(text).forEach { token ->
                val path = token.replace((92).toChar(), (47).toChar()).removePrefix("./")
                when {
                    // Class#method 形式的红线引用
                    Regex("""^[A-Za-z]+#([a-z0-9_]+)$""").matches(token) -> {
                        val method = token.substringAfter((35).toChar())
                        if (method !in knownTests) violations.add(rel + " -> 引用了不存在的红线 " + token)
                    }
                    // 点名的测试类：必须存在同名 .kt 文件
                    Regex("""^[A-Z][A-Za-z0-9]*Test$""").matches(token) -> {
                        if (token !in testClassFiles) violations.add(rel + " -> 引用了不存在的测试类 " + token)
                    }
                    // 纯 snake_case：AGENTS 里只应是测试名
                    file.name == "AGENTS.md" && Regex("""^[a-z][a-z0-9_]{11,}$""").matches(token) -> {
                        if (token !in knownTests) violations.add(rel + " -> 引用了不存在的测试/标识符 " + token)
                    }
                    // references/ 相对所在 skill 目录
                    path.startsWith("references/") && file.absolutePath.contains(".agents/skills") -> {
                        if (!File(skillDir, path).exists()) violations.add(rel + " -> 引用了不存在的 reference " + token)
                    }
                    // 我们的 skill 目录：minibgm-xxx/references/...
                    path.startsWith("minibgm-") -> {
                        if (!File(File(projectRoot, ".agents/skills"), path).exists()) {
                            violations.add(rel + " -> 引用了不存在的 skill 路径 " + token)
                        }
                    }
                    // 仓库相对路径（保守判据：纯 ASCII、首段必须是已知顶层目录、非生成目录）
                    looksLikeRepoPath(path) -> {
                        if (!File(projectRoot, path).exists()) violations.add(rel + " -> 引用了不存在的路径 " + token)
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "文档引用了不存在的东西（测试名 / 路径 / reference）——文档说谎比写长更糟，请改成真实存在的名字：\n" +
                violations.joinToString("\n"),
        )
    }


    /**
     * skill 必须**渐进披露**：SKILL.md 只做路由与铁律，细节进 `references/`，按需读取。
     *
     * 反例是 4.2 万字符的单文件 SKILL.md——命中一次就全文注入，比常驻的 AGENTS.md 还贵。
     */
    @Test
    fun minibgm_skills_must_stay_progressively_disclosed() {
        val violations = mutableListOf<String>()
        minibgmSkillDirs().forEach { dir ->
            val skill = File(dir, "SKILL.md")
            if (!skill.isFile) {
                violations.add(dir.name + " -> 缺 SKILL.md")
                return@forEach
            }
            val chars = skill.readText().replace("\r\n", "\n").length
            if (chars > SKILL_MAX_CHARS) {
                violations.add(dir.name + "/SKILL.md 有 " + chars + " 字符，超过上限 " + SKILL_MAX_CHARS)
            }
            val refs = File(dir, "references").listFiles().orEmpty().filter { it.isFile && it.extension == "md" }
            refs.forEach { ref ->
                val refChars = ref.readText().replace("\r\n", "\n").length
                if (refChars > SKILL_REFERENCE_MAX_CHARS) {
                    violations.add(dir.name + "/references/" + ref.name + " 有 " + refChars + " 字符，超过上限 " + SKILL_REFERENCE_MAX_CHARS)
                }
            }
            if (chars > 2_000 && refs.isEmpty()) {
                violations.add(dir.name + " -> SKILL.md 超过 2000 字符却没有 references/ 分流")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "skill 必须渐进披露（SKILL.md 做路由 + 铁律，细节进 references/ 按需读）：\n" +
                violations.joinToString("\n"),
        )
    }

    private companion object {
        /**
         * `AGENTS.md` 上下文预算（字符数，按 LF 归一后计量）。
         *
         * 棘轮语义：只允许「删等量再写」；确需上调时必须把这个数字改大，并在提交说明里写明
         * 为什么这笔上下文值得占用——涨幅会出现在 diff 里，而不是悄悄长胖。
         */
        const val AGENTS_MD_CHAR_BUDGET = 9_800

        /** `AGENTS.md` 单行字符上限：巨型 bullet 既没人读，也拆不出可评审的 diff */
        const val AGENTS_MD_MAX_LINE_CHARS = 900

        /** 单个 `SKILL.md` 的上限：路由 + 铁律够用，细节必须进 references/ */
        const val SKILL_MAX_CHARS = 6_000

        /** 单个 reference 的上限：一次 read 不该把上下文顶爆 */
        const val SKILL_REFERENCE_MAX_CHARS = 10_000
    }
}
