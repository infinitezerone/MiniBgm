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

        assertTrue(
            violations.isEmpty(),
            "违反 ECH 优选池防污染与 BT 流播/下载单盘协同红线：\n" + violations.joinToString("\n"),
        )
    }

    private companion object {
        /** 红线只约束生产源码：build 产物与各测试源集目录一律不下钻 */
        val NON_SOURCE_DIR_NAMES =
            setOf("build", "test", "androidTest", "androidHostTest", "androidDeviceTest", "commonTest")
    }
}
