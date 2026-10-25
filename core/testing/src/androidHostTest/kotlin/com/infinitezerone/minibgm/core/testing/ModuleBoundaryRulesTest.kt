package com.infinitezerone.minibgm.core.testing

import com.infinitezerone.minibgm.core.datastore.UserPreferences
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** 域规则文件：从 ArchitectureRulesTest 拆出（测试名即规则名，复盘在各测试 KDoc）。 */
class ModuleBoundaryRulesTest : ArchitectureRuleTestSupport() {
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
                val content = sourceFile.readText()
                // 两种形态一并拦截：SnackbarHost 直发，以及 Channel/SharedFlow 的 send/trySend 直发
                // （"trySend(" 与 "resend(" 等词尾均含 "send("，此处只匹配 .send( / .trySend( 调用）。
                val cjkInSend = findCjkLiteralsInCallArgs(content, ".send(") + findCjkLiteralsInCallArgs(content, ".trySend(")
                (findCjkLiteralsInCallArgs(content, "ShowSnackbar(") + cjkInSend).forEach { (lineNumber, literal) ->
                    violations.add(
                        "$relPath:$lineNumber 一次性事件内联用户文案 -> \"$literal\"。" +
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
     * 对 [callToken] 做括号配对得到跨度，再回原文提取跨度内的字符串字面量检查 CJK——
     * 注释里提到 callToken("示例") 不会误伤，跨行调用也不会漏判。
     */
    private fun findCjkLiteralsInCallArgs(
        content: String,
        callToken: String,
    ): List<Pair<Int, String>> {
        val sanitized = KotlinSourceScanner.fromContent(content).sanitizedContent
        val spans = mutableListOf<IntRange>()
        var index = sanitized.indexOf(callToken)
        while (index >= 0) {
            var depth = 1
            var cursor = index + callToken.length
            while (cursor < sanitized.length && depth > 0) {
                when (sanitized[cursor]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                cursor++
            }
            spans += index until cursor
            index = sanitized.indexOf(callToken, cursor)
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
}
