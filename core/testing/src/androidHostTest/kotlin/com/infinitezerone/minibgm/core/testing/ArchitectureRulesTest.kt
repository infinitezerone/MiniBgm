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
     * 2. `AssetDownloadService` 必须将本地回环 HTTP 流（`isLoopbackHttpUrl`）分流至纯流式 `CIO` 引擎，
     *    严禁将 `http://127.0.0.1` 的 P2P 视频流送入仅支持 `https://` 且全量内存缓冲的 `EchHttpClientEngine`；
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
        if (!assetContent.contains("isLoopbackHttpUrl") || !assetContent.contains("HttpClient(CIO)")) {
            violations.add(
                "AssetDownloadService.kt 未对本地回环流 (http://127.0.0.1) 分流至流式 CIO 引擎，将导致本地 P2P 流被 EchHttpClientEngine 拦截失败",
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

    private companion object {
        /** 红线只约束生产源码：build 产物与各测试源集目录一律不下钻 */
        val NON_SOURCE_DIR_NAMES =
            setOf("build", "test", "androidTest", "androidHostTest", "androidDeviceTest", "commonTest")
    }
}
