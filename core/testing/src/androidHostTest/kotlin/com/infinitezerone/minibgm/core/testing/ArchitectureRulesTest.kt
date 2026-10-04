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
        val ladder = setOf("4", "8", "12", "16", "24")
        val radiusLiteral = Regex("""RoundedCornerShape\\(([^()]*)\\)""")
        val dpValue = Regex("""(\\d+)\\.dp""")

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
                        dpValue.findAll(match.lineContent).forEach { value ->
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

    private companion object {
        /** 红线只约束生产源码：build 产物与各测试源集目录一律不下钻 */
        val NON_SOURCE_DIR_NAMES =
            setOf("build", "test", "androidTest", "androidHostTest", "androidDeviceTest", "commonTest")
    }
}
