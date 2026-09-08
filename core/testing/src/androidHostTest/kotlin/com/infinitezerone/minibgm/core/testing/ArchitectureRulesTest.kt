package com.infinitezerone.minibgm.core.testing

import com.infinitezerone.minibgm.core.datastore.UserPreferences
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 对应 AGENTS.md 核心架构规范与安全边界的确定性自动化测试：
 * 1. Feature 隔离：:feature:A 绝不能依赖 :feature:B
 * 2. 单一数据源：UI 层（:feature:*）严禁越级依赖或 import :core:network / :core:database / :core:datastore
 * 3. 纯模型层：:core:model 为纯 Kotlin，严禁引入任何 android.* / androidx.* / UI 框架依赖
 * 4. MVI 单向流：所有 ViewModel 严禁对外暴露 MutableStateFlow，必须暴露不可变 StateFlow
 * 5. 凭据隔离：UserPreferences 绝不包含任何 token / 凭据字段（Token 必须走 AndroidKeyStore）
 * 6. 传输与存储框架隔离：feature 源码严禁 import io.ktor.* 或 androidx.room.*
 * 7. 主题一致性：feature 源码严禁硬编码 Color(0x...)，必须使用 :core:designsystem 主题 token
 * 8. 裸 IO 隔离：feature 源码严禁手写底层网络传输（HttpURLConnection / java.net.*）与私有磁盘 IO（cacheDir / filesDir / FileOutputStream）
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
    fun feature_modules_never_depend_on_each_other() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()
        featureDir
            .walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            .forEach { buildScript ->
                val moduleName = buildScript.parentFile?.name ?: "unknown"
                val lines = buildScript.readLines()
                lines.forEachIndexed { index, line ->
                    if (line.contains("project(\":feature:") || line.contains("project(':feature:")) {
                        violations.add("[$moduleName] build.gradle.kts:${index + 1} 依赖了其他 feature 模块 -> $line")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail("违反 Feature 隔离原则（:feature:A 绝不可依赖 :feature:B）：\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun feature_modules_never_depend_directly_on_network_or_database() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()

        // 1. 检查 build.gradle.kts 依赖
        featureDir
            .walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            .forEach { buildScript ->
                val moduleName = buildScript.parentFile?.name ?: "unknown"
                buildScript.readLines().forEachIndexed { index, line ->
                    if (line.contains("project(\":core:network\")") ||
                        line.contains("project(':core:network')") ||
                        line.contains("project(\":core:database\")") ||
                        line.contains("project(':core:database')")
                    ) {
                        violations.add("[$moduleName] build.gradle.kts:${index + 1} 越级依赖了底层库 -> $line")
                    }
                }
            }

        // 2. 检查源码 import（传输/存储框架 import 由 feature_sources_never_import_transport_or_storage_frameworks 单独负责）
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
    fun feature_modules_never_depend_directly_on_datastore() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()

        // 1. 检查 build.gradle.kts 依赖
        featureDir
            .walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            .forEach { buildScript ->
                val moduleName = buildScript.parentFile?.name ?: "unknown"
                buildScript.readLines().forEachIndexed { index, line ->
                    if (line.contains("project(\":core:datastore\")") ||
                        line.contains("project(':core:datastore')")
                    ) {
                        violations.add("[$moduleName] build.gradle.kts:${index + 1} 越级依赖了 :core:datastore -> $line")
                    }
                }
            }

        // 2. 检查源码 import
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
}
