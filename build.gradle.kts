plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlinGradle {
        target("*.kts")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Compose 编译器指标：-Pminibgm.composeMetrics=true 时输出 recompose/skippability 报告，
// 用于排查"不符合规范的 UI 写法"（不可跳过 Composable、不稳定 lambda）。
// 报告输出在 build/compose-reports/<模块名>/，默认关闭避免每次构建的开销。
val composeMetricsEnabled = providers.gradleProperty("minibgm.composeMetrics").isPresent

subprojects {
    if (!composeMetricsEnabled) return@subprojects
    val reportsDir =
        rootProject.layout.buildDirectory
            .dir("compose-reports/${project.name}")
            .get()
            .asFile
    val metricsDir =
        rootProject.layout.buildDirectory
            .dir("compose-metrics/${project.name}")
            .get()
            .asFile
    val composeArgs =
        listOf(
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${reportsDir.absolutePath}",
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${metricsDir.absolutePath}",
        )
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions.freeCompilerArgs.addAll(
                composeArgs.flatMap { listOf("-P", it) },
            )
        }
    }
}
