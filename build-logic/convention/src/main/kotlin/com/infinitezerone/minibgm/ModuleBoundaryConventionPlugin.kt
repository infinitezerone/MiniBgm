package com.infinitezerone.minibgm

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.configuration.BuildFeatures
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

/**
 * 配置期模块边界校验（AGENTS.md 红线 1/2/9 的依赖图版）。
 *
 * 借鉴 Now in Android 的 Graph.kt：只读 [ProjectDependency.path] 取目标工程路径，
 * 不触碰目标 [Project] 对象，无 eager evaluation，与 Isolated Projects 兼容。
 *
 * 在根工程应用：gradle.projectsEvaluated 时所有工程均已配置完成，此时遍历每个
 * configuration 实际声明的 ProjectDependency 做断言。相比 ArchitectureRulesTest 的
 * build 脚本正则扫描：
 * - 检查对象是 Gradle 解析出的依赖对象，动态拼接的 project(":...") 无法逃逸；
 * - 覆盖所有 configuration（api / implementation / debugImplementation / 自定义容器）；
 * - 任何 ./gradlew 调用都自动执行，无需单独的验证任务，也不做配置期 resolve。
 *
 * 同时承担根工程接线职责：为所有模块注册 graphDump / graphUpdate 任务（README 依赖图）。
 */
abstract class ModuleBoundaryConventionPlugin : Plugin<Project> {
    @get:Inject
    abstract val buildFeatures: BuildFeatures

    override fun apply(root: Project) {
        check(root === root.rootProject) { "minibgm.module.boundary 只能应用在根工程" }

        // Isolated Projects 下配置期遍历其他 Project 不被允许，与 NIA 的 RootPlugin 一样跳过
        if (!buildFeatures.isolatedProjects.active.getOrElse(false)) {
            root.subprojects { configureGraphTasks() }
        }

        root.gradle.projectsEvaluated {
            val violations = buildList {
                root.allprojects.forEach { from ->
                    from.configurations.forEach { configuration ->
                        configuration.dependencies
                            .withType<ProjectDependency>()
                            .forEach { dependency ->
                                val to = dependency.path
                                if (to == from.path) return@forEach
                                boundaryViolation(from.path, to)?.let { reason ->
                                    add("${from.path} -> $to (${configuration.name})：$reason")
                                }
                            }
                    }
                }
            }
            if (violations.isNotEmpty()) {
                throw GradleException(
                    "模块边界违规（配置期依赖图校验，见 ModuleBoundaryConventionPlugin）：\n" +
                        violations.joinToString("\n"),
                )
            }
        }
    }

    /** 返回违规原因；null 表示合法。只约束 :feature:* 的出边，其余模块由依赖方向本身约束。 */
    private fun boundaryViolation(from: String, to: String): String? {
        if (!from.startsWith(":feature:")) return null
        return when {
            to.startsWith(":feature:") -> "feature 之间禁止互相依赖（红线 1）"
            to in FORBIDDEN_CORE -> "feature 禁止越级依赖 $to，必须经 :core:data Repository 协调（红线 2）"
            to == ":core:ai" && from != ":feature:assistant" ->
                "只有 :feature:assistant 可依赖 :core:ai，其他页面走 AssistantRoute(prefillPrompt) 交接（红线 9）"
            else -> null
        }
    }

    private companion object {
        val FORBIDDEN_CORE = setOf(":core:network", ":core:database", ":core:datastore")
    }
}
