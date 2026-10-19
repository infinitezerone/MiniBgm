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
 *
 * 注：本测试套件已全面迁移至 [KotlinSourceScanner]，实现词法脱敏与结构化扫描，彻底杜绝注释误伤、字符串干扰与跨行漏判。
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
        val forbiddenPackagePrefixes =
            listOf(
                "com.infinitezerone.minibgm.core.network",
                "com.infinitezerone.minibgm.core.database",
            )

        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                val relPath = sourceFile.relativeTo(projectRoot).path
                scanner.imports.forEach { imp ->
                    if (forbiddenPackagePrefixes.any { imp.path.startsWith(it) }) {
                        violations.add("$relPath:${imp.lineNumber} 违规直接引入底层库或协议 -> import ${imp.path}")
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
        val forbiddenPrefix = "com.infinitezerone.minibgm.core.datastore"

        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                val relPath = sourceFile.relativeTo(projectRoot).path
                scanner.imports.forEach { imp ->
                    if (imp.path.startsWith(forbiddenPrefix)) {
                        violations.add("$relPath:${imp.lineNumber} 违规直接引入 datastore -> import ${imp.path}")
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
                "android.",
                "androidx.",
                "com.google.android.",
            )

        modelDir
            .resolve("src")
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                val relPath = sourceFile.relativeTo(projectRoot).path
                scanner.imports.forEach { imp ->
                    if (forbiddenPrefixes.any { imp.path.startsWith(it) }) {
                        violations.add("$relPath:${imp.lineNumber} 包含平台/UI依赖 -> import ${imp.path}")
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
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
            .forEach { vmFile ->
                val scanner = KotlinSourceScanner.fromFile(vmFile)
                val relPath = vmFile.relativeTo(projectRoot).path
                scanner.findExposedMutableStateFlows().forEach { match ->
                    violations.add("$relPath:${match.lineNumber} 暴露了可变状态流 -> ${match.lineContent}")
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反 MVI 单向数据流规范（ViewModel 只能对外暴露只读状态：StateFlow.asStateFlow()，" +
                    "或 Compose State 持有者的只读类型；禁止暴露 MutableStateFlow）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun userPreferences_never_stores_sensitive_tokens() {
        // 反射校验 UserPreferences 所有属性名，杜绝凭据泄漏至未加密的 Preferences 中
        val fields = UserPreferences::class.java.declaredFields
        // 凭据形状的字段名一律不得出现在明文偏好里（OAuth token 走 TokenProvider，其他密钥走 SecureSecretStore）
        val forbiddenKeywords =
            listOf("token", "accesstoken", "refreshtoken", "apikey", "secret", "password", "passwd", "credential")

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
        val forbiddenImportPrefixes =
            listOf(
                "io.ktor",
                "androidx.room",
            )

        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                val relPath = sourceFile.relativeTo(projectRoot).path
                scanner.imports.forEach { imp ->
                    if (forbiddenImportPrefixes.any { imp.path.startsWith(it) }) {
                        violations.add("$relPath:${imp.lineNumber} 违规引入传输/存储框架 -> import ${imp.path}")
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
        val rulesFile =
            File(
                projectRoot,
                "core/testing/src/androidHostTest/kotlin/com/infinitezerone/minibgm/core/testing/ArchitectureRulesTest.kt",
            )
        val knownTests =
            Regex("""\n    fun ([a-z0-9_]+)\(\)""")
                .findAll(rulesFile.readText())
                .map { it.groupValues[1] }
                .toSet()

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

    /**
     * ECH 引擎必须把「自有请求超时」翻译成 Ktor 的 `HttpRequestTimeoutException`（IOException）。
     *
     * 直接抛 `CancellationException` 的后果（详情页整页失败的复盘）：重试插件显式排除取消语义，
     * 一次超时就判死整次请求；调用方也无法区分「我们等超时了」与「用户取消了」。
     * 同一个文件里 body 读取处早就翻译成 IOException，请求阶段却原样抛取消——两套相反契约。
     */
    @Test
    fun ech_engine_must_translate_its_own_timeout() {
        val file =
            File(
                projectRoot,
                "core/network/src/androidMain/kotlin/com/infinitezerone/minibgm/core/network/ech/EchHttpClientEngine.kt",
            )
        if (!file.isFile) return

        val text = file.readText()
        assertTrue(
            text.contains("HttpRequestTimeoutException("),
            "EchHttpClientEngine 必须把自有超时翻译成 HttpRequestTimeoutException（IOException），" +
                "严禁直接 throw cancellation——重试插件会把它当成调用方取消",
        )
    }

    /**
     * 网络切换必须真的重置探测冷却与解析缓存，并且**真的被注册**。
     *
     * 平台契约：地址与可达性绑定在「当前网络」上。监听器写了却没接线，等于换网后仍要等满
     * 10 分钟冷却——这正是「明明有网却一直超时」的成因，而且静态上完全看不出来。
     */
    @Test
    fun ech_network_switch_must_be_wired_and_reset_state() {
        val monitor =
            File(
                projectRoot,
                "core/network/src/androidMain/kotlin/com/infinitezerone/minibgm/core/network/ech/EchNetworkMonitor.kt",
            )
        val app = File(projectRoot, "app/src/main/kotlin/com/infinitezerone/minibgm/MiniBgmApp.kt")
        if (!monitor.isFile || !app.isFile) return

        val monitorText = monitor.readText()
        assertTrue(
            monitorText.contains("registerDefaultNetworkCallback"),
            "网络监听必须走 ConnectivityManager.registerDefaultNetworkCallback（平台契约）",
        )
        assertTrue(
            monitorText.contains("CloudflareEdgeProbe.resetCooldown()"),
            "换网必须重置探测冷却，否则要等满 10 分钟才会重新发现边缘",
        )
        assertTrue(
            monitorText.contains("AdaptiveDnsResolver.invalidateCaches()"),
            "换网必须丢弃旧网络的 DNS 与候选缓存",
        )
        assertTrue(
            monitorText.contains("EchRuntime.clearConnectionPool()"),
            "换网必须清空底层长连接池，否则会在已失效的旧网络 Socket 上死等超时",
        )
        assertTrue(
            app.readText().contains("startNetworkMonitor("),
            "监听器写了却没在 App 启动时注册，等于没有——必须调用 EchRuntime.startNetworkMonitor",
        )
    }

    @Test
    fun feature_sources_never_hardcode_compose_colors() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到")

        val violations = mutableListOf<String>()
        val forbiddenColorPattern = Regex("""\bColor\(0x[0-9a-fA-F]+""")

        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                val relPath = sourceFile.relativeTo(projectRoot).path
                scanner.findMatches(forbiddenColorPattern).forEach { match ->
                    violations.add("$relPath:${match.lineNumber} 硬编码颜色 -> ${match.lineContent}")
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反主题一致性规范（feature 必须使用 :core:designsystem 的 MiniBgmTheme token，严禁硬编码 Color(0x...)）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    /**
     * 播放线路面板是**主题化**面板（竖屏底部弹层与横屏右侧抽屉共用同一份内容），
     * 容器色与文字必须全部来自 `MaterialTheme.colorScheme`。
     *
     * 横屏抽屉曾硬编码 `color = Color.Black.copy(alpha = 0.92f)` + `contentColor = Color.White`，
     * 而内部每一行文字又显式取 `onSurface` / `onSurfaceVariant`：浅色主题下标题、说明文案与
     * 「共 N 条线路」整体变成「深灰字压黑底」，对用户等于抽屉顶部是空的；同时与右侧选集抽屉
     * （surfaceContainerHigh + 左侧圆角）风格割裂。`Color.Black` / `Color.White` 这类语义色
     * 绕开了 `Color(0x...)` 硬编码红线，必须单独把这条面板钉死。
     */
    @Test
    fun player_sources_panel_must_be_theme_driven() {
        val file =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerSourcesBottomSheet.kt",
            )
        if (!file.isFile) return

        val scanner = KotlinSourceScanner.fromFile(file)
        val violations = mutableListOf<String>()
        listOf("Color.Black", "Color.White").forEach { token ->
            scanner.containsToken(token).forEach { match ->
                violations.add("PlayerSourcesBottomSheet.kt:${match.lineNumber} 硬编码 $token -> ${match.lineContent}")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "播放线路面板必须整体走主题 token（浅色主题下黑底 + 深色 onSurface 会让标题与说明文案直接消失）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 播放线路面板必须复用设计系统现成组件，而不是在 feature 里手搓私有样式：
     * 竖屏走 `BgmSourcesBottomSheet`（收起动效 / 导航栏 padding / 头部 / 滚动容器），
     * 次级入口（播放源管理、AI 找源）与空态走 `BgmSourceActionCard` / `SourceEmptyStateCard`。
     *
     * 历史缺陷：手写 `BgmModalBottomSheet` + 私有标题行 + `TextButton`「播放源管理」+ 裸 Text 空态，
     * 于是同一份内容横竖两套样式各自漂移——收起没有动效、空态也没有可达的配置入口。
     */
    @Test
    fun player_sources_panel_must_reuse_design_system() {
        val file =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerSourcesBottomSheet.kt",
            )
        if (!file.isFile) return

        val scanner = KotlinSourceScanner.fromFile(file)
        val violations = mutableListOf<String>()

        listOf("BgmSourcesBottomSheet(", "BgmSourceActionCard(", "SourceEmptyStateCard(").forEach { token ->
            if (scanner.containsToken(token).isEmpty()) {
                violations.add("缺少设计系统组件 $token")
            }
        }
        listOf("BgmModalBottomSheet(", "TextButton(").forEach { token ->
            scanner.containsToken(token).forEach { match ->
                violations.add("PlayerSourcesBottomSheet.kt:${match.lineNumber} 手搓容器/私有按钮 $token -> ${match.lineContent}")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "播放线路面板必须复用设计系统现成组件（BgmSourcesBottomSheet / BgmSourceActionCard / SourceEmptyStateCard）：\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun feature_sources_never_perform_raw_network_or_private_disk_io() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到")

        val violations = mutableListOf<String>()
        val forbiddenTokens =
            listOf(
                "HttpURLConnection" to "手写 HttpURLConnection 裸网络连接",
                "java.net.URL" to "直接使用 java.net.URL",
                "java.net.Socket" to "直接使用 java.net.Socket",
                ".cacheDir" to "私自操作 context.cacheDir 磁盘缓存",
                ".filesDir" to "私自操作 context.filesDir 内部存储",
                "FileOutputStream" to "手写文件流写入",
            )

        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                val relPath = sourceFile.relativeTo(projectRoot).path

                // import 级别检查
                scanner.imports.forEach { imp ->
                    if (imp.path.startsWith("java.net.")) {
                        violations.add("$relPath:${imp.lineNumber} 直接引入 java.net.* 底层网络传输类 -> import ${imp.path}")
                    }
                }

                // 代码 Token 级别检查（自动免疫注释与字符串）
                for ((token, reason) in forbiddenTokens) {
                    scanner.containsToken(token).forEach { match ->
                        violations.add("$relPath:${match.lineNumber} $reason -> ${match.lineContent}")
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
        // 测试源集豁免（测试本就该能构造隔离/异构配置）。
        val whitelist =
            listOf(
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BgmHttpClient.kt",
                "core/ai/src/commonMain/kotlin/com/infinitezerone/minibgm/core/ai/AiJson.kt",
                "core/datastore/src/androidMain/kotlin/com/infinitezerone/minibgm/core/datastore/UserPreferencesSerializer.kt",
            )
        val pattern = Regex("""\bJson\s*\{""")
        val violations = mutableListOf<String>()
        listOf("core", "feature", "app", "sync").forEach { dirName ->
            val dir = File(projectRoot, dirName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter {
                    it.isFile &&
                        it.extension == "kt" &&
                        !it.path.replace(File.separatorChar, '/').contains("/build/") &&
                        !it.name.endsWith("Test.kt")
                }.forEach { sourceFile ->
                    val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    if (relPath in whitelist) return@forEach
                    val scanner = KotlinSourceScanner.fromFile(sourceFile)
                    scanner.findMatches(pattern).forEach { match ->
                        violations.add(
                            "$relPath:${match.lineNumber} 手写 Json 实例 -> ${match.lineContent}（请引用作用域内的共享实例，" +
                                "或把本文件加入本测试的白名单并说明理由）",
                        )
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

        // 路由式声明（实现 NavKey 的 class/object）只允许出现在 BgmRoutes.kt
        // 脱敏后匹配，避免注释或字符串中的说明误伤，同时严格区分构造函数参数类型与类继承超类
        val navKeyPattern = Regex("""\b(class|object)\s+\w+(?:\s*\([^)]*\))?\s*:\s*(?:[^,{}\n]*,\s*)*NavKey\b""")
        val violations = mutableListOf<String>()
        listOf("core", "feature", "app", "sync").forEach { dirName ->
            val dir = File(projectRoot, dirName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter {
                    it.isFile &&
                        it.extension == "kt" &&
                        it != routesFile &&
                        !it.path.replace(File.separatorChar, '/').contains("/build/")
                }.forEach { sourceFile ->
                    val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    val scanner = KotlinSourceScanner.fromFile(sourceFile)
                    scanner.findMatches(navKeyPattern).forEach { match ->
                        violations.add("$relPath:${match.lineNumber} 直接实现 NavKey（路由必须声明在 BgmRoutes.kt 并实现 BgmRoute）-> ${match.lineContent}")
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
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file
                        .relativeTo(projectRoot)
                        .path
                        .replace(File.separatorChar, '/')
                        .let { !it.contains("/build/") && !it.startsWith("feature/assistant/") }
            }.forEach { file ->
                val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                val scanner = KotlinSourceScanner.fromFile(file)
                scanner.imports.forEach { imp ->
                    if (imp.path.startsWith("com.infinitezerone.minibgm.core.ai.")) {
                        violations.add("$relPath:${imp.lineNumber} 直接调用智能体 -> import ${imp.path}")
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

    /**
     * 排期单一真源红线：时刻表仓库不得再依赖官方日历、bangumi-data 固定窗口或算术预测。
     * 这三者正是历史上“漏番 / 假集数”的来源；AniList 周排期是名单与时间的唯一真源。
     */
    @Test
    fun schedule_repository_uses_anilist_as_the_only_roster_source() {
        val repoFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/ScheduleRepository.kt",
            )
        assertTrue(repoFile.isFile, "ScheduleRepository.kt 未找到: ${repoFile.absolutePath}")

        val forbidden =
            listOf(
                "getCalendar(" to "官方日历已废弃：名单/排期真值只能来自 AniList 周排期",
                "getRecentBangumiData(" to "bangumi-data 固定窗口已废弃：只能按 startDate/begin 月按需拉取单月切片",
                "AirEventKind.PREDICTED" to "禁止生成算术预测事件（PREDICTED）",
            )
        val violations = mutableListOf<String>()
        val scanner = KotlinSourceScanner.fromFile(repoFile)
        for ((needle, reason) in forbidden) {
            scanner.containsToken(needle).forEach { match ->
                violations.add("${repoFile.name}:${match.lineNumber} $reason -> ${match.lineContent}")
            }
        }

        if (violations.isNotEmpty()) {
            fail("违反排期单一真源红线（AniList 为名单+时间唯一真源）：\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun ui_sources_only_use_ladder_radius_literals() {
        val violations = mutableListOf<String>()
        val ladder = setOf("0", "4", "8", "12", "16", "24")
        val radiusLiteral = Regex("""(?:RoundedCornerShape\(([^()]*)\)|cornerRadius\s*=\s*\d+\.dp)""")
        val dpValue = Regex("""(\d+)\.dp""")

        for (dir in listOf("feature", "app")) {
            val rootDir = File(projectRoot, dir)
            if (!rootDir.isDirectory) continue
            rootDir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    val scanner = KotlinSourceScanner.fromFile(sourceFile)
                    val relPath = sourceFile.relativeTo(projectRoot).path
                    scanner.findMatches(radiusLiteral).forEach { match ->
                        dpValue.findAll(match.token).forEach { value ->
                            if (value.groupValues[1] !in ladder) {
                                violations.add(
                                    "$relPath:${match.lineNumber} 圆角野值 " +
                                        "RoundedCornerShape(...${value.groupValues[1]}.dp...) -> ${match.lineContent.trim()}",
                                )
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "违反形状令牌收口（RoundedCornerShape 的 dp 字面量仅允许 BgmShapes 阶梯 4/8/12/16/24，" +
                    "圆角应表达 MaterialTheme.shapes.* 或阶梯内的字面量）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun feature_and_app_sources_never_import_raw_material_icons() {
        val violations = mutableListOf<String>()
        val forbiddenPrefix = "androidx.compose.material.icons"

        for (dir in listOf("feature", "app")) {
            val rootDir = File(projectRoot, dir)
            if (!rootDir.isDirectory) continue
            rootDir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    val scanner = KotlinSourceScanner.fromFile(sourceFile)
                    val relPath = sourceFile.relativeTo(projectRoot).path
                    scanner.imports.forEach { imp ->
                        if (imp.path.startsWith(forbiddenPrefix)) {
                            violations.add(
                                "$relPath:${imp.lineNumber} 违规直接引入 raw Material Icons -> import ${imp.path}，请改用 BgmIcons.*",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "违反图标令牌收口（feature 与 app 源码严禁直接引入 androidx.compose.material.icons，所有图标必须统一经 :core:designsystem 的 BgmIcons 访问）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun production_sources_never_import_legacy_date_or_calendar() {
        val violations = mutableListOf<String>()
        val forbiddenImports = setOf("java.util.Date", "java.util.Calendar")

        listOf("core", "feature", "app", "sync").forEach { dirName ->
            val dir = File(projectRoot, dirName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    val scanner = KotlinSourceScanner.fromFile(sourceFile)
                    val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    scanner.imports.forEach { imp ->
                        if (imp.path in forbiddenImports) {
                            violations.add(
                                "$relPath:${imp.lineNumber} 违规引入遗留时间类型 -> import ${imp.path}，请改用 kotlinx.datetime.*",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "违反现代时间规范（全仓生产代码严禁使用 java.util.Date / java.util.Calendar，统一使用 kotlinx.datetime.*）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun production_sources_never_use_global_scope() {
        val violations = mutableListOf<String>()
        val pattern = Regex("""\bGlobalScope\b""")

        listOf("core", "feature", "app", "sync").forEach { dirName ->
            val dir = File(projectRoot, dirName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    val scanner = KotlinSourceScanner.fromFile(sourceFile)
                    val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    scanner.findMatches(pattern).forEach { match ->
                        violations.add(
                            "$relPath:${match.lineNumber} 违规使用 GlobalScope 裸协程 -> ${match.lineContent.trim()}",
                        )
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "违反结构化并发规范（生产源码严禁使用 GlobalScope，请使用 viewModelScope 或注入的 CoroutineScope）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun ui_sources_never_import_implementation_classes() {
        val violations = mutableListOf<String>()

        listOf("feature", "app").forEach { dirName ->
            val dir = File(projectRoot, dirName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    val scanner = KotlinSourceScanner.fromFile(sourceFile)
                    val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    scanner.imports.forEach { imp ->
                        val simpleName = imp.path.substringAfterLast('.')
                        if (simpleName.endsWith("Impl") || simpleName == "LocalOAuthProxyServer") {
                            violations.add(
                                "$relPath:${imp.lineNumber} 违规直接引入底层实现类 -> import ${imp.path}，UI 层必须面向抽象接口编程",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                "违反面向接口契约（feature 与 app 源码严禁直接引入 *Impl 或底层服务实现类，必须通过 Interface 契约依赖）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun feature_screens_never_collect_viewmodel_events_without_observe_as_events() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()
        val rawCollectPattern =
            Regex(
                """\b(viewModel|collectionsViewModel|[a-zA-Z0-9_]*ViewModel)\.(events|uiEvents|userMessage|userMessages|uiEffects)\.collect\b""",
            )

        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                scanner.findMatches(rawCollectPattern).forEach { match ->
                    violations.add(
                        "$relPath:${match.lineNumber} 违规裸收集 ViewModel 一次性事件 -> ${match.lineContent.trim()}，必须使用 ObserveAsEvents 绑定生命周期（STARTED）",
                    )
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反生命周期与一次性事件消费规范（ViewModel 一次性事件流必须使用 ObserveAsEvents 收集，确保后台挂起与前台恢复，杜绝静默吃事件与多栈穿透）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun viewModels_never_inline_user_facing_snackbar_text() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()
        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
            .forEach { sourceFile ->
                val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                findCjkLiteralsInSnackbarArgs(sourceFile.readText()).forEach { (lineNumber, literal) ->
                    violations.add(
                        "$relPath:$lineNumber ShowSnackbar 内联用户文案 -> \"$literal\"。" +
                            "文案必须以 @StringRes + formatArgs 下发、UI 侧解析（服务端运行时消息经 message 字段透传），" +
                            "严禁在 ViewModel 直写用户可见文案绕开资源体系",
                    )
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反一次性事件文案资源化规范（ShowSnackbar 严禁内联中文字面量，多语言与文案审查都在资源体系内收口）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun stream_engines_never_use_fake_placeholders_or_unbound_sockets() {
        val dataDir = File(projectRoot, "core/data")
        assertTrue(dataDir.isDirectory, "core/data 目录未找到: ${dataDir.absolutePath}")

        val violations = mutableListOf<String>()
        val forbiddenTokens =
            listOf(
                "minibgm_streamer_placeholder",
                "minibgm_fake",
            )

        dataDir
            .resolve("src")
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val content = sourceFile.readText()
                val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')

                forbiddenTokens.forEach { token ->
                    if (content.contains(token)) {
                        violations.add("$relPath 违规包含虚假流占位标记 '$token'")
                    }
                }

                if (content.contains("ServerSocket(0)") && (relPath.contains("Torrent") || relPath.contains("Stream"))) {
                    violations.add("$relPath 严禁在流媒体引擎中使用 ServerSocket(0) 伪造空端口分发流地址（必须由真实常驻服务端或 JNI 监听绑定）")
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反流媒体发流真实性规范（严禁伪造 placeholder 虚假文件或未绑定的空 Socket 伪地址欺骗播放器）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    @Test
    fun platform_data_module_declares_all_referenced_services() {
        val platformModuleFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/di/DataModule.android.kt",
            )
        assertTrue(platformModuleFile.isFile, "DataModule.android.kt 未找到: ${platformModuleFile.absolutePath}")

        val content = platformModuleFile.readText()
        val violations = mutableListOf<String>()

        // 必须显式注册的底层核心契约，杜绝在 UpdateRepositoryImpl / AndroidTorrentRepository 等处调用 get() 时发生 NoDefinitionFoundException
        val requiredBindings =
            listOf(
                "single<ApkInstaller>",
                "single<UpdateRepository>",
                "single<TorrentRepository>",
                "single<TorrentStreamEngine>",
                "single<TorrentIntentDispatcher>",
                "single<AssetDownloader>",
                "single<NetworkMonitor>",
                "single<CrashLogRepository>",
            )

        requiredBindings.forEach { binding ->
            if (!content.contains(binding)) {
                violations.add("platformDataModule 缺少必须的显式服务绑定声明: '$binding'（防止运行期 NoDefinitionFoundException 崩溃）")
            }
        }

        if (violations.isNotEmpty()) {
            fail(
                "违反 Koin DI 平台模块绑定完整性规范（源自更新服务与流引擎参数未注册导致闪退复盘）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    /**
     * 定位 [content] 中所有 ShowSnackbar(...) 实参跨度内的中文字符串字面量。
     *
     * 借助 [KotlinSourceScanner.sanitize] 的"长度与索引不变"特性：先用脱敏文本（注释与字符串已抹平）
     * 做 ShowSnackbar( 的括号配对得到跨度，再回原文提取跨度内的字符串字面量检查 CJK——
     * 注释里提到 ShowSnackbar("示例") 不会误伤，跨行调用也不会漏判。
     */
    private fun findCjkLiteralsInSnackbarArgs(content: String): List<Pair<Int, String>> {
        val sanitized = KotlinSourceScanner.fromContent(content).sanitizedContent
        val spans = mutableListOf<IntRange>()
        var index = sanitized.indexOf("ShowSnackbar(")
        while (index >= 0) {
            var depth = 1
            var cursor = index + "ShowSnackbar(".length
            while (cursor < sanitized.length && depth > 0) {
                when (sanitized[cursor]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                cursor++
            }
            spans += index until cursor
            index = sanitized.indexOf("ShowSnackbar(", cursor)
        }
        if (spans.isEmpty()) return emptyList()

        val violations = mutableListOf<Pair<Int, String>>()
        val cjkRegex = Regex("""[\p{InCJKUnifiedIdeographs}\u3000-\u303F\uFF01-\uFF5E]""")
        for ((start, _, literal) in extractStringLiterals(content)) {
            if (spans.any { it.contains(start) } && cjkRegex.containsMatchIn(literal)) {
                val line = content.take(start).count { it == '\n' } + 1
                violations += line to literal
            }
        }
        return violations
    }

    /** 提取原文中所有字符串字面量（含普通与三引号原生串，跳过注释），返回 (起始索引, 结束索引, 内容) */
    private fun extractStringLiterals(content: String): List<Triple<Int, Int, String>> {
        val literals = mutableListOf<Triple<Int, Int, String>>()
        var i = 0
        val len = content.length
        while (i < len) {
            when {
                // 注释整体跳过（与 KotlinSourceScanner.sanitize 同口径）
                i + 1 < len && content[i] == '/' && content[i + 1] == '/' -> {
                    while (i < len && content[i] != '\n') i++
                }

                i + 1 < len && content[i] == '/' && content[i + 1] == '*' -> {
                    var depth = 1
                    i += 2
                    while (i < len && depth > 0) {
                        if (i + 1 < len && content[i] == '/' && content[i + 1] == '*') {
                            depth++
                            i += 2
                        } else if (i + 1 < len && content[i] == '*' && content[i + 1] == '/') {
                            depth--
                            i += 2
                        } else {
                            i++
                        }
                    }
                }

                i + 2 < len && content[i] == '"' && content[i + 1] == '"' && content[i + 2] == '"' -> {
                    val start = i
                    i += 3
                    val sb = StringBuilder()
                    while (i + 2 < len && !(content[i] == '"' && content[i + 1] == '"' && content[i + 2] == '"')) {
                        sb.append(content[i])
                        i++
                    }
                    i = (i + 3).coerceAtMost(len)
                    literals += Triple(start, i, sb.toString())
                }

                content[i] == '"' -> {
                    val start = i
                    i++
                    val sb = StringBuilder()
                    while (i < len && content[i] != '"' && content[i] != '\n') {
                        if (content[i] == '\\' && i + 1 < len) {
                            sb.append(content[i]).append(content[i + 1])
                            i += 2
                        } else {
                            sb.append(content[i])
                            i++
                        }
                    }
                    i = (i + 1).coerceAtMost(len)
                    literals += Triple(start, i, sb.toString())
                }

                else -> i++
            }
        }
        return literals
    }

    /**
     * WebView 可观测性红线（见 AGENTS.md 领域规则）：
     * 生产代码中声明 WebChromeClient 必须实现 onConsoleMessage。
     * 严禁静默吞掉前端控制台与 JS 报错，导致排查陷入盲区。
     */
    @Test
    fun webChromeClient_must_implement_onConsoleMessage() {
        val violations = mutableListOf<String>()
        projectRoot
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && !it.path.replace(File.separatorChar, '/').contains("/build/") }
            .forEach { file ->
                val content = file.readText()
                if (content.contains("WebChromeClient()") || content.contains(": WebChromeClient")) {
                    if (!content.contains("onConsoleMessage")) {
                        val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                        violations.add("$relPath 实现了 WebChromeClient 但未重写 onConsoleMessage")
                    }
                }
            }
        assertTrue(
            violations.isEmpty(),
            "违反 WebView 可观测性红线（见 AGENTS.md 领域规则）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 构建解耦红线（见 AGENTS.md 构建与验证）：
     * `build-logic` 严禁直接调度 `cargo` 或 `cargo-ndk` 进程。
     * Rust 原生库统一由独立脚本 `tools/build-native` 编译，Gradle 仅通过 `registerPrebuiltJniLibs` 挂载产物。
     */
    @Test
    fun buildLogic_never_invokes_cargo_toolchain() {
        val buildLogicDir = File(projectRoot, "build-logic")
        if (!buildLogicDir.isDirectory) return

        val violations = mutableListOf<String>()
        val forbiddenTokens = listOf("cargo-ndk", "\"cargo\"", "'cargo'", "Cargo.toml")
        buildLogicDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val sanitized = KotlinSourceScanner.sanitize(file.readText())
                val matched = forbiddenTokens.filter { sanitized.contains(it) }
                if (matched.isNotEmpty()) {
                    val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    violations.add("$relPath 包含跨界 Cargo 调度特征: ${matched.joinToString()}")
                }
            }
        assertTrue(
            violations.isEmpty(),
            "违反 Gradle/Rust 构建解耦红线（见 AGENTS.md 构建与验证）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 按需动态 `.so` 装载与确认红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. 生产代码凡调用 `System.load(` 从私有目录加载动态库，同文件必须调用 `.setReadOnly()`（Android 10+ W^X 安全约束）、
     *    使用 `Mutex` 防并发重入，并按应用版本隔离目录；
     * 2. `TorrentRepository` 严禁暴露未经用户确认即静默下载组件的 `checkOrInstallEngine` 后门。
     */
    @Test
    fun dynamic_so_loading_enforces_wx_version_isolation_and_no_silent_install() {
        val violations = mutableListOf<String>()
        var foundDynamicLoader = false

        projectRoot
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES && !it.name.startsWith(".") && it.name != "build-logic" }
            .filter { it.isFile && it.extension == "kt" && !it.path.replace(File.separatorChar, '/').contains("/build/") }
            .forEach { file ->
                val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                val sanitized = KotlinSourceScanner.sanitize(file.readText())

                if (sanitized.contains("fun checkOrInstallEngine")) {
                    violations.add("$relPath 暴露了未确认静默下载入口 checkOrInstallEngine（违反扩展下载显式确认红线）")
                }

                if (sanitized.contains("System.load(")) {
                    foundDynamicLoader = true
                    if (!sanitized.contains(".setReadOnly()")) {
                        violations.add("$relPath 调用了 System.load( 但缺少 .setReadOnly()（违反 Android 10+ W^X 只读安全约束）")
                    }
                    if (!sanitized.contains("Mutex()")) {
                        violations.add("$relPath 调用了 System.load( 但缺少 Mutex() 并发下载互斥保护")
                    }
                    if (!sanitized.contains("cleanStalePluginDirs")) {
                        violations.add("$relPath 调用了 System.load( 但缺少版本目录隔离与旧版本清理 cleanStalePluginDirs")
                    }
                }
            }

        assertTrue(foundDynamicLoader, "未找到包含 System.load( 的按需动态库加载实现")
        assertTrue(
            violations.isEmpty(),
            "违反按需动态 .so 装载安全红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * ECH 边缘优选池防污染与本地回环流分流红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `EchHttpClientEngine` 捕获 `CancellationException` 时必须区分真实请求超时（`TimeoutCancellationException`）
     *    与正常协程取消（如 Coil 列表滚动取消图片请求），严禁无条件调用 `recordFailure` 导致 `EchEdgePool` 黄金节点被误杀；
     * 2. 本地回环 HTTP 流必须保留 `isLoopbackHttpUrl` 判定（用于放宽 socket 超时），
     *    但分流动作统一收敛到平台 `RoutingEngine`；`AssetDownloadService` 严禁自建 CIO client、
     *    也严禁把 `http://127.0.0.1` 的 P2P 视频流送进仅支持 `https://` 的 `EchHttpClientEngine`；
     * 3. `crates/minibgm-torrent/src/server.rs` 必须开启 `overwrite: true` 并在初始化异常时 `cancel_token.cancel()`。
     */
    @Test
    fun ech_and_torrent_stream_boundaries_prevent_pool_poisoning_and_loopback_failures() {
        val violations = mutableListOf<String>()

        val echEngineFile =
            File(
                projectRoot,
                "core/network/src/androidMain/kotlin/com/infinitezerone/minibgm/core/network/ech/EchHttpClientEngine.kt",
            )
        assertTrue(echEngineFile.isFile, "EchHttpClientEngine.kt 未找到")
        val echContent = KotlinSourceScanner.sanitize(echEngineFile.readText())
        if (!echContent.contains("TimeoutCancellationException")) {
            violations.add(
                "EchHttpClientEngine.kt 捕获 CancellationException 时未区分 TimeoutCancellationException，正常协程取消会误杀 EchEdgePool 节点",
            )
        }

        val assetServiceFile =
            File(
                projectRoot,
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/AssetDownloadService.kt",
            )
        assertTrue(assetServiceFile.isFile, "AssetDownloadService.kt 未找到")
        val assetContent = KotlinSourceScanner.sanitize(assetServiceFile.readText())
        if (!assetContent.contains("isLoopbackHttpUrl")) {
            violations.add(
                "AssetDownloadService.kt 丢失 isLoopbackHttpUrl 判定，回环流将无法放宽 socket 读取超时",
            )
        }
        if (assetContent.contains("HttpClient(CIO)")) {
            violations.add(
                "AssetDownloadService.kt 不得自建 CIO client：回环分流必须统一由 RoutingEngine 负责",
            )
        }
        val routingEngineFile =
            File(
                projectRoot,
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/transport/RoutingEngine.kt",
            )
        assertTrue(routingEngineFile.isFile, "RoutingEngine.kt 未找到")
        val routingContent = KotlinSourceScanner.sanitize(routingEngineFile.readText())
        if (!routingContent.contains("URLProtocol.HTTP") || !routingContent.contains("plainEngine")) {
            violations.add(
                "RoutingEngine.kt 未把 http 协议路由到 plain 引擎：回环/明文流会被 EchHttpClientEngine 拒绝",
            )
        }

        val torrentServerFile = File(projectRoot, "crates/minibgm-torrent/src/server.rs")
        if (torrentServerFile.isFile) {
            val serverRs = torrentServerFile.readText()
            if (!serverRs.contains("overwrite: true")) {
                violations.add(
                    "crates/minibgm-torrent/src/server.rs AddTorrentOptions 缺少 overwrite: true，二次播放/下载同一剧集将报 File exists (os error 17)",
                )
            }
            if (!serverRs.contains("cancel_token.cancel()")) {
                violations.add("crates/minibgm-torrent/src/server.rs 缺少 cancel_token.cancel() 清理守卫")
            }
            if (!serverRs.contains("resolve_target_file_path")) {
                violations.add(
                    "crates/minibgm-torrent/src/server.rs 未暴露真实落盘路径 resolve_target_file_path，将导致离线下载退化为双倍磁盘写入",
                )
            }
        }

        val torrentRepoFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            )
        assertTrue(torrentRepoFile.isFile, "AndroidTorrentRepository.kt 未找到")
        val repoContent = KotlinSourceScanner.sanitize(torrentRepoFile.readText())
        if (!repoContent.contains("getActiveTargetFilePath") || !repoContent.contains("monitorDirectEngineDownload")) {
            violations.add(
                "AndroidTorrentRepository.kt 未使用 getActiveTargetFilePath + monitorDirectEngineDownload 直接复用 librqbit 落盘文件（严禁走二次 HTTP 拷贝双写）",
            )
        }
        if (!repoContent.contains("scheduleNextIfIdle") || !repoContent.contains("setDownloadSessionProtected")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少串行队列调度 scheduleNextIfIdle 或会话保护 setDownloadSessionProtected，多任务或退出播放器将互相掐断",
            )
        }
        if (!repoContent.contains("peerConnectTimeoutMs") || !repoContent.contains("PEER_CONNECT_TIMEOUT_MESSAGE")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少做种节点连接超时熔断守卫 peerConnectTimeoutMs / PEER_CONNECT_TIMEOUT_MESSAGE，死种或 UDP NAT 阻断将永久卡死串行队列",
            )
        }
        if (!repoContent.contains("isPreempting") || !repoContent.contains("withTimeout")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少抢占互斥状态守卫 isPreempting 或起播获取超时守卫 withTimeout，并发抢占将发生会话掐断或死种永久挂起",
            )
        }
        if (!repoContent.contains("torrentTaskDao") || !repoContent.contains("TorrentTaskDao")) {
            violations.add(
                "AndroidTorrentRepository.kt 必须依赖 TorrentTaskDao 进行 ACID 任务持久化，严禁裸文件 tasks_manifest.json 覆写作为主存储",
            )
        }
        if (repoContent.contains("persistTasksSnapshot")) {
            violations.add(
                "AndroidTorrentRepository.kt 严禁使用全量快照覆写 persistTasksSnapshot，高频进度不得落库，关键状态必须单行有序提交",
            )
        }
        if (!repoContent.contains("restoreDeferred") || !repoContent.contains("cancelAndJoin")) {
            violations.add(
                "AndroidTorrentRepository.kt 必须包含 restoreDeferred 启动门闩与 cancelAndJoin 停机同步守卫",
            )
        }

        val dataModuleAndroidFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/di/DataModule.android.kt",
            )
        assertTrue(dataModuleAndroidFile.isFile, "DataModule.android.kt 未找到")
        val dataModuleContent = KotlinSourceScanner.sanitize(dataModuleAndroidFile.readText())
        if (dataModuleContent.contains("getOrNull<TorrentTaskDao>()") ||
            dataModuleContent.contains("torrentTaskDao = getOrNull()")
        ) {
            violations.add(
                "DataModule.android.kt 中 torrentTaskDao 必须强定义注入 get()，严禁使用 getOrNull() 静默回退",
            )
        }

        val streamEngineFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            )
        assertTrue(streamEngineFile.isFile, "AndroidTorrentStreamEngine.kt 未找到")
        val engineContent = KotlinSourceScanner.sanitize(streamEngineFile.readText())
        if (!engineContent.contains("downloadSessionProtected") || !engineContent.contains("playerSessionActive")) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 缺少 downloadSessionProtected / playerSessionActive 双消费者生命周期协调守卫",
            )
        }

        val sourcesSheetFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt",
            )
        if (sourcesSheetFile.isFile) {
            val sheetContent = KotlinSourceScanner.sanitize(sourcesSheetFile.readText())
            if (sheetContent.contains("torrentStreamEngine.startStream(") ||
                sheetContent.contains("torrentStreamEngine.") ||
                !sheetContent.contains("torrentRepository.startStreamDownload(")
            ) {
                violations.add(
                    "SubjectSourcesBottomSheet.kt 必须完全面向 torrentRepository 调度边下边播与插件校验，严禁直接引用或调用 torrentStreamEngine.*",
                )
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反 ECH 优选池防污染与 BT 流播/下载单盘协同红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * JNI 符号绑定与流统计失败信号红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. Kotlin 侧 6 个 `external fun` 必须各自带 `@JvmStatic`：只有 `@JvmStatic` 会把 native 跳板
     *    生成在**外层类** `AndroidTorrentStreamEngine` 上（`$Companion` 只留转发器），符号名才成立为扁平的
     *    `Java_com_..._AndroidTorrentStreamEngine_nativeXxx`；漏掉即变成 `..._00024Companion_nativeXxx`，
     *    真机 `UnsatisfiedLinkError` 闪退——而单测与静态检查面对的都是预编译 `.so`，这条链路只能靠契约守住；
     * 2. Rust 侧必须逐个导出与之同名的符号（改名或漏一个都是同一场真机闪退）；
     * 3. Kotlin 侧必须保留 `stats.size < STATS_ARRAY_SIZE`（=5）的失败判据，且 Rust 侧无活跃会话时必须走
     *    `stats_to_jni` 的 `None` 分支返回长度不足的数组——严禁全零数组兜底：那会把"引擎失联"伪装成
     *    "健康的零进度"，`MAX_CONSECUTIVE_STAT_FAILURES` 永不计数，`streamState` 既不发射也不收敛 Error，
     *    下游 `first {}` 与死种看门狗双双无界挂起（任务永远停在 DOWNLOADING）；
     * 4. 第 5 位（下标 4）的引擎状态码与 `nativeGetLastError` 必须成对存在：librqbit 的
     *    `TorrentStatsState::Error`（磁盘写入失败 / 初始化校验失败）过去被折叠成"0 速度 0 Peer"，
     *    用户只能在死种看门狗超时后被告知「连不上做种节点」——真实原因被伪装成死种。
     */
    @Test
    fun jni_symbol_binding_and_stats_failure_signal_keep_kotlin_and_rust_in_lockstep() {
        val violations = mutableListOf<String>()

        val nativeMethods =
            listOf(
                "nativeIsAvailable",
                "nativeStartStream",
                "nativeStopStream",
                "nativeGetStreamStats",
                "nativeGetLastError",
                "nativeGetTargetFilePath",
            )

        val engineFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            )
        assertTrue(engineFile.isFile, "AndroidTorrentStreamEngine.kt 未找到")
        val engineContent = KotlinSourceScanner.sanitize(engineFile.readText())

        // @JvmStatic 必须紧贴各自的声明站点：只断言"文件里出现过 @JvmStatic"会在漏掉其中一个时静默放行。
        var cursor = 0
        nativeMethods.forEach { name ->
            val index = engineContent.indexOf("external fun $name", cursor)
            if (index < 0) {
                violations.add(
                    "AndroidTorrentStreamEngine.kt 缺少 external fun $name（Rust 侧符号将无人绑定）",
                )
                return@forEach
            }
            if (!engineContent.substring(cursor, index).contains("@JvmStatic")) {
                violations.add(
                    "AndroidTorrentStreamEngine.kt 的 external fun $name 缺少 @JvmStatic：" +
                        "native 会落在外层类之外，JNI 符号变为 ..._00024Companion_$name，真机 UnsatisfiedLinkError",
                )
            }
            cursor = index
        }

        if (!engineContent.contains("stats.size < STATS_ARRAY_SIZE")) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 缺少 stats.size < STATS_ARRAY_SIZE 失败判据：长度不足的统计数组会被当成有效值，引擎失联将不再收敛 Error",
            )
        }
        if (!engineContent.contains("STATS_ARRAY_SIZE = 5") || !engineContent.contains("STATS_INDEX_STATE = 4")) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 统计数组布局常量（长度 5 / 状态码下标 4）与 Rust 侧 stats_to_jni 失配",
            )
        }
        if (!engineContent.contains("STATS_STATE_ENGINE_ERROR") ||
            !engineContent.contains("getLastError") ||
            !engineContent.contains("resolveEngineErrorReason")
        ) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 未消费引擎级错误状态码/错误原文：librqbit 判定种子 Error（磁盘写满、初始化校验失败）时只会表现为 0 速度，" +
                    "用户将在 45 秒后被死种看门狗告知「连不上做种节点」，真实原因被伪装成死种",
            )
        }

        val rustLibFile = File(projectRoot, "crates/minibgm-torrent/src/lib.rs")
        assertTrue(rustLibFile.isFile, "crates/minibgm-torrent/src/lib.rs 未找到")
        val rustLib = rustLibFile.readText()
        nativeMethods.forEach { name ->
            val symbol =
                "Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_$name"
            if (!rustLib.contains("pub extern \"system\" fn $symbol(")) {
                violations.add(
                    "crates/minibgm-torrent/src/lib.rs 未导出 $symbol：Kotlin 侧调用将 UnsatisfiedLinkError",
                )
            }
        }
        if (!rustLib.contains("fn stats_to_jni(") || !rustLib.contains("None => return empty_stats_array(&env)")) {
            violations.add(
                "crates/minibgm-torrent/src/lib.rs nativeGetStreamStats 缺少无会话失败路径（stats_to_jni 返回 None 必须落为长度 0 的数组），全零数组兜底会伪装成健康零进度",
            )
        }
        if (!rustLib.contains("-> Option<[i64; 5]>") || !rustLib.contains("env.new_long_array(5)")) {
            violations.add(
                "crates/minibgm-torrent/src/lib.rs 统计数组长度不是 5：Kotlin 侧按 STATS_ARRAY_SIZE=5 判失败，" +
                    "长度错位会让每次读取都被判为失败并误收敛 STATS_UNAVAILABLE_MESSAGE",
            )
        }
        if (!rustLib.contains("STATS_STATE_ENGINE_ERROR")) {
            violations.add(
                "crates/minibgm-torrent/src/lib.rs 未把引擎错误态编码进统计数组第 5 位：Kotlin 侧无从区分「引擎级致命错误」与「死种 0 速度」",
            )
        }
        val rustServerFile = File(projectRoot, "crates/minibgm-torrent/src/server.rs")
        assertTrue(rustServerFile.isFile, "crates/minibgm-torrent/src/server.rs 未找到")
        val rustServer = rustServerFile.readText()
        if (!rustServer.contains("TorrentStatsState::Error") || !rustServer.contains("engine_error")) {
            violations.add(
                "crates/minibgm-torrent/src/server.rs 未把 librqbit TorrentStatsState::Error 映射进 StreamStats：致命错误会退化成 0 速度 0 Peer",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反 JNI 符号绑定与流统计失败信号红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 播放优先红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 单实例 librqbit 会话下，后台离线下载起任何新任务都会 release 掉当前会话；若此时前台播放器
     * 正在消费该本地回环流（"看第 5 集时下第 6 集"），用户的画面会被直接掐断且全程无提示。
     * 因此下载调度必须同时具备两半，缺一不可：
     * 1. 礼让：播放器消费本地流期间保持 QUEUED（判据 `isPlayerSessionActive()`），
     *    只有用户显式点播（`allowPlayerTakeover`）才允许接管会话；
     * 2. 唤醒：订阅 `playerSessionActive` 流，在播放器释放会话时重新调度——只读一次布尔值感知不到
     *    "释放"，被礼让的任务会永久停在 QUEUED（通知栏挂着下载中却永远 0 字节）。
     * 契约侧必须把该状态暴露为可观察流，否则第 2 半无从实现。
     */
    @Test
    fun offline_download_must_yield_to_active_playback_session() {
        val violations = mutableListOf<String>()

        val repoFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            )
        assertTrue(repoFile.isFile, "AndroidTorrentRepository.kt 未找到")
        val repoContent = KotlinSourceScanner.sanitize(repoFile.readText())
        if (!repoContent.contains("isPlayerSessionActive")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少 isPlayerSessionActive 礼让判据：后台下载会在播放期间起新任务并掐断用户正在看的本地流",
            )
        }
        if (!repoContent.contains("allowPlayerTakeover")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少 allowPlayerTakeover 显式接管开关：用户主动点播的边下边播会被自己的礼让判据卡死到超时",
            )
        }
        if (!repoContent.contains("playerSessionActive.collect")) {
            violations.add(
                "AndroidTorrentRepository.kt 未订阅 playerSessionActive：播放器释放会话后无人唤醒，被礼让的任务将永久停在 QUEUED",
            )
        }

        val contractFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/download/TorrentStreamEngine.kt",
            )
        assertTrue(contractFile.isFile, "TorrentStreamEngine.kt 未找到")
        val contractContent = KotlinSourceScanner.sanitize(contractFile.readText())
        if (!contractContent.contains("val playerSessionActive: StateFlow<Boolean>")) {
            violations.add(
                "TorrentStreamEngine 契约未暴露 val playerSessionActive: StateFlow<Boolean>：调度侧无法感知播放会话释放，被礼让的排队任务只能在应用重启后消化",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放优先红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力文件挑选链路红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `server.rs` 必须以 `paused: true` 添加种子，并在锁定单集后调用 `session.unpause(&handle)`：
     *    否则元数据阶段会先按整包视频选片再收敛单集，合集/BD 包会白下其他剧集的分片；
     * 2. 视频扩展名白名单必须单一真源（`VIDEO_EXTENSIONS` 同时派生 `only_files_regex` 与 `is_video_file`），
     *    严禁出现第二份硬编码扩展名清单；
     * 3. 目标单集必须经 `select_target_video_file` 挑选，且多视频文件未命中集数时必须显式失败，
     *    严禁静默回退体积最大文件（合集/BD 包会稳定挑错集且全程无提示）。
     */
    @Test
    fun torrent_file_selection_locks_single_episode_before_starting_download() {
        val torrentServerFile = File(projectRoot, "crates/minibgm-torrent/src/server.rs")
        assertTrue(torrentServerFile.isFile, "crates/minibgm-torrent/src/server.rs 未找到")
        val serverRs = torrentServerFile.readText()
        // 单测里为锁定派生结果会显式写出期望正则字面量，漂移检查只看生产代码
        val productionRs = serverRs.substringBefore("#[cfg(test)]")

        val violations = mutableListOf<String>()
        if (!productionRs.contains("paused: true")) {
            violations.add(
                "server.rs 未以 paused: true 添加种子：元数据阶段会先按整包视频选片，合集/BD 包将白下其他剧集分片",
            )
        }
        if (!productionRs.contains(".unpause(&handle)")) {
            violations.add(
                "server.rs 锁定单集后未调用 session.unpause(&handle)，paused 添加将导致永不开始下载",
            )
        }
        if (!productionRs.contains("VIDEO_EXTENSIONS")) {
            violations.add("server.rs 视频扩展名白名单未收口到 VIDEO_EXTENSIONS 单一真源")
        }
        if (productionRs.contains("mkv|mp4|webm|avi|flv|ts|m2ts")) {
            violations.add("server.rs 存在第二份硬编码视频扩展名清单，会与 VIDEO_EXTENSIONS 静默漂移")
        }
        if (!productionRs.contains("select_target_video_file") || !productionRs.contains("未能按集数")) {
            violations.add(
                "server.rs 必须经 select_target_video_file 挑选目标单集，并对多视频未命中集数显式失败（严禁静默回退体积最大文件）",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力文件挑选链路红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力分集隔离与种子载荷校验红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `AndroidTorrentRepository.startDownload` 去重必须同时比对分集身份键 `episodeKey`：
     *    合集/BD 包内所有分集共用同一磁力，只比 infoHash 会让点第 N 集命中第 M 集的既有任务，
     *    直接复用其 `streamUrl`（或已完成任务的 `file://` 落盘路径），静默播错集且全程无提示；
     * 2. `AndroidTorrentStreamEngine.startStream` 会话复用必须比对 `activeEpisodeHint`：
     *    同磁力换集必须重新解析目标文件，否则复用上一集的流与 `targetFile`；
     * 3. `SubjectSourcesBottomSheet` 的已完成任务快捷复用必须同样比对 `episodeKey`；
     * 4. 种子（`.torrent`）载荷必须经 `TorrentFileCache` 校验：镜像返回 HTTP 200 的 HTML 错误页时
     *    `AssetDownloader` 只按字节数判成功，缓存命中若只看「文件名 + 非空」会让坏种子被永久复用
     *    （离线下载还会把它写进 DB 的 `torrentFilePath`，重启后继续命中）；
     * 5. 起播等待超时必须以 `Result.failure` 返回：`getOrThrow()` 抛出的 `CancellationException`
     *    会连带取消调用方协程，UI 的 `onFailure` 永不执行，用户全程无任何提示。
     */
    @Test
    fun torrent_episode_isolation_and_payload_validation_guards_are_enforced() {
        val torrentRepoFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            )
        val streamEngineFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            )
        val sheetFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt",
            )
        assertTrue(torrentRepoFile.isFile, "AndroidTorrentRepository.kt 未找到")
        assertTrue(streamEngineFile.isFile, "AndroidTorrentStreamEngine.kt 未找到")
        assertTrue(sheetFile.isFile, "SubjectSourcesBottomSheet.kt 未找到")

        val repoContent = KotlinSourceScanner.sanitize(torrentRepoFile.readText())
        val engineContent = KotlinSourceScanner.sanitize(streamEngineFile.readText())
        val sheetContent = KotlinSourceScanner.sanitize(sheetFile.readText())

        val violations = mutableListOf<String>()
        if (!repoContent.contains("task.episodeKey == incomingEpisodeKey")) {
            violations.add(
                "AndroidTorrentRepository.startDownload 去重缺少分集身份键比对（episodeKey）：合集/BD 包内换集将复用上一集的 streamUrl 或 file:// 落盘路径，静默播错集",
            )
        }
        if (!repoContent.contains("TorrentFileCache.isValidPayload(") ||
            !repoContent.contains("TorrentFileCache.isUsableCachedFile(")
        ) {
            violations.add(
                "AndroidTorrentRepository 下载/复用种子文件未经 TorrentFileCache 校验载荷：镜像 HTML 错误页会被当作成功种子永久复用",
            )
        }
        // runTaskPipeline 从 DB 取回的 torrentFilePath 同样必须校验：坏种子一旦写进 DB，
        // 每次启动都会把该路径直接喂给引擎，坏种子永久复现（用户只能清应用数据自救）。
        val pipelineStart = repoContent.indexOf("private suspend fun runTaskPipeline")
        val pipelineEnd = repoContent.indexOf("private suspend fun monitorDirectEngineDownload")
        if (pipelineStart < 0 || pipelineEnd <= pipelineStart) {
            violations.add(
                "AndroidTorrentRepository.runTaskPipeline 结构锚点失效：红线无法定位种子路径复用逻辑，请同步更新红线",
            )
        } else if (!repoContent.substring(pipelineStart, pipelineEnd).contains("TorrentFileCache.isValidPayload(")) {
            violations.add(
                "AndroidTorrentRepository.runTaskPipeline 直接复用 DB 中的 torrentFilePath 而未校验载荷：失效或坏种子会被永久复用",
            )
        }
        if (repoContent.contains("getOrThrow()")) {
            violations.add(
                "AndroidTorrentRepository 使用 getOrThrow() 表达业务失败：CancellationException 会连带取消调用方协程，UI onFailure 永不执行",
            )
        }
        if (!repoContent.contains("catch (e: TimeoutCancellationException)") ||
            !repoContent.contains("STREAM_ACQUISITION_TIMEOUT_MESSAGE")
        ) {
            violations.add(
                "AndroidTorrentRepository.startStreamDownload 缺少起播等待超时守卫：死种将无界挂起或直接取消调用方协程",
            )
        }
        // 用词边界锚定标识符本体：改名（activeEpisodeHintX）或只留注释都不得蒙混过关
        if (Regex("\\bactiveEpisodeHint\\b").findAll(engineContent).count() < 4 ||
            !engineContent.contains("activeEpisodeHint == normalizedEpisodeHint") ||
            !engineContent.contains("activeEpisodeHint = null")
        ) {
            violations.add(
                "AndroidTorrentStreamEngine 会话复用缺少 activeEpisodeHint 声明/比对/赋值/重置四处闭环：同磁力换集将复用上一集的会话与 targetFile",
            )
        }
        if (!engineContent.contains("TorrentFileCache.isUsableCachedFile(")) {
            violations.add(
                "AndroidTorrentStreamEngine 预取/复用本地种子文件未经 TorrentFileCache 校验载荷，坏种子会随 DB 路径被永久复用",
            )
        }
        // 选源面板既有任务复用判据：锚定 `completedTaskFor` 的「分集身份键 + 源同一性」双判据本体。
        // 曾经锚定旧的 `task.episodeKey == requestedEpisodeKey` 字面量——面板重构把比对收进
        // `completedTaskFor` 后该字面量消失，红线必须以结构（函数 + 两处判据）而非旧写法为锚，
        // 否则「重命名/抽函数」这类等价重构会被误判为违规，而真正的判据删失又会漏检。
        if (!sheetContent.contains("fun completedTaskFor(") ||
            !sheetContent.contains("task.episodeKey ==") ||
            !sheetContent.contains("matchesResourceSource(resource)")
        ) {
            violations.add(
                "SubjectSourcesBottomSheet 已完成任务快捷复用缺少「分集身份键 + 源同一性」双判据：点第 N 集会直接播第 M 集已落盘的文件，或让不同来源互相复用落盘路径",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力分集隔离与种子载荷校验红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力链路「交互与业务逻辑」红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. 分集检索未命中只允许回退到合集项（`episodeSort == null`），严禁回退整份 Feed：
     *    否则请求第 5 集会看到第 1-4 集，点单文件资源还会被 Rust 侧「单一候选即选中」静默播成第 1 集；
     * 2. 多规则结果合并去重必须走 `TorrentMediaResource.dedupeKey`（infoHash → magnetUrl → torrentUrl → title）：
     *    只按 `infoHash.ifBlank { magnetUrl }` 判重时，未携带 40 位哈希的 `.torrent` 直链条目会集体退化为
     *    空串，`distinctBy` 只保留第一条，静默丢掉同 Feed 的其余条目；
     * 3. 条目级（未选分集）动作严禁硬编码假集数（`?: 1f`）：那会把所有条目级资源塌缩成同一个
     *    `sort:1.0` 身份键，并把 `episodeHint="1"` 喂给 Rust 的分集选片逻辑；必须回退资源自身 `episodeSort`；
     * 4. 离线下载（后台大任务）必须经 `BgmOverlayHost` 显式确认并披露体积/可用空间；
     * 5. 流统计轮询一次失败严禁 `break`（会永久中断状态流，下游 `first{}` 与死种看门狗双双无界挂起），
     *    必须容忍瞬时失败并只在连续失败超阈值时收敛为 `Error`；
     * 6. 流进度必须经 `TorrentRepository.streamState` 暴露给 UI，feature 层严禁直引 `TorrentStreamEngine`。
     */
    @Test
    fun torrent_ui_and_business_guards_enforce_confirmation_dedupe_and_stream_feedback() {
        val violations = mutableListOf<String>()

        fun read(relativePath: String): String {
            val file = File(projectRoot, relativePath)
            assertTrue(file.isFile, "$relativePath 未找到")
            return KotlinSourceScanner.sanitize(file.readText())
        }

        // 1. 分集未命中只回退合集项
        val feedRepo = read("core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentFeedRepository.kt")
        if (feedRepo.contains("matched.isNotEmpty()")) {
            violations.add(
                "TorrentFeedRepository.filterAndSort 用 matched.isNotEmpty() 回退了整份 Feed：分集未命中会让用户看到并点到别的集",
            )
        }
        if (!feedRepo.contains("resources.filter { it.episodeSort == null }")) {
            violations.add(
                "TorrentFeedRepository.filterAndSort 缺少「只回退合集项（episodeSort == null）」的兜底",
            )
        }

        // 2. 去重键必须收敛到 dedupeKey
        listOf(
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectDetailViewModel.kt",
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/EpisodeDetailViewModel.kt",
        ).forEach { path ->
            val content = read(path)
            if (content.contains("infoHash.ifBlank")) {
                violations.add(
                    "$path 仍在就地拼 `infoHash.ifBlank { magnetUrl }` 去重键：无哈希的 .torrent 直链条目会被静默丢弃，改用 dedupeKey",
                )
            }
            if (!content.contains("distinctBy { it.dedupeKey }")) {
                violations.add("$path 未使用 TorrentMediaResource.dedupeKey 做去重")
            }
        }

        // 3~5. 磁力 UI 与流引擎
        val sheet =
            read("feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt")
        if (Regex("\\?:\\s*1f").containsMatchIn(sheet)) {
            violations.add(
                "SubjectSourcesBottomSheet 仍把条目级动作硬编码成「第 1 话」（?: 1f）：身份键会塌缩、episodeHint 会喂假集数",
            )
        }
        if (!sheet.contains("resource.episodeSort")) {
            violations.add("SubjectSourcesBottomSheet 未回退资源自身 episodeSort 作为分集身份")
        }
        val downloadStart = sheet.indexOf("suspend fun downloadTorrentResource")
        val downloadEnd = sheet.indexOf("private suspend fun ensureTorrentPluginInstalled")
        if (downloadStart < 0 || downloadEnd <= downloadStart) {
            violations.add("SubjectSourcesBottomSheet.downloadTorrentResource 结构锚点失效，请同步更新红线")
        } else {
            val downloadBody = sheet.substring(downloadStart, downloadEnd)
            if (!downloadBody.contains("overlayHostState.await(")) {
                violations.add(
                    "离线下载未走 BgmOverlayHost 显式确认：后台大任务严禁静默入队",
                )
            }
            if (!downloadBody.contains("feature_subject_source_torrent_download_confirm_title") ||
                !downloadBody.contains("getStorageUsage()")
            ) {
                violations.add("离线下载确认弹窗未披露资源体积与设备可用空间")
            }
            val confirmIndex = downloadBody.indexOf("overlayHostState.await(")
            val startIndex = downloadBody.indexOf("startDownload(")
            if (confirmIndex < 0 || startIndex < 0 || confirmIndex > startIndex) {
                violations.add("离线下载的 startDownload 必须晚于用户确认（当前确认未前置于落库调用）")
            }
        }

        val streamEngine =
            read("core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt")
        if (streamEngine.contains("getStreamStats() }.getOrNull() ?: break")) {
            violations.add(
                "AndroidTorrentStreamEngine 流统计轮询仍以 `?: break` 永久中断状态流：下游 first{} 与死种看门狗会无界挂起",
            )
        }
        if (!streamEngine.contains("MAX_CONSECUTIVE_STAT_FAILURES")) {
            violations.add("AndroidTorrentStreamEngine 缺少连续失败阈值，瞬时抖动与真实故障无法区分")
        }

        // 6. 流状态只经仓库契约暴露
        val repoContract = read("core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentRepository.kt")
        if (!repoContract.contains("val streamState: StateFlow<TorrentStreamState>")) {
            violations.add("TorrentRepository 未暴露只读 streamState，UI 无法呈现起播进度")
        }
        // 播放来源面板只允许经仓库契约消费流进度（引擎控制面另有既定入口，不在此红线范围）
        val sheetPath = "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        if (sheet.contains("import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine")) {
            violations.add("$sheetPath 直引 TorrentStreamEngine：流进度只能经 TorrentRepository.streamState 消费")
        }
        if (!sheet.contains("torrentRepository.streamState")) {
            violations.add("$sheetPath 未消费 TorrentRepository.streamState，起播进度与失败原因对用户不可见")
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力交互与业务逻辑红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力实现收敛红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `infoHash` 解析必须收敛到 `:core:model` 的 `magnetInfoHash` 唯一真源：
     *    历史上引擎 / 仓库 / 缓存 / UI 四处各写一遍，引擎侧还降级成 `"unknown_hash"` 哨兵、
     *    其余处返回空串——同一语义两套降级，是"未知磁力互相误判为同一源"的温床；
     * 2. 传输速率与体积格式化只允许走 `formatTransferSpeed` / `DownloadStorageUsage.formatBytes`
     *    真源，严禁页面内再手搓 `KB/s` 分支（副本漂移会让同一数值在两处显示不一致）；
     * 3. 种子缓存键的直链指纹必须是强摘要（SHA-256 截断），严禁 `hashCode().toHexString()`
     *    这种 32 位可碰撞的弱哈希——碰撞会让两个不同种子命中同一缓存文件，而载荷校验只验
     *    bencode 结构不验身份，会接受"合法但错误"的种子；
     * 4. 「停止起播」必须真停底层任务（经 `onTaskQueued` 拿到 taskId → `pauseDownload`）：
     *    只 `busyJob?.cancel()` 会让任务继续占着单实例 P2P 队列，并在下载页留下用户从未创建过的条目；
     * 5. 既有任务比对必须含 `torrentUrl` 兜底：只比 `magnetUrl` 时 torrentUrl-only 资源两侧都是空串，
     *    `"" == ""` 会互相误命中并复用错误的流地址或落盘路径。
     */
    @Test
    fun torrent_implementation_consolidation_guards_are_enforced() {
        val violations = mutableListOf<String>()
        val infoHashSourceOfTruth =
            "core/model/src/commonMain/kotlin/com/infinitezerone/minibgm/core/model/TorrentMediaResource.kt"

        fun raw(relativePath: String): String {
            val file = File(projectRoot, relativePath)
            assertTrue(file.isFile, "$relativePath 未找到")
            return file.readText()
        }

        fun code(relativePath: String): String = KotlinSourceScanner.sanitize(raw(relativePath))

        // 1. infoHash 解析唯一真源（原始文本扫描：违规形态必然落在字符串字面量里，sanitize() 会抹掉字符串）
        val handrolledParse = "substringAfter(\"xt=urn:btih:\""
        listOf("core", "feature", "app", "sync").forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val rel = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    if (rel != infoHashSourceOfTruth && file.readText().contains(handrolledParse)) {
                        violations.add("$rel 重复手写 infoHash 解析，必须调用 :core:model 的 magnetInfoHash 唯一真源")
                    }
                }
        }
        if (!code(infoHashSourceOfTruth).contains("fun magnetInfoHash(")) {
            violations.add("$infoHashSourceOfTruth 缺少 magnetInfoHash 唯一真源")
        }

        // 2. 哨兵降级彻底退场（引擎曾用 "unknown_hash"，其余处返回空串）
        listOf(
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/TorrentFileCache.kt",
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            "core/testing/src/commonMain/kotlin/com/infinitezerone/minibgm/core/testing/repository/FakeTorrentStreamEngine.kt",
        ).forEach { path ->
            if (raw(path).contains("unknown_hash")) {
                violations.add("$path 仍残留 infoHash 哨兵降级 unknown_hash：未知源必须统一以空串 + isBlank 判定")
            }
        }

        // 3. 速率 / 体积格式化唯一真源
        val downloadScreen = "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/DownloadScreen.kt"
        if (!code(downloadScreen).contains("formatTransferSpeed(")) {
            violations.add("$downloadScreen 未消费 formatTransferSpeed 真源")
        }
        if (raw(downloadScreen).contains("KB/s") || raw(downloadScreen).contains("MB/s")) {
            violations.add("$downloadScreen 内联手搓速率单位，必须调用 formatTransferSpeed")
        }
        val torrentModels = "core/model/src/commonMain/kotlin/com/infinitezerone/minibgm/core/model/TorrentModels.kt"
        if (!code(torrentModels).contains("fun formatTransferSpeed(")) {
            violations.add("$torrentModels 缺少 formatTransferSpeed 真源")
        }
        if (!code(torrentModels).contains("fun formatDownloadProgressPair(")) {
            violations.add("$torrentModels 缺少 formatDownloadProgressPair 真源（已下载/总量进度文案）")
        }
        if (!code(torrentModels).contains("DownloadStorageUsage.formatBytes")) {
            violations.add("$torrentModels 体积格式化未收敛到 DownloadStorageUsage.formatBytes 真源")
        }
        val downloadModels = "core/model/src/commonMain/kotlin/com/infinitezerone/minibgm/core/model/DownloadModels.kt"
        listOf("formattedSpeed", "formattedProgress").forEach { duplicate ->
            if (code(downloadModels).contains(duplicate)) {
                violations.add(
                    "$downloadModels 仍残留 $duplicate 重复格式化实现：速率与体积格式化只能有 :core:model 的单一真源",
                )
            }
        }
        val rssParser = "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/resolver/TorrentRssParser.kt"
        if (!code(rssParser).contains("DownloadStorageUsage.formatBytes(")) {
            violations.add("$rssParser 体积格式化未收敛到 DownloadStorageUsage.formatBytes 真源")
        }

        // 4. 种子缓存键强摘要
        val cache = "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/TorrentFileCache.kt"
        if (!raw(cache).contains("SHA-256") || !code(cache).contains("MessageDigest")) {
            violations.add("$cache 种子缓存键未使用 SHA-256 强摘要")
        }
        if (code(cache).contains("toHexString(")) {
            violations.add("$cache 仍用 toHexString(hashCode()) 弱指纹做缓存键：32 位碰撞会命中错误种子")
        }

        // 5. 停止起播必须真停任务 + 源同一性必须含 torrentUrl
        val sheetPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        val sheet = code(sheetPath)
        if (!sheet.contains("pauseDownload(")) {
            violations.add("$sheetPath 停止起播未调用 pauseDownload：只取消等待协程会让任务继续占着 P2P 队列")
        }
        if (!sheet.contains("onTaskQueued")) {
            violations.add("$sheetPath 未接住 onTaskQueued 回调：拿不到 taskId 就无法真正停止底层任务")
        }
        if (sheet.contains("onCancelBusy")) {
            violations.add("$sheetPath 仍暴露 onCancelBusy 旧语义：停止起播的语义是真停任务，不是只取消等待")
        }
        if (!sheet.contains("matchesResourceSource") || !sheet.contains("torrentUrl")) {
            violations.add("$sheetPath 既有任务比对缺少 torrentUrl 兜底：torrentUrl-only 资源会以空磁力互相误命中")
        }
        val repoContract =
            "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentRepository.kt"
        if (!code(repoContract).contains("onTaskQueued")) {
            violations.add("$repoContract.startStreamDownload 未声明 onTaskQueued：UI 无法在任务落库后拿到身份")
        }
        val repoImpl =
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt"
        if (!code(repoImpl).contains("onTaskQueued")) {
            violations.add("$repoImpl 未实现 onTaskQueued 契约")
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力实现收敛红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 应用内更新下载的可见性红线（见 AGENTS.md 领域规则 & 剧本 12）：
     * 更新 APK 是数十 MB 的后台大任务，而设置页长期只弹一次固定文案「正在下载新版本… 0%」——
     * 真实进度（`DownloadState.Downloading`）与取消入口（`UpdateRepository.cancelDownload` /
     * `UserViewModel.cancelUpdateDownload`）整条链路都已实现且状态一直在发射，却没有任何消费者：
     * 用户既看不到进度也无法中止，只能杀进程，而固定文案还在持续宣称一个假的 0%。
     *
     * 机械判据：
     * 1. 进度必须来自真实状态（`DownloadState.Downloading` + `percentage`），且未知总量
     *    （`isIndeterminate`，服务端未给 Content-Length）不得当成 0%；
     * 2. 必须提供取消入口（`cancelUpdateDownload(`）；
     * 3. 严禁把百分比硬编码成常量传给下载中文案（原始文本扫描，字符串字面量在 sanitize 后会被抹掉）。
     */
    @Test
    fun update_flow_must_surface_real_progress_and_cancel() {
        val relativePath = "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/SettingsScreen.kt"
        val file = File(projectRoot, relativePath)
        assertTrue(file.isFile, "$relativePath 未找到")
        val raw = file.readText()
        val code = KotlinSourceScanner.sanitize(raw)
        val violations = mutableListOf<String>()

        if (!code.contains("DownloadState.Downloading")) {
            violations.add("$relativePath 未消费 DownloadState.Downloading：更新下载对用户无进度")
        }
        if (!code.contains("isIndeterminate")) {
            violations.add("$relativePath 未区分未知总量（isIndeterminate）：无 Content-Length 时会显示假的 0%")
        }
        if (!code.contains("percentage")) {
            violations.add("$relativePath 未使用进度百分比：更新下载进度不可见")
        }
        if (!code.contains("cancelUpdateDownload(")) {
            violations.add("$relativePath 未提供取消入口：后台大任务无法中止，用户只能杀进程")
        }
        val hardcodedPercent = Regex("feature_user_update_downloading[^)]*\"\\d+%\"")
        if (hardcodedPercent.containsMatchIn(raw)) {
            violations.add(
                "$relativePath 仍把更新下载进度硬编码成固定百分比：文案必须由 DownloadState.Downloading 驱动",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反应用内更新下载可见性红线（见 AGENTS.md 领域规则 & 剧本 12）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * Flow 算子副作用收敛红线：`map` / `onEach` / `filter` / `transform` / `mapNotNull`
     * 等算子的 lambda 内严禁 `launch` 协程。
     *
     * 任务流在下载期间每秒都会推送进度，把 `launch` 写进算子等于「每次发射都新起一个协程」：
     * 无人等待、无界堆积，`DownloadViewModel` 曾因此在下载页每秒并发触发一次全量
     * `walkTopDown` 扫盘。需要持续刷新时必须改为单条收集协程串行执行——StateFlow 自带
     * conflate，扫描期间的发射会被丢弃，天然背压，始终只有一个任务在跑。
     */
    @Test
    fun flow_operators_never_launch_side_effect_coroutines() {
        val scanRoots =
            listOf(
                File(projectRoot, "core"),
                File(projectRoot, "feature"),
                File(projectRoot, "app"),
                File(projectRoot, "sync"),
            )
        val launchInOperator =
            Regex(
                """\.(map|onEach|filter|transform|mapNotNull|mapLatest|transformLatest)\s*\{[^}]*\blaunch\s*[\{\(]""",
            )
        val violations = mutableListOf<String>()
        scanRoots.forEach { dir ->
            if (dir.isDirectory) {
                dir
                    .walkTopDown()
                    .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val code = KotlinSourceScanner.sanitize(file.readText())
                        val match = launchInOperator.find(code) ?: return@forEach
                        val line = code.take(match.range.first).count { it == '\n' } + 1
                        val rel = file.relativeTo(projectRoot).path
                        violations.add(
                            "$rel:$line 在 Flow 算子 lambda 内 launch 副作用协程：每次发射都会新起协程且无界堆积，请改为单条收集协程串行执行",
                        )
                    }
            }
        }
        assertTrue(
            violations.isEmpty(),
            "违反 Flow 算子副作用收敛红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 仓库契约参数实现闭环红线：`TorrentFeedRepository.getTorrentResources` 的
     * `alternativeTitles` 曾是死参数——接口声明、两个调用方都在传，实现体却从未读取。
     * 迁移自旧专有实现时把别名回退检索与它的单测一起删掉了，只留下签名，
     * 于是「站点按原名/别名索引、主标题检索为空」时用户看到的是稳定的空列表，
     * 调用方传参被静默丢弃且全程无任何报错。
     *
     * 机械判据：接口声明的每个参数名，在实现体内必须至少出现两次
     * （一次是 override 签名，一次是真实读取），杜绝只留签名不写实现的假契约。
     */
    @Test
    fun repository_interface_parameters_must_be_read_in_implementation() {
        val file =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentFeedRepository.kt",
            )
        assertTrue(file.isFile, "TorrentFeedRepository.kt 未找到")
        val content = KotlinSourceScanner.sanitize(file.readText())

        val violations = mutableListOf<String>()
        val signature =
            Regex("""suspend fun getTorrentResources\(([\s\S]*?)\)\s*:\s*List<TorrentMediaResource>""")
                .find(content)
                ?.groupValues
                ?.get(1)
        val implStart = content.indexOf("class TorrentFeedRepositoryImpl")
        if (signature == null) {
            violations.add("TorrentFeedRepository.getTorrentResources 接口签名锚点失效，请同步更新红线")
        }
        if (implStart < 0) {
            violations.add("TorrentFeedRepositoryImpl 未找到，红线无法定位实现体")
        }
        if (signature != null && implStart >= 0) {
            val implBody = content.substring(implStart)
            val paramNames =
                Regex("""(?m)^\s*(\w+)\s*:""")
                    .findAll(signature)
                    .map { it.groupValues[1] }
                    .toList()
            if (paramNames.isEmpty()) {
                violations.add("未能从 getTorrentResources 签名解析出参数名，请同步更新红线")
            }
            paramNames.forEach { name ->
                val reads = Regex("""\b${Regex.escape(name)}\b""").findAll(implBody).count()
                if (reads < 2) {
                    violations.add(
                        "getTorrentResources 参数 `$name` 在 TorrentFeedRepositoryImpl 实现体中未被读取" +
                            "（仅出现 $reads 次）：接口契约形同虚设，调用方传参会被静默丢弃",
                    )
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反仓库契约参数实现闭环红线（见 AGENTS.md 领域规则）：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun production_sources_never_use_reflection_getIdentifier() {
        val scanRoots =
            listOf(
                File(projectRoot, "core"),
                File(projectRoot, "feature"),
                File(projectRoot, "app"),
            )
        val violations = mutableListOf<String>()
        scanRoots.forEach { dir ->
            if (dir.isDirectory) {
                dir
                    .walkTopDown()
                    .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val code = KotlinSourceScanner.sanitize(file.readText())
                        if (code.contains(".getIdentifier(") || code.contains("getIdentifier(")) {
                            val rel = file.relativeTo(projectRoot).path
                            violations.add("$rel 违规使用反射 getIdentifier 查找资源，必须通过依赖倒置或静态资源引用")
                        }
                    }
            }
        }
        assertTrue(
            violations.isEmpty(),
            "违反资源体系静态引用红线（严禁反射查找资源）：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun player_sources_interaction_strictly_centralized() {
        val subjectSrcDir = File(projectRoot, "feature/subject/src/main/kotlin")
        if (!subjectSrcDir.isDirectory) return

        val violations = mutableListOf<String>()
        subjectSrcDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                val rel = file.relativeTo(projectRoot).path
                if (text.contains("fun PlayerSourceSelector(") || text.contains("fun BiliPlayerSourceBar(")) {
                    violations.add("$rel 遗留了废弃的线路选择条，必须使用统一的 PlayerCurrentSourceCard 与 PlayerSourcesBottomSheet")
                }
                if (file.name == "PlayerEpisodeQueueDrawer.kt" &&
                    (text.contains("PlayerSourceTab") || text.contains("selectedSourceIndex"))
                ) {
                    violations.add("$rel 选集抽屉违规嵌入了线路选择器，抽屉必须专注选集，切源收拢至统一浮层")
                }
            }
        assertTrue(
            violations.isEmpty(),
            "违反播放线路统一收敛规范：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun player_media_state_reset_must_preserve_engine_side_preferences() {
        val controllerFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerController.kt",
            )
        if (!controllerFile.isFile) return

        val text = controllerFile.readText()
        // 单集循环（isLooping）由引擎的 repeatMode 持有、且是用户级偏好：换源/换集只允许重置媒体相关字段。
        // 一旦退回 `_state.value = PlaybackState()` 这种无参整体重置，界面会显示"已关闭"而引擎仍在循环，
        // 用户要点两次开关才关得掉（换集续播点属同一类"整体覆盖静默丢字段"陷阱）。
        assertTrue(
            !text.contains("_state.value = PlaybackState()"),
            "PlayerController 禁止用 PlaybackState() 无参整体重置媒体态，必须显式保留 isLooping 等引擎侧偏好",
        )
    }

    /**
     * 播放页长期偏好必须持久化，且只能有一个真源（设置仓库投影回读）。
     *
     * 弹幕开关曾只活在 PlayerViewModel 的内存状态里：`toggleDanmaku()` 就地翻转 `_uiState`，
     * 换集/重进播放页就悄悄回到「开」，用户关掉的弹幕自己长回来，且关闭期间仍在后台拉弹幕；
     * 自动连播是同一形态（关掉后换一集又自动连播）。反向的坑在 UI 侧——把偏好私藏在
     * `remember { mutableStateOf }` 里，仓库层完全不知情，重启即丢。两侧都要钉住：
     * 偏好写设置仓库并由快照投影回读，UI 局部状态只承载瞬态交互。
     */
    @Test
    fun player_view_preferences_must_be_persisted_not_held_in_session_state() {
        val violations = mutableListOf<String>()

        // ① ViewModel：偏好开关必须写设置仓库，严禁就地翻转 _uiState 形成第二事实源
        val viewModelPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt"
        val viewModelCode = KotlinSourceScanner.sanitize(File(projectRoot, viewModelPath).readText())
        playerPersistedPreferences.forEach { (field, setter) ->
            if (Regex("""_uiState\s*\.update\s*\{\s*it\.copy\(\s*$field\s*=""").containsMatchIn(viewModelCode)) {
                violations.add(
                    "$viewModelPath 就地翻转 $field：偏好开关必须写设置仓库（$setter）并由快照投影回读，" +
                        "否则重进播放页/换集即丢，且关闭期间的行为（如弹幕拉取）不会真的停",
                )
            }
            if (!viewModelCode.contains("settingsRepository.$setter(")) {
                violations.add("$viewModelPath 缺少 $setter 调用：$field 会退化成拨了不落库的装饰性开关")
            }
        }

        // ② UI：PlayerScreen 的局部状态只能是瞬态交互（悬浮控件/拖动/横竖屏…）。
        //    新增一处局部状态就必须显式回答「它该不该持久化」——偏好要经 ViewModel 下发
        val screenPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerScreen.kt"
        val screenCode = KotlinSourceScanner.sanitize(File(projectRoot, screenPath).readText())
        screenLocalStateDeclaration.findAll(screenCode).map { it.groupValues[1] }.forEach { name ->
            if (name !in playerScreenTransientState) {
                violations.add(
                    "$screenPath 新增局部状态 `$name`：用户偏好必须持久化（经 SettingsRepository 与快照投影），" +
                        "只有瞬态交互才允许留在 UI 局部状态",
                )
            }
        }

        // ③ 反向：会话级状态（播放速度 / 画面比例这类单次观看意图，单集循环这类引擎会话态）严禁落库。
        //    速度与画幅跟着「这一条视频、这一次观看」走，持久化等于让「上一部调到 2 倍速 / 拉伸成裁切」
        //    静默作用到下一部毫不相干的番上（B 站 / YouTube 的倍速同样不跨视频记忆）；循环由引擎
        //    repeatMode 持有，重启后自己记得"还在循环"是用户没要的意外。落库只可能从这两处发生：
        //    偏好模型的数据类，或设置仓库的字段/setter——两处都闭锁。
        val preferencesPath =
            "core/datastore/src/commonMain/kotlin/com/infinitezerone/minibgm/core/datastore/UserPreferences.kt"
        val repositoryPath =
            "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/SettingsRepository.kt"
        val preferencesCode = KotlinSourceScanner.sanitize(File(projectRoot, preferencesPath).readText())
        val repositoryCode = KotlinSourceScanner.sanitize(File(projectRoot, repositoryPath).readText())
        nonPersistedPlayerState.forEach { (name, reason) ->
            if (Regex("""\b(?:val|var)\s+$name\b""").containsMatchIn(preferencesCode)) {
                violations.add(
                    "$preferencesPath 出现偏好字段 `$name`：$reason，" +
                        "落库会把上一次的选择静默带到下一个毫不相干的视频 / 下一次播放",
                )
            }
            if (Regex("""\b$name\b""").containsMatchIn(repositoryCode)) {
                violations.add(
                    "$repositoryPath 出现 `$name`：$reason，不得进入设置仓库（字段/setter/投影都不行），" +
                        "它只允许活在播放页的局部状态 / 引擎会话里",
                )
            }
        }
        // 换个名字（如 preferredPlaybackSpeed / videoResizeMode）落库同样不允许：两处都按关键词兜一遍，
        // 免得「改个名就绕过」
        listOf(preferencesPath to preferencesCode, repositoryPath to repositoryCode).forEach { (path, code) ->
            Regex("""\b(?:val|var|fun)\s+\w*[Ss]peed\w*\b""").findAll(code).forEach { match ->
                violations.add("$path 出现速度类偏好声明 ${match.value.trim()}：播放速度属单次观看意图，严禁落库")
            }
            Regex("""\b(?:val|var|fun)\s+\w*(?:[Rr]esize|[Aa]spect)\w*\b""").findAll(code).forEach { match ->
                violations.add("$path 出现画面比例类偏好声明 ${match.value.trim()}：画面比例属单次观看意图，严禁落库")
            }
            Regex("""\b(?:val|var|fun)\s+\w*[Ll]oop\w*\b""").findAll(code).forEach { match ->
                violations.add("$path 出现循环播放类偏好声明 ${match.value.trim()}：单集循环属引擎会话态，严禁落库")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反「播放页偏好必须持久化」红线（偏好只活在会话内存里，重进页面即回退且行为不会真的停）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 播放器软字幕图层必须直连 [Player.Listener] 且随 [View.OnAttachStateChangeListener] 挂载/卸载。
     *
     * 事故复盘（2026-10-19）：
     * 旧实现将 [Player.Listener.onCues] 写入 Compose `mutableStateOf(cues)` 并经 `AndroidView.update`
     * 派发给 SubtitleView。当播放器在横竖屏（或 PiP）切换时，因外层使用了 `movableContentOf`，
     * 视图节点被从旧窗口脱离并移至新窗口，在此期间 AndroidViewHolder 的快照观察器被 clear，
     * 导致字幕更新观察永久断裂：字幕永久冻结在切屏时刻的某一帧；且用户在设置面板点击「关闭字幕」时，
     * 尽管 ExoPlayer 正确发出了空字幕组，快照观察器也因断开而无法将空字幕推入 SubtitleView，
     * 使得冻结字幕死死停留在画面中央。
     *
     * 红线要求：
     * 1. 严禁在 PlayerSubtitleOverlay 声明 `mutableStateOf` 中转高频字幕；
     * 2. 必须使用直连视图 [PlayerBoundSubtitleView]；
     * 3. 必须通过 View.OnAttachStateChangeListener 管理窗口附着生命周期，且关闭字幕时强制置空。
     */
    @Test
    fun player_subtitle_overlay_must_bind_directly_to_player_listener_without_compose_state() {
        val violations = mutableListOf<String>()
        val screenPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerScreen.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, screenPath).readText())

        val overlayBody =
            Regex("""fun\s+PlayerSubtitleOverlay\s*\([\s\S]*?\)\s*\{([\s\S]*?)\}""")
                .find(code)
                ?.groupValues
                ?.get(1)

        if (overlayBody?.contains("mutableStateOf") == true) {
            violations.add("$screenPath 的 PlayerSubtitleOverlay 函数体声明了 mutableStateOf：高频字幕事件绝不能经 Compose 快照中转，必须直连 Player.Listener")
        }

        if (!code.contains("PlayerBoundSubtitleView") || !code.contains("OnAttachStateChangeListener")) {
            violations.add("$screenPath 必须使用 PlayerBoundSubtitleView 并在 OnAttachStateChangeListener 中绑定与解绑 Player.Listener")
        }

        if (!code.contains("disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)")) {
            violations.add("$screenPath 的字幕组件必须检查 disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) 并在关闭时清空字幕")
        }

        assertTrue(
            violations.isEmpty(),
            "违反「播放器软字幕图层必须直连监听且安全承接横竖屏迁移」架构红线：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 播放器音视频与字幕轨道快照必须进行防重消歧，且通用语言代码（chi/zho/zh）不得武断硬编码为单一变体。
     *
     * 事故复盘（2026-10-19）：
     * 在压制规范的 MKV 片源（如 Crunchyroll WEB-DL 或合集）中，双语字幕按 ISO 639-2 规范均打上
     * `chi` 标签且未填独立 Track Title。旧实现一刀切将 `chi`/`zh` 映射为「中文 (简体)」，
     * 导致选轨弹窗中同时出现两个同名的「中文 (简体)」，繁体字幕被误标且用户只能盲选。
     * 必须经 [disambiguateTrackOptions] 进行消歧（SC_TC 双中文自动归一为简/繁，多同名轨追加编号）。
     */
    @Test
    fun player_tracks_snapshot_must_disambiguate_duplicate_tracks() {
        val sheetPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerTrackSelectionSheet.kt"
        val code = File(projectRoot, sheetPath).readText()

        assertTrue(
            code.contains("disambiguateTrackOptions(subtitleOptions)"),
            "$sheetPath 的 subtitleTracks 必须经 disambiguateTrackOptions 消歧，防止同语言多轨重名混淆",
        )
        assertTrue(
            code.contains("disambiguateTrackOptions(audioOptions)"),
            "$sheetPath 的 audioTracks 必须经 disambiguateTrackOptions 消歧，防止同语言多音轨重名混淆",
        )
        assertTrue(
            !Regex("""listOf\([^)]*"(?:chi|zh|zho)"[^)]*\)\s*->\s*"中文\s*\(简体\)"""").containsMatchIn(code),
            "$sheetPath 严禁将通用宏语言代号 chi/zh/zho 武断硬编码映射为「中文 (简体)」，必须中立标为「中文」并经消歧处理",
        )
    }

    @Test
    fun comment_sort_order_must_be_a_shared_persisted_preference() {
        val violations = mutableListOf<String>()
        val sharedConsumers =
            listOf(
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt",
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/EpisodeDetailViewModel.kt",
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/TopicDetailViewModel.kt",
            )
        sharedConsumers.forEach { path ->
            val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())

            // ① 写入必须落到两页共用的那条设备侧偏好
            if (!code.contains("settingsRepository.setCommentSortOrder(") &&
                !code.contains("settingsRepository?.setCommentSortOrder(")
            ) {
                violations.add("$path 未调用 setCommentSortOrder：吐槽排序必须写在两页共用的设备侧偏好上")
            }

            // ② 严禁再留一份会话状态——两处各持一份时，这里改成「最新」详情页仍是「热门」，重启又双双回默认
            if (Regex("""MutableStateFlow\s*\(\s*CommentSortOrder""").containsMatchIn(code) ||
                Regex("""\bcommentSortOrder\s*\.\s*value\s*=""").containsMatchIn(code)
            ) {
                violations.add("$path 仍持有评论排序的会话状态：唯一真源必须是与另一页共用的设备侧偏好")
            }

            // ③ 解析必须经唯一入口 fromKey——两处各自写默认值正是「同一语义两套降级」的温床
            if (!code.contains("CommentSortOrder.fromKey(")) {
                violations.add("$path 未经 CommentSortOrder.fromKey 解析持久化键：两页的默认值会各自漂移")
            }
        }

        // ④ 持久化键必须与枚举名解耦：直接落 name 会让一次改名把用户已保存的排序静默打回默认值
        val enumPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/CommentSortOrder.kt"
        if (!Regex("""val\s+sortKey\s*:\s*String""").containsMatchIn(
                KotlinSourceScanner.sanitize(File(projectRoot, enumPath).readText()),
            )
        ) {
            violations.add("$enumPath 缺少 sortKey：持久化键必须独立于枚举名（改名不得重置用户排序偏好）")
        }

        assertTrue(
            violations.isEmpty(),
            "违反「跨页共用偏好必须只有一份真源」红线（两页各持一份会话状态，改一处不动另一处，重启即回默认）：\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun danmaku_match_entry_must_be_gated_by_the_danmaku_toggle() {
        val violations = mutableListOf<String>()

        // ① 控制栏入口：有候选还不够，弹幕关闭时必须一并收起
        val overlayPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerControlsOverlay.kt"
        val overlayCode = KotlinSourceScanner.sanitize(File(projectRoot, overlayPath).readText())
        if (!Regex("""hasCandidates\s*=\s*isDanmakuEnabled\s*&&""").containsMatchIn(overlayCode)) {
            violations.add(
                "$overlayPath 的 DanmakuMatchEntryButton 未按 isDanmakuEnabled 收起：弹幕关掉后仍能进" +
                    "「分集纠正」，选中候选会给已关闭的功能写持久化覆盖并拉一次弹幕",
            )
        }

        // ② 播放设置面板里的「分集纠正」行：同样必须随开关收起
        val screenPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerScreen.kt"
        val screenCode = KotlinSourceScanner.sanitize(File(projectRoot, screenPath).readText())
        if (!Regex(
                """onOpenDanmakuPicker\s*=\s*openDanmakuPicker\s*\.takeIf\s*\{[^}]*isDanmakuEnabled""",
            ).containsMatchIn(screenCode)
        ) {
            violations.add(
                "$screenPath 的 onOpenDanmakuPicker 未按 isDanmakuEnabled 收起：设置面板里的" +
                    "「分集纠正」行会在弹幕关闭时照样可点",
            )
        }

        // ③ ViewModel 侧竞态兜底：关掉开关的那一帧里旧入口仍可能被点到
        val viewModelPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt"
        val viewModelCode = KotlinSourceScanner.sanitize(File(projectRoot, viewModelPath).readText())
        val start = viewModelCode.indexOf("fun selectDanmakuCandidate(")
        if (start < 0) {
            violations.add("$viewModelPath 找不到 selectDanmakuCandidate：闸口必须留在该入口内")
        } else {
            val rest = viewModelCode.substring(start)
            val end = rest.indexOf("\n    fun ")
            val body = if (end > 0) rest.substring(0, end) else rest
            if (!body.contains("isDanmakuEnabled")) {
                violations.add(
                    "$viewModelPath 的 selectDanmakuCandidate 未按 isDanmakuEnabled 闸口：" +
                        "关闭弹幕期间点到旧入口仍会 saveDanmakuOverride + 拉一次弹幕",
                )
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反「弹幕关闭即零请求」红线（入口不随开关收起，关闭期间仍写覆盖/发请求）：\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun danmaku_view_must_use_dynamic_track_allocation_and_not_hash_modulo() {
        val violations = mutableListOf<String>()
        val danmakuViewPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/DanmakuView.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, danmakuViewPath).readText())

        // 1. 禁止使用粗暴的 text.hashCode() % maxTracks
        if (code.contains(".hashCode()") || code.contains("hashCode() %")) {
            violations.add(
                "$danmakuViewPath 包含 hashCode() 弹道取模：粗暴的哈希取模会导致相同文字（如「草」「2333」）" +
                    "或哈希冲突的弹幕物理重叠在同一轨道，且无视文字长度与移动速度差异引发追尾",
            )
        }

        // 2. 必须具备动态排版预计算 computeDanmakuLayout
        if (!code.contains("fun computeDanmakuLayout(") || !code.contains("computeDanmakuLayout(")) {
            violations.add("$danmakuViewPath 缺少 computeDanmakuLayout：弹道分配必须前置预计算，严禁每帧盲算")
        }

        // 3. 绘制循环必须消费 PositionedDanmaku，且严禁在绘制循环里反复 measureText
        if (!code.contains("PositionedDanmaku")) {
            violations.add("$danmakuViewPath 缺少 PositionedDanmaku 预计算实例模型")
        }

        val canvasIdx = code.indexOf("Canvas(modifier =")
        if (canvasIdx > 0) {
            val canvasBody = code.substring(canvasIdx)
            if (canvasBody.contains("measureText(")) {
                violations.add("$danmakuViewPath 在 Canvas 绘制块中调用了 measureText：测量必须在预计算中完成，严禁在 60/120 FPS 绘制帧触发文字测量")
            }
        }

        // 4. 必须具备字幕安全避让逻辑
        if (!code.contains("SUBTITLE_AREA_RATIO") && !code.contains("subtitleAreaRatio")) {
            violations.add("$danmakuViewPath 缺少字幕避让保护区域计算：必须为屏幕底部预留正文字幕安全区")
        }

        assertTrue(
            violations.isEmpty(),
            "违反「弹幕动态排版与防重叠」架构红线：\n" + violations.joinToString("\n"),
        )
    }

    /** 播放页必须落库的偏好字段 → 对应的 SettingsRepository 写入方法 */
    private val playerPersistedPreferences: Map<String, String> =
        mapOf(
            "isDanmakuEnabled" to "setDanmakuEnabled",
            "autoNextEnabled" to "setAutoNextEnabled",
            // 关闭字幕：稳定立场，重启后仍要「字幕关」（引擎参数只表达本次播放）
            "subtitlesDisabled" to "setSubtitlesDisabled",
            "danmakuAlpha" to "setDanmakuAlpha",
            "danmakuDurationMs" to "setDanmakuDurationMs",
        )

    /** PlayerScreen 允许保留的 UI 局部状态（全部是瞬态交互，不是用户偏好） */
    private val playerScreenTransientState: Set<String> =
        setOf(
            "areControlsVisible",
            "isLandscape",
            // 播放速度与画面比例：跟着「这一条视频、这一次观看」走，刻意不持久化（见测试内的反向闭锁）
            "playbackSpeed",
            "resizeMode",
            "isScreenLocked",
            "isScrubbing",
            "scrubProgress",
            "lastResumedEpisodeId",
            "resumeOverlayPosition",
            "playerReady",
            "selectedPortraitTab",
            "rawStageBounds",
            "qualityAppliedKey",
        )

    /** PlayerScreen 里的 `var x by remember { mutableStateOf(...) }` 声明 */
    private val screenLocalStateDeclaration =
        Regex(
            """^\s*var\s+(\w+)\s+by\s+remember(?:Saveable)?\s*\{\s*mutable\w*StateOf""",
            RegexOption.MULTILINE,
        )

    /**
     * 刻意不落库的播放页会话级状态。
     *
     * 两类：① 单次观看意图（播放速度 / 画面比例——跟着「这一条视频、这一次观看」走）；
     * ② 引擎会话态（单集循环 `isLooping`——由引擎 `repeatMode` 持有，进程内跨换集/换源保留，
     * 但刻意不跨重启记忆；B 站/YouTube 的循环同样不跨视频）。
     */
    private val nonPersistedPlayerState: Map<String, String> =
        mapOf(
            "playbackSpeed" to "播放速度属单次观看意图（B 站 / YouTube 的倍速同样不跨视频记忆）",
            "resizeMode" to "画面比例属单次观看意图（跟着「这一条视频、这一次观看」走）",
            "isLooping" to "单集循环属引擎会话态（由 repeatMode 持有，刻意不跨重启记忆）",
        )

    @Test
    fun exoplayer_engine_media_source_factory_must_wrap_default_data_source() {
        val engineFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/ExoPlayerEngine.kt",
            )
        if (!engineFile.isFile) return

        val text = engineFile.readText()
        // Media3 的 DefaultMediaSourceFactory 若裸传 HttpDataSource.Factory，
        // 播放本地 file://（如离线下载完成的视频）时会尝试强转 HttpURLConnection 导致 ClassCastException 崩溃。
        // 必须通过 DefaultDataSource.Factory(context, httpDataSourceFactory) 包装以支持离线与本地资源回放。
        assertTrue(
            text.contains("DefaultDataSource.Factory(context, httpDataSourceFactory)"),
            "ExoPlayerEngine 的 DefaultMediaSourceFactory 必须使用 DefaultDataSource.Factory 包装以支持离线 file:// 播放",
        )
    }

    @Test
    fun player_view_model_must_filter_out_torrent_rules_from_online_sources() {
        val playerViewModelFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt",
            )
        if (!playerViewModelFile.isFile) return

        val text = playerViewModelFile.readText()
        // 播放器线路必须排除 PlaybackRuleKind.TORRENT，P2P 种子由离线下载和详情页专门面板承接，不得作为在线直链嗅探源。
        assertTrue(
            text.contains("PlaybackRuleKind.TORRENT"),
            "PlayerViewModel 必须处理并过滤 PlaybackRuleKind.TORRENT 种子规则",
        )
    }

    /**
     * 播放器观看记录与断点续播红线：
     * 1. 观看历史与断点续播唯一真源是 [PlaybackHistoryRepository]，按分集 [episodeId] 绑定；
     * 2. [PlayerViewModel] 严禁再引用 SettingsRepository.playbackPositions / savePlaybackPosition / clearPlaybackPosition，
     *    彻底剥离直链 streamUrl 依赖，杜绝双轨数据与跨集串流。
     */
    @Test
    fun player_view_model_playback_resume_must_bind_strictly_to_playback_history_repo() {
        val playerViewModelFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt",
            )
        if (!playerViewModelFile.isFile) return
        val text = KotlinSourceScanner.sanitize(playerViewModelFile.readText())

        val violations = mutableListOf<String>()
        if (text.contains("settingsRepository.playbackPositions") ||
            text.contains("settingsRepository.savePlaybackPosition") ||
            text.contains("settingsRepository.clearPlaybackPosition")
        ) {
            violations.add("PlayerViewModel 仍存在对 SettingsRepository.playbackPositions 的读写调用，必须统一收拢至 PlaybackHistoryRepository")
        }
        if (!text.contains("playbackHistoryRepository?.saveHistory") ||
            !text.contains("playbackHistoryRepository?.deleteHistory") ||
            !text.contains("playbackHistoryRepository?.getHistoryByEpisode")
        ) {
            violations.add("PlayerViewModel 缺少对 PlaybackHistoryRepository 的 save/delete/getHistoryByEpisode 完整调用闭环")
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放器观看记录与断点续播架构红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 播放源管理与观看记录解耦红线：
     * 1. 观看历史由独立 [HistoryViewModel] 和 [HistoryScreen] 承载，归口个人中心 [UserScreen] 顶栏直达；
     * 2. [PlaybackRulesViewModel] 还原为纯粹的播放源规则与订阅管理，严禁持有 PlaybackHistoryRepository 或混入历史管理。
     */
    @Test
    fun playback_history_must_be_decoupled_from_playback_rules_view_model() {
        val rulesViewModelFile =
            File(
                projectRoot,
                "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/PlaybackRulesViewModel.kt",
            )
        if (!rulesViewModelFile.isFile) return
        val text = KotlinSourceScanner.sanitize(rulesViewModelFile.readText())

        val violations = mutableListOf<String>()
        if (text.contains("PlaybackHistoryRepository") || text.contains("playbackHistory")) {
            violations.add("PlaybackRulesViewModel 仍混入观看记录逻辑（持有 PlaybackHistoryRepository 或暴露 playbackHistory），必须彻底解耦")
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放源规则与观看记录解耦架构红线：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun screenshot_test_modules_must_use_shared_unit_test_convention() {
        val includeRe = Regex("""^include\(":(.+)"\)$""")
        val violations = mutableListOf<String>()

        File(projectRoot, "settings.gradle.kts").readLines().forEach { line ->
            val module =
                includeRe
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.replace(':', '/') ?: return@forEach
            val script = File(projectRoot, "$module/build.gradle.kts")
            if (!script.isFile) return@forEach
            val text = script.readText()
            if (!text.contains("roborazzi")) return@forEach
            // 截图测试模块的"测试 JVM / roborazzi.record / -Pminibgm.skipScreenshots"只能来自
            // build-logic 的 configureAndroidUnitTests()。历史上 core/designsystem、feature/subject、
            // feature/user 各抄了一份相同的 tasks.withType<Test> 块——副本漂移后会出现
            // "跳过开关只对部分模块生效"这类无法察觉的静默不一致，故机械拦截。
            if (!text.contains("configureAndroidUnitTests()")) {
                violations += "$module/build.gradle.kts：含截图测试但未调用 configureAndroidUnitTests()"
            }
            if (text.contains("javaLauncher.set(")) {
                violations += "$module/build.gradle.kts：禁止手写 Test javaLauncher，改用 configureAndroidUnitTests()"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反单测配置单一真源（build-logic/convention/.../AndroidUnitTest.kt）：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun core_data_and_feature_subject_sources_must_not_hardcode_proprietary_torrent_domains() {
        val violations = mutableListOf<String>()
        val checkedDirs = listOf("core/data", "core/network", "feature/subject")
        val forbiddenDomain = "mikanani.me"

        checkedDirs.forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    if (text.contains(forbiddenDomain)) {
                        violations.add(
                            "${file.relativeTo(projectRoot).path} 违规硬编码专用种子站点域名 $forbiddenDomain，必须使用通用的 TorrentFeedRepository 与规则驱动体系",
                        )
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "违反 P2P 种子协议中立性红线（核心数据层与详情层严禁硬编码特定第三方站点域名）：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun production_sources_must_not_inject_hardcoded_public_trackers() {
        val violations = mutableListOf<String>()
        val checkedDirs = listOf("core", "feature", "app", "sync")
        // 原始文本扫描：违规形态必然落在字符串字面量里（sanitize() 会抹掉字符串，此处不可用）。
        val forbiddenTokens =
            listOf(
                "&tr=" to "磁链 Tracker 查询参数",
                "/announce" to "Tracker announce 端点",
            )

        checkedDirs.forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    forbiddenTokens.forEach { (token, label) ->
                        if (text.contains(token)) {
                            violations.add(
                                "${file.relativeTo(projectRoot).path} 违规硬编码公共 $label（$token）",
                            )
                        }
                    }
                }
        }

        assertTrue(
            violations.isEmpty(),
            "违反 P2P 零第三方广播红线（严禁硬编码公共 Tracker 注入请求，寻道只走磁链自带声明与 DHT）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 隐私与安全隔离红线：
     * 开源仓库中严禁硬编码任何开发者个人私有域名、网关端点（如私有反代、私人 DDNS 域名）；
     * OAuth 令牌兑换与弹幕网关等端点必须由 CI（GitHub Secrets）或本地 local.properties 注入，
     * 避免个人凭据与域名随开源代码泄漏。
     */
    @Test
    fun production_sources_must_not_hardcode_private_proxy_domains() {
        val violations = mutableListOf<String>()
        val checkedDirs = listOf("core", "feature", "app", "sync")
        val forbiddenDomains = listOf("zero" + "nex", "dpdns" + ".org")

        checkedDirs.forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") && it.name != "ArchitectureRulesTest.kt" }
                .forEach { file ->
                    val text = file.readText()
                    forbiddenDomains.forEach { domain ->
                        if (text.contains(domain, ignoreCase = true)) {
                            violations.add(
                                "${file.relativeTo(projectRoot).path} 违规硬编码私有代理域名 $domain，必须由 CI / local.properties 注入",
                            )
                        }
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "违反私有网关与域名隔离红线（严禁在源码中硬编码私有端点）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 开发者面板「开关必须真实生效」红线（见 AGENTS.md 领域规则 & 剧本 8）：
     *
     * 面板里能拨的每一个开关都必须至少有一个**生产消费方**——只能写设置、只能读设置、
     * 但没人拿它做事，就是"装饰性开关"：用户拨完以为配置生效，实际行为毫无变化，
     * 且全程没有任何提示。历史上同一次审查里连中两例：
     *   - `customMirrorPrefixOverride`（自定义加速镜像前缀）：更新包与 BT 原生扩展的下载
     *     从未读过它，填了前缀照样走默认公共镜像；
     *   - `verboseLoggingEnabled`（详细网络与引擎日志）：日志门限只在启动期按 BuildConfig
     *     决定一次，Release 包里打开开关依旧只有 Warn 以上日志，真机排查形同盲人摸象。
     *
     * 机械判据：每个开关名在「消费方白名单」之外的源码里至少出现一次，
     * 且白名单文件本身必须真的把它接进行为（读设置 → 传参 / 调门限）。
     */
    @Test
    fun developer_panel_settings_must_have_a_real_consumer() {
        val violations = mutableListOf<String>()

        fun raw(relativePath: String): String {
            val file = File(projectRoot, relativePath)
            assertTrue(file.isFile, "$relativePath 未找到")
            return file.readText()
        }

        fun code(relativePath: String): String = KotlinSourceScanner.sanitize(raw(relativePath))

        val downloaderPath =
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidAssetDownloader.kt"
        val downloaderDiPath =
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/di/DataModule.android.kt"
        val appPath = "app/src/main/kotlin/com/infinitezerone/minibgm/MiniBgmApp.kt"

        // 1. 自定义加速镜像前缀：下载器必须暴露读取器，且 DI 必须把它接到 DeveloperSettings 上
        if (!code(downloaderPath).contains("customMirrorPrefixProvider")) {
            violations.add(
                "$downloaderPath 未暴露 customMirrorPrefixProvider：开发者面板配的前缀进不了下载器，只会静默走默认镜像",
            )
        }
        if (!code(downloaderPath).contains("customMirrorPrefixProvider()")) {
            violations.add("$downloaderPath 声明了 customMirrorPrefixProvider 却从未在解析候选地址前读取（死参数）")
        }
        if (!code(downloaderDiPath).contains("customMirrorPrefixProvider") ||
            !code(downloaderDiPath).contains("developerSettings") ||
            !code(downloaderDiPath).contains("customMirrorPrefixOverride")
        ) {
            violations.add(
                "$downloaderDiPath 未把 AssetDownloader 的镜像前缀读取器接到 SettingsRepository.developerSettings.customMirrorPrefixOverride",
            )
        }

        // 2. 详细调试日志：门限必须由开关驱动，而不是只在启动期按 BuildConfig 定死一次
        val appCode = code(appPath)
        if (!appCode.contains("verboseLoggingEnabled")) {
            violations.add("$appPath 未消费 verboseLoggingEnabled：详细调试日志开关在真机上不改变任何日志门限")
        }
        if (!appCode.contains("setMinSeverity")) {
            violations.add("$appPath 未按开关调整 Logger 门限（setMinSeverity）")
        }
        if (!appCode.contains("distinctUntilChanged")) {
            violations.add("$appPath 收集 developerSettings 时缺少 distinctUntilChanged：每次都重设全局日志门限")
        }

        assertTrue(
            violations.isEmpty(),
            "违反开发者面板开关生效红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 「已落盘资源」动作诚实性红线（见 AGENTS.md 领域规则 & 剧本 8）：
     *
     * 资源已完整落盘时，「离线下载」必须被禁用，且「已下载」判据必须排除物理文件缺失的任务。
     * 否则两条路径都会骗人：
     *   - 放行点击只会落进 `startDownload` 的「既有完成任务直接返回 taskId」分支，
     *     随后弹出「已加入下载队列，可在通知栏查看进度」——用户盯着通知栏等一个永不出现的进度；
     *   - 若只认 `status == COMPLETED` 而忽略文件已被清理，徽标会宣告「已下载」、
     *     播放按钮指向不存在的 `file://`，同时下载入口被禁，用户卡在既播不了也重下不了的死路。
     */
    @Test
    fun downloaded_resource_actions_must_stay_honest() {
        val path =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())
        val violations = mutableListOf<String>()

        val inAppDownloadGate =
            Regex(
                """R\.string\.feature_subject_source_torrent_in_app_download[\s\S]{0,1200}?enabled\s*=\s*!isAnyBusy\s*&&\s*!isDownloaded""",
            )
        if (!inAppDownloadGate.containsMatchIn(code)) {
            violations.add(
                "$path 的「离线下载」未在 isDownloaded 时禁用：已下载资源点击后只会弹一条「已加入下载队列」的假提示",
            )
        }

        val completedGate = Regex("""fun completedTaskFor\([\s\S]{0,1200}?!task\.isFileMissing""")
        if (!completedGate.containsMatchIn(code)) {
            violations.add(
                "$path 的 completedTaskFor 未排除 isFileMissing：文件已被清理的任务仍会被标成「已下载」，点下去是一条既播不了也重下不了的死路",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反「已落盘资源」动作诚实性红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 播放源底栏必须统一使用设计系统规范卡片（BgmSourceActionCard）：
     * 在线 Tab 与 BT Tab 的空态管理引导必须同构，严禁在 BT 空态中随手借调 TorrentActionButton，
     * 且管理跳转必须严格通过 onDismissAndRun / runAfterDismiss 调度底栏收起动画。
     * 多按钮操作行必须显式声明 contentPadding，彻底杜绝 Material 3 默认 48dp 内边距挤压截断中文。
     */
    @Test
    fun subject_sources_sheet_must_use_unified_source_action_card_for_management() {
        val path =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())
        val violations = mutableListOf<String>()

        val torrentEmptyBlock =
            Regex("""!hasTorrentRules[\s\S]{0,1000}?if\s*\(\s*onManageRules\s*!=\s*null\s*\)[\s\S]{0,600}?BgmSourceActionCard""")
        if (!torrentEmptyBlock.containsMatchIn(code)) {
            violations.add(
                "$path 的 BT 空态配置引导未采用 BgmSourceActionCard 统一卡片，破坏了与在线播放 Tab 的设计系统同构性",
            )
        }

        val torrentAdhocButton = Regex("""!hasTorrentRules[\s\S]{0,1000}?TorrentActionButton""")
        if (torrentAdhocButton.containsMatchIn(code)) {
            violations.add(
                "$path 的 BT 空态配置引导错误借用了 TorrentActionButton，违反设计系统收敛红线",
            )
        }

        val torrentDismissCheck = Regex("""!hasTorrentRules[\s\S]{0,1000}?onDismissAndRun\s*\{\s*onManageRules\(\)\s*\}""")
        if (!torrentDismissCheck.containsMatchIn(code)) {
            violations.add(
                "$path 的 BT 空态管理跳转未包裹 onDismissAndRun 抽屉收起动画",
            )
        }

        val buttonPaddingCheck = Regex("""onToggleWatched[\s\S]{0,400}?contentPadding\s*=\s*PaddingValues\(""")
        if (!buttonPaddingCheck.containsMatchIn(code)) {
            violations.add(
                "$path 的打卡动作按钮未显式声明 contentPadding，在窄屏多按钮排版下将触发 Material 3 默认 48dp 内边距导致的中文文字截断",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反「播放源底栏组件与交互同构」红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 详情页分集点击必须直接路由至统一抽屉（SubjectSourcesBottomSheet）：
     * 严禁退化为「先弹分集微操作、再关弹窗开播放源」的多层嵌套与抽屉跳抽屉反模式。
     */
    @Test
    fun subject_detail_episode_click_must_route_to_unified_sources_sheet() {
        val path =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectDetailScreen.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())

        val unifiedRouteCheck =
            Regex(
                """onEpisodeClickForQuickAction\s*=\s*\{\s*episode\s*->[\s\S]{0,150}?selectedEpisodeForSources\s*=\s*episode[\s\S]{0,100}?showSourcesBottomSheet\s*=\s*true""",
            )
        assertTrue(
            unifiedRouteCheck.containsMatchIn(code),
            "$path 的 onEpisodeClickForQuickAction 必须直接打开统一底栏（selectedEpisodeForSources + showSourcesBottomSheet），严禁开孤立抽屉套娃！",
        )
    }

    /**
     * 手工 `viewModel { XxxViewModel(...) }` 块必须显式注入**全部**「默认值为 null」的构造器参数。
     *
     * 这类参数是"可选依赖"，危险在于漏传时三层防线同时失效：
     * 1. 编译期不报错（有默认值）；2. Koin 解析不报错（默认值兜住，与 platformDataModule 漏绑触发
     * NoDefinitionFoundException 的形态不同）；3. 单测自己传 fake，同样不报错。结果只在真机上表现为
     * 整条链路静默退化成「没有这个功能」——SubjectModule 漏传 `playbackHistoryRepository` 时，
     * 播放历史不落库、换集/换源的「历史兜底续播」全废，且全程无提示。
     *
     * 非 null 的默认值（`autoStart: Boolean = true`、`clock: () -> Long = { ... }`）是有意的可选项，
     * 不在本红线约束范围内——这里只钉真正会静默退化的「可选依赖」类别。
     */
    @Test
    fun manual_view_model_blocks_must_inject_every_optional_dependency() {
        val violations = mutableListOf<String>()
        listOf("feature", "app").forEach { rootName ->
            val dir = File(projectRoot, rootName)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val scanner = KotlinSourceScanner.fromFile(file)
                    val text = scanner.sanitizedContent
                    val relPath = file.relativeTo(projectRoot).path
                    manualViewModelBlock.findAll(text).forEach { block ->
                        val className = block.groupValues[1]
                        val argsOpen = block.range.last
                        val argsClose = text.indexOfCloseDelimiter(argsOpen) ?: return@forEach
                        val passedNames =
                            splitTopLevelArguments(text.substring(argsOpen + 1, argsClose))
                                .mapNotNull { topLevelNamedArgumentName(it) }
                                .toSet()
                        val classFile =
                            projectRoot
                                .walkTopDown()
                                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                                .firstOrNull { it.isFile && it.name == "$className.kt" }
                        if (classFile == null) {
                            violations.add("$relPath 手工构造的 $className 找不到源码文件，无法校验可选依赖是否传全")
                            return@forEach
                        }
                        val nullDefaults =
                            nullDefaultedConstructorParams(
                                KotlinSourceScanner.fromFile(classFile).sanitizedContent,
                                className,
                            )
                        val missing = nullDefaults - passedNames
                        if (missing.isNotEmpty()) {
                            violations.add(
                                "$relPath:${scanner.getLineNumber(block.range.first)} 手工构造 $className 漏传可选依赖 " +
                                    missing.sorted() + "（默认值 null，漏传即静默退化为「功能没接上」）",
                            )
                        }
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "违反「手工 DI 块必须显式注入全部可选依赖」红线（漏传不会编译报错也不会被 Koin 发现，只在真机静默退化）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 播放源订阅完整性红线（见 AGENTS.md 领域规则 & 剧本 15）：
     * 1. `SettingsRepository` 刷新/导入订阅源时严禁按 `isAlive` 过滤丢弃规则：
     *    连通性探测纯用于 UI 延迟排序与诊断展示，瞬时丢包或服务端反爬返回 403 绝不代表规则废弃，
     *    过滤 `it.isAlive` 会静默丢弃用户订阅的绝大多数合法可用源（历史上曾导致 5 个 MacCMS 源丢掉 4 个）；
     * 2. `CommunitySubscriptionService` 探测存活判据必须支持 HTTP 403（MacCMS 根路径普遍返回 403 但 API 可用），
     *    严禁仅以 200..399 判死。
     */
    @Test
    fun subscription_sources_must_never_be_discarded_by_probe_status() {
        val settingsRepoFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/SettingsRepository.kt",
            )
        assertTrue(settingsRepoFile.isFile, "SettingsRepository.kt 未找到")
        val repoContent = KotlinSourceScanner.sanitize(settingsRepoFile.readText())

        val violations = mutableListOf<String>()
        if (repoContent.contains(".filter { it.isAlive }") || repoContent.contains(".filter { rule -> rule.isAlive }")) {
            violations.add(
                "SettingsRepository.kt 在导入/刷新订阅时按 isAlive 过滤规则：探测失败会永久丢弃合法规则源，违反订阅完整性红线",
            )
        }

        val serviceFile =
            File(
                projectRoot,
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/CommunitySubscriptionService.kt",
            )
        assertTrue(serviceFile.isFile, "CommunitySubscriptionService.kt 未找到")
        val serviceContent = KotlinSourceScanner.sanitize(serviceFile.readText())

        if (serviceContent.contains("status.value in 200..399")) {
            violations.add(
                "CommunitySubscriptionService.kt 仅将 200..399 视为存活：MacCMS 等源根路径返回 403 会被误判为死亡",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放源订阅完整性红线：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun player_gesture_hud_must_not_jitter_and_use_tnum_and_avoid_dual_hud_conflict() {
        val gestureFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerGestureDetector.kt",
            )
        assertTrue(gestureFile.isFile, "PlayerGestureDetector.kt 未找到")
        val rawContent = gestureFile.readText()
        val content = KotlinSourceScanner.sanitize(rawContent)

        val violations = mutableListOf<String>()

        // 1. 顶部胶囊必须带 tnum 与固定槽位宽度
        if (!rawContent.contains("""fontFeatureSettings = "tnum"""")) {
            violations.add("PlayerGestureDetector.kt 缺少 fontFeatureSettings = \"tnum\" 配置，数字宽度跳变会导致 HUD 剧烈抖动")
        }
        if (!content.contains("Modifier.width(36.dp)")) {
            violations.add("TopCapsuleIndicator 缺少固定文本宽度 Modifier.width(36.dp)，百分比字符数变化会导致胶囊忽长忽短")
        }

        // 2. SeekFeedbackCard 必须有最小宽度约束
        if (!content.contains("widthIn(min = 160.dp)")) {
            violations.add("SeekFeedbackCard 缺少 widthIn(min = 160.dp) 最小宽度约束，拖动快进快退时卡片容易晃动")
        }

        // 3. 双击快进 handleQuickSeek 严禁向中央派发 GestureHudState.Seek，杜绝侧边 YouTube 药丸与中央卡片双重浮窗打架
        val handleQuickSeekBody =
            Regex("""fun handleQuickSeek\([\s\S]*?\{([\s\S]*?)\n\s*\}""")
                .find(content)
                ?.groupValues
                ?.get(1)
        if (handleQuickSeekBody != null && handleQuickSeekBody.contains("GestureHudState.Seek")) {
            violations.add("handleQuickSeek 违规调用了 GestureHudState.Seek，导致双击快进时侧边药丸与中央卡片两层浮窗重叠打架")
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放器手势 HUD 稳定性与防冲突红线：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun image_loader_routing_must_fail_open_for_unknown_third_party_hosts() {
        val moduleFile =
            File(
                projectRoot,
                "app/src/main/kotlin/com/infinitezerone/minibgm/di/ImageLoaderModule.kt",
            )
        assertTrue(moduleFile.isFile, "ImageLoaderModule.kt 未找到")
        val content = KotlinSourceScanner.sanitize(moduleFile.readText())

        val violations = mutableListOf<String>()

        // 未知域名必须 fail-open：严禁在 execute 内同步阻塞调用 isEchSupported
        if (content.contains("EchCapabilityDetector.isEchSupported(host")) {
            violations.add("ImageRoutingEngine 在主请求路径违规同步调用了 isEchSupported，阻断了首图立即加载")
        }

        // 必须通过 probeAsyncIfUnknown 异步探测
        if (!content.contains("EchCapabilityDetector.probeAsyncIfUnknown(host)")) {
            violations.add("ImageRoutingEngine 缺少 probeAsyncIfUnknown(host) 异步探测，未知图床无法自愈升级走 ECH")
        }

        assertTrue(
            violations.isEmpty(),
            "违反第三方图床 Fail-Open 路由红线：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun torrent_resource_card_must_not_expose_technical_dual_source_badges_and_use_lean_actions() {
        val sheetFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt",
            )
        assertTrue(sheetFile.isFile, "SubjectSourcesBottomSheet.kt 未找到")
        val rawContent = sheetFile.readText()
        val content = KotlinSourceScanner.sanitize(rawContent)

        val violations = mutableListOf<String>()

        // 1. 严禁展示"双源可用"这类给用户制造认知负担的抓取技术细节徽章
        if (rawContent.contains("feature_subject_source_torrent_channel_dual") ||
            content.contains("resource.isDualSource")
        ) {
            violations.add("SubjectSourcesBottomSheet 违规暴露了技术细节徽标「双源可用」，增加了用户的认知负担")
        }

        // 2. 种子卡片必须具备 TorrentFeedDiskCache 磁盘缓存契约
        val repoFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentFeedRepository.kt",
            )
        assertTrue(repoFile.isFile, "TorrentFeedRepository.kt 未找到")
        val repoContent = repoFile.readText()
        if (!repoContent.contains("TorrentFeedDiskCache")) {
            violations.add("TorrentFeedRepository 缺少 TorrentFeedDiskCache 磁盘持久化缓存支持，无法保证离线秒开")
        }

        assertTrue(
            violations.isEmpty(),
            "违反种子资源卡片去噪与持久化缓存红线：\n" + violations.joinToString("\n"),
        )
    }

    /** 手工构造 ViewModel 的 Koin 块；`{ params -> ... }`、`{ (arg: T) -> ... }` 与单行写法都要覆盖 */
    private val manualViewModelBlock = Regex("""\bviewModel\s*\{[\s\S]{0,600}?\b([A-Z]\w*ViewModel)\s*\(""")

    /** 返回 [open] 处 `(` 对应的 `)` 下标（只统计圆括号嵌套；找不到返回 null） */
    private fun String.indexOfCloseDelimiter(open: Int): Int? {
        var depth = 0
        for (i in open until length) {
            when (this[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    /** 按顶层逗号切分实参/形参列表（嵌套括号内的逗号不算分隔符） */
    private fun splitTopLevelArguments(list: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in list.indices) {
            when (list[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' ->
                    if (depth == 0) {
                        parts.add(list.substring(start, i))
                        start = i + 1
                    }
            }
        }
        parts.add(list.substring(start))
        return parts.filter { it.isNotBlank() }
    }

    /** 顶层命名实参的参数名（`foo = get()` → foo）；位置实参返回 null */
    private fun topLevelNamedArgumentName(segment: String): String? = Regex("""^\s*(\w+)\s*=(?!=)""").find(segment)?.groupValues?.get(1)

    /** 取 `class X(` 主构造器里所有「默认值为 null」的可选依赖参数名 */
    private fun nullDefaultedConstructorParams(
        code: String,
        className: String,
    ): Set<String> {
        val classIndex = code.indexOf("class $className(")
        if (classIndex < 0) return emptySet()
        val open = code.indexOf('(', classIndex)
        val close = code.indexOfCloseDelimiter(open) ?: return emptySet()
        return splitTopLevelArguments(code.substring(open + 1, close))
            .filter { Regex("""=\s*null\s*$""").containsMatchIn(it.trim()) }
            .mapNotNull { Regex("""(?:^|\s)(?:val|var)\s+(\w+)\s*:""").find(it)?.groupValues?.get(1) }
            .toSet()
    }

    /**
     * 仓库/抓取层必须把 CPU/IO 工作切出调用方线程。
     *
     * Kotlin 的 `suspend` 不切换线程：`viewModelScope.launch` 的续体仍在 Main，
     * 所以 JSON 解码与正文解码必须在 `withContext(dispatchers.default)` 块内完成。
     * 这条红线钉住"机制存在且包住了对应调用"，防止再出现"解析在主线程"。
     */
    @Test
    fun repository_cpu_work_must_leave_the_caller_thread() {
        val violations = mutableListOf<String>()

        val danmakuRepo =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/DanmakuRepository.kt",
            )
        if (!danmakuRepo.isFile) {
            violations += "DanmakuRepository.kt 不存在：${danmakuRepo.path}"
        } else {
            val code = KotlinSourceScanner.sanitize(danmakuRepo.readText())
            if (!mentionsInsideBlock(code, "withContext(dispatchers.default)", "parseDandanJsonResult")) {
                violations +=
                    "DanmakuRepository.kt: 弹幕 JSON 解码/排序必须在 withContext(dispatchers.default) 内" +
                    "（suspend 不等于离开主线程）"
            }
        }

        val pageFetch =
            File(
                projectRoot,
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/PageFetchService.kt",
            )
        if (!pageFetch.isFile) {
            violations += "PageFetchService.kt 不存在：${pageFetch.path}"
        } else {
            val code = KotlinSourceScanner.sanitize(pageFetch.readText())
            if (!mentionsInsideBlock(code, "withContext(dispatchers.default)", "decodeToString")) {
                violations += "PageFetchService.kt: 正文读取/解码必须在 withContext(dispatchers.default) 内"
            }
        }

        if (violations.isNotEmpty()) {
            fail("调用方线程（可能是 Main）不得承担解析/解码：\n" + violations.joinToString("\n"))
        }
    }

    /**
     * 播放器横屏右侧抽屉（PlayerRightDrawerContainer）必须将关闭动效响应绑定至快照流与下树清理，
     * 且 BackHandler 必须按目标显隐状态条件启闭（`enabled = visibleState.targetState`）：
     *
     * 1. 动效结束自动响应：严禁使用 `SideEffect` 判定 `visibleState.isIdle && !visibleState.currentState`。
     *    `SideEffect` 仅在宿主重组时执行，抽屉退出动效结束时内部状态变更不会触发宿主重组，导致 `onRespond()` 永不执行，
     *    `OverlayHostState` 挂起协程永久泄漏，隐蔽层级残留在 UI 树；必须通过 `snapshotFlow` 监听动效完成。
     * 2. 下树兜底响应：必须通过 `DisposableEffect(Unit)` 在 `onDispose` 时触发兜底响应，防止横竖屏切换/页面退出/PiP 强制下树时挂起协程挂死。
     * 3. 返回键条件使能：严禁注册无条件的裸 `BackHandler { onDismissRequest() }`；必须声明 `BackHandler(enabled = visibleState.targetState)`。
     *    否则关闭动画期间或残留层级会永久霸占 Android `OnBackPressedDispatcher` 栈顶，拦截并吞掉所有后续返回键，导致横屏模式下系统返回键彻底失灵。
     */
    @Test
    fun player_right_drawer_must_bind_lifecycle_and_conditional_back_handler() {
        val file =
            File(projectRoot, "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerPanelOverlays.kt")
        assertTrue(file.isFile, "PlayerPanelOverlays.kt 未找到")
        val code = KotlinSourceScanner.sanitize(file.readText())

        val drawerToken = "fun PlayerRightDrawerContainer"
        val drawerStart = code.indexOf(drawerToken)
        assertTrue(drawerStart >= 0, "PlayerRightDrawerContainer 未找到")

        // 提取 PlayerRightDrawerContainer 函数体
        val openBrace = code.indexOf('{', drawerStart)
        assertTrue(openBrace >= 0, "PlayerRightDrawerContainer 函数体未找到")
        var depth = 0
        var closeBrace = openBrace
        while (closeBrace < code.length) {
            when (code[closeBrace]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            closeBrace++
        }
        val drawerBody = code.substring(openBrace, minOf(closeBrace + 1, code.length))

        // 1. 严禁使用 SideEffect 判定退出动效完成
        if (drawerBody.contains("SideEffect")) {
            fail("PlayerRightDrawerContainer 严禁使用 SideEffect：宿主未重组时退出动效结束无法触发响应，导致挂起协程泄漏与返回键失效")
        }

        // 2. 动效完成必须使用 snapshotFlow 收集
        if (!drawerBody.contains("snapshotFlow")) {
            fail("PlayerRightDrawerContainer 必须使用 snapshotFlow 监听退出动效结束并触发 onRespond")
        }

        // 3. 必须包含 DisposableEffect 兜底下树清理
        if (!drawerBody.contains("DisposableEffect") || !drawerBody.contains("onDispose")) {
            fail("PlayerRightDrawerContainer 必须声明 DisposableEffect(Unit).onDispose 进行下树兜底清理，避免横竖屏切换时协程泄漏")
        }

        // 4. BackHandler 必须按 targetState 条件使能
        val hasConditionalBackHandler =
            drawerBody.contains("BackHandler(enabled = visibleState.targetState)") ||
                drawerBody.contains("BackHandler(enabled =")
        if (!hasConditionalBackHandler) {
            fail("PlayerRightDrawerContainer 的 BackHandler 必须声明 enabled = visibleState.targetState，防止关闭后拦截后续系统返回键")
        }
    }

    /** [inner] 是否出现在以 [blockToken] 开头、花括号配对的代码块内 */
    private fun mentionsInsideBlock(
        code: String,
        blockToken: String,
        inner: String,
    ): Boolean {
        var searchFrom = 0
        while (true) {
            val start = code.indexOf(blockToken, searchFrom)
            if (start < 0) return false
            val open = code.indexOf('{', start + blockToken.length)
            if (open < 0) return false
            var depth = 0
            var index = open
            while (index < code.length) {
                when (code[index]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                index++
            }
            val block = code.substring(open, minOf(index + 1, code.length))
            if (block.contains(inner)) return true
            searchFrom = index + 1
        }
    }

    private companion object {
        /** 红线只约束生产源码：build 产物与各测试源集目录一律不下钻 */
        val NON_SOURCE_DIR_NAMES =
            setOf("build", "test", "androidTest", "androidHostTest", "androidDeviceTest", "commonTest")

        /**
         * `AGENTS.md` 上下文预算（字符数，按 LF 归一后计量）。
         *
         * 棘轮语义：只允许「删等量再写」；确需上调时必须把这个数字改大，并在提交说明里写明
         * 为什么这笔上下文值得占用——涨幅会出现在 diff 里，而不是悄悄长胖。
         */
        const val AGENTS_MD_CHAR_BUDGET = 9_600

        /** `AGENTS.md` 单行字符上限：巨型 bullet 既没人读，也拆不出可评审的 diff */
        const val AGENTS_MD_MAX_LINE_CHARS = 900

        /** 单个 `SKILL.md` 的上限：路由 + 铁律够用，细节必须进 references/ */
        const val SKILL_MAX_CHARS = 6_000

        /** 单个 reference 的上限：一次 read 不该把上下文顶爆 */
        const val SKILL_REFERENCE_MAX_CHARS = 10_000
    }
}
