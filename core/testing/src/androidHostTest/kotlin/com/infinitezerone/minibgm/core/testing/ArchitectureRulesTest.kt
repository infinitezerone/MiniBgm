package com.infinitezerone.minibgm.core.testing

import com.infinitezerone.minibgm.core.datastore.UserPreferences
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 对应 AGENTS.md 核心架构规范与安全边界的确定性自动化测试：
 * 1. Feature 隔离：:feature:A 绝不能依赖 :feature:B
 * 2. 单一数据源：UI 层（:feature:*）严禁越级依赖或 import :core:network 或 :core:database
 * 3. 纯模型层：:core:model 为纯 Kotlin，严禁引入任何 android.* / androidx.* / UI 框架依赖
 * 4. MVI 单向流：所有 ViewModel 严禁对外暴露 MutableStateFlow，必须暴露不可变 StateFlow
 * 5. 凭据隔离：UserPreferences 绝不包含任何 token / 凭据字段（Token 必须走 AndroidKeyStore）
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

        // 2. 检查源码 import
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
                        violations.add("$relPath:${index + 1} 违规直连底库 -> $trimmed")
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
                    val isPublic = !trimmed.startsWith("private ") && !trimmed.startsWith("internal private ")
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
}
