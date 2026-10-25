package com.infinitezerone.minibgm.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** 域规则文件：从 ArchitectureRulesTest 拆出（测试名即规则名，复盘在各测试 KDoc）。 */
class UiDesignRulesTest : ArchitectureRuleTestSupport() {
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
    fun feature_content_never_uses_system_chrome_glass() {
        val featureDir = File(projectRoot, "feature")
        assertTrue(featureDir.isDirectory, "feature 目录未找到: ${featureDir.absolutePath}")

        val violations = mutableListOf<String>()
        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                scanner.containsToken("BgmGlassSurface").forEach { match ->
                    violations.add(
                        "${sourceFile.relativeTo(projectRoot).path}:${match.lineNumber} " +
                            "feature 内容层使用了系统 chrome 玻璃 -> ${match.lineContent}",
                    )
                }
            }

        assertTrue(
            violations.isEmpty(),
            "违反材质分层：玻璃只允许用于悬浮导航、Snackbar 等系统 chrome / 瞬态浮层；" +
                "内容卡片与容器必须使用 BgmFloatingFrame 的实体悬浮框：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 【顶栏双模式范式守卫】
     * MiniBgm 顶栏采用沉浸天幕模式与实体稳固结构模式（BgmTopBarMode）。
     * 特性页面严禁在 BgmTopAppBar 上硬编码 containerColor，破坏沉浸天幕穿透或实体底色；
     * 实心顶栏统一声明 mode = BgmTopBarMode.Solid，沉浸顶栏统一声明 mode = BgmTopBarMode.Immersive。
     */
    @Test
    fun feature_screens_must_not_hardcode_solid_top_app_bar_colors() {
        val featureDir = File(projectRoot, "feature")
        val violations = mutableListOf<String>()
        featureDir
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val scanner = KotlinSourceScanner.fromFile(sourceFile)
                scanner.containsToken("topAppBarColors").forEach { match ->
                    if (match.lineContent.contains("containerColor = MaterialTheme.colorScheme.surface") ||
                        match.lineContent.contains("containerColor = Color.Transparent")
                    ) {
                        violations.add(
                            "${sourceFile.relativeTo(projectRoot).path}:${match.lineNumber} " +
                                "使用了硬编码 topAppBarColors，应改用 BgmTopBarMode.Solid 或 BgmTopBarMode.Immersive -> ${match.lineContent.trim()}",
                        )
                    }
                }
            }
        assertTrue(
            violations.isEmpty(),
            "发现页面硬编码 TopAppBar 容器颜色（应改用 BgmTopBarMode）：\n${violations.joinToString("\n")}",
        )
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

    /**
     * 明暗判定统一走当前生效色阶（红线）：
     * 应用支持 `ThemeMode.LIGHT/DARK` 强制覆盖系统设置，`isSystemInDarkTheme()` 读的是系统配置，
     * 与生效色 scheme 相悖——同一组件会出现「亮色阴影 + 暗档底色」的明暗错配。
     *
     * 允许名单只有主题引导层两处（它们正是把系统偏好投影进色 scheme 的入口）：
     * `app/.../MainActivity.kt`（ThemeMode.SYSTEM 分支）与
     * `core/designsystem/.../theme/Theme.kt`（默认参数兜底）。
     * 其余任何 UI 代码需要明暗档位时，一律 `MaterialTheme.colorScheme.surface.luminance() < 0.5f`。
     */
    @Test
    fun feature_ui_must_not_decide_darkness_from_system_config() {
        val violations = mutableListOf<String>()
        val pattern = Regex("""\bisSystemInDarkTheme\s*\(""")
        val allowedPaths =
            setOf(
                "app/src/main/kotlin/com/infinitezerone/minibgm/MainActivity.kt",
                "core/designsystem/src/main/kotlin/com/infinitezerone/minibgm/core/designsystem/theme/Theme.kt",
            )

        projectRoot
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" && "src/main" in it.path }
            .forEach { file ->
                val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                if (relPath in allowedPaths) return@forEach
                KotlinSourceScanner.fromFile(file).findMatches(pattern).forEach { match ->
                    violations.add(
                        "$relPath:${match.lineNumber} 违规使用 isSystemInDarkTheme -> ${match.lineContent.trim()}。" +
                            "应用支持强制亮/暗主题覆盖，明暗判定必须走生效色阶（surface.luminance()）",
                    )
                }
            }

        assertTrue(
            violations.isEmpty(),
            "明暗判定必须与生效色阶同源（isSystemInDarkTheme 只允许出现在主题引导层）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 触控靶区红线（M3 ≥48dp）：
     * 设计系统的可点击视觉原语（如 40dp 圆盘）必须在组件层显式扩展交互区
     * （`minimumInteractiveComponentSize`），不得让触控靶区等于视觉尺寸。
     * 本规则是对 [com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton] 的
     * 回归绊线——它是「40dp 视觉 + 48dp 靶区」范式的落点。
     */
    @Test
    fun designsystem_touch_target_primitives_must_extend_interactive_area() {
        val file =
            File(
                projectRoot,
                "core/designsystem/src/main/kotlin/com/infinitezerone/minibgm/core/designsystem/component/BgmCircleActionButton.kt",
            )
        assertTrue(file.isFile, "BgmCircleActionButton.kt 未找到: ${file.absolutePath}")
        assertTrue(
            file.readText().contains("minimumInteractiveComponentSize"),
            "BgmCircleActionButton 必须经 minimumInteractiveComponentSize 保证 ≥48dp 触控靶区（视觉尺寸 != 触控靶区）",
        )
    }

/**

     * 动作键原语统一（红线）：
     * 全局移除涟漪后，M3 原生 IconButton / FloatingActionButton 按下半径内零反馈——
     * 水生按压反馈只挂在自家原语（BgmCircleActionButton / BgmGhostIconButton / BgmPillButton）上，
     * 生产代码严禁再引入 M3 图标键与 FAB（对话框转圈、角标等场景用原语的 content 槽重载承接）。
     */
    @Test
    fun production_code_never_uses_material3_icon_or_fab_buttons() {
        val violations = mutableListOf<String>()
        val pattern = Regex("""androidx.compose.material3.(IconButton|FloatingActionButton|ExtendedFloatingActionButton)""")
        listOf(File(projectRoot, "feature"), File(projectRoot, "app/src/main")).forEach { dir ->
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    KotlinSourceScanner.fromFile(file).findMatches(pattern).forEach { match ->
                        violations.add(
                            "$relPath:${match.lineNumber} 违规引入 M3 原生动作键 -> ${match.lineContent.trim()}，" +
                                "必须使用 BgmGhostIconButton / BgmCircleActionButton / BgmPillButton（水生按压反馈 + 48dp 靶区）",
                        )
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "动作键必须统一为水生原语（M3 IconButton/FAB 无按压反馈）：" +
                violations.joinToString("\n"),
        )
    }

    /**
     * Modifier.Node 架构规范（第 1 条）：
     * 继承自 [ModifierNodeElement] 的类必须声明为 data class，或显式 override equals 与 hashCode。
     *
     * 架构原理：
     * Compose 在重组阶段比对修饰符链时，完全依赖 Element.equals() 判断是复用已有 Node 并调用 update(node)，
     * 还是销毁旧 Node 并重新 create()。若不是 data class 且未重写 equals，将使用对象引用比对，
     * 导致每次重组都误判为新修饰符而频繁销毁重建，彻底丧失 Node 性能优势，并在异步手势中引发触控断流。
     */
    @Test
    fun modifier_node_elements_must_be_data_classes_or_implement_value_equality() {
        val violations = mutableListOf<String>()
        val elementInheritanceRegex =
            Regex("""([a-zA-Z0-9_\s]*?)\bclass\s+(\w+)[^{:]*?:\s*(?:[^{:]*?\b)?ModifierNodeElement\s*<""")

        projectRoot
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val scanner = KotlinSourceScanner.fromFile(file)
                val code = scanner.sanitizedContent

                elementInheritanceRegex.findAll(code).forEach { match ->
                    val prefix = match.groupValues[1]
                    val className = match.groupValues[2]
                    val isDataClass = prefix.contains(Regex("""\bdata\s+"""))

                    if (!isDataClass) {
                        val hasEquals = code.contains(Regex("""override\s+fun\s+equals\s*\("""))
                        val hasHashCode = code.contains(Regex("""override\s+fun\s+hashCode\s*\("""))
                        if (!hasEquals || !hasHashCode) {
                            val line = scanner.getLineNumber(match.range.first)
                            val relPath = file.relativeTo(projectRoot).path
                            violations.add(
                                "$relPath:$line: $className 继承 ModifierNodeElement 但未声明为 data class 且未实现 equals/hashCode",
                            )
                        }
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "所有 ModifierNodeElement 必须声明为 data class 或显式实现 equals/hashCode，以保障 Compose 节点复用：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * Modifier.Node 架构规范（第 2 条）：
     * 严禁在生产代码中使用遗留的 [Modifier.composed]。
     *
     * 架构原理：
     * Modifier.composed 在 Composition 阶段强制插入组合作用域解析层，绕过 Layout/Draw 阶段优化并破坏
     * 局部跳过（Skipping）机制。所有自定义微交互修饰符必须采用现代 Modifier.Node 节点或 this then ... 链式组合。
     */
    @Test
    fun custom_modifiers_must_never_use_legacy_modifier_composed() {
        val violations = mutableListOf<String>()
        val composedPattern = Regex("""\bModifier\.composed\b|\.composed\s*\{""")

        projectRoot
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val scanner = KotlinSourceScanner.fromFile(file)
                val matches = scanner.findMatches(composedPattern)
                val relPath = file.relativeTo(projectRoot).path
                matches.forEach { match ->
                    violations.add("$relPath:${match.lineNumber}: 违规调用遗留的 Modifier.composed -> ${match.lineContent}")
                }
            }

        assertTrue(
            violations.isEmpty(),
            "生产代码严禁使用已废弃/低性能的 Modifier.composed，必须迁移至 Modifier.Node 或标准修饰符链：\n" +
                violations.joinToString("\n"),
        )
    }
}
