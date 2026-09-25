package com.infinitezerone.minibgm

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.NONE
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * 生成模块依赖图（graphDump 任务）并用 graphUpdate 把 Mermaid 图写回各模块 README.md。
 *
 * 移植自 Now in Android 的 Graph.kt（Apache License 2.0），适配本仓库的 minibgm.* 插件体系。
 * 与 NIA 的差异：
 * - 插件类型着色对应 minibgm.*（application / feature / kmpLibrary / androidLibrary）；
 * - KMP 模块的依赖声明在按源集命名的 configuration（commonMainImplementation 等）里，
 *   默认白名单除 api / implementation 外还包含 *MainApi / *MainImplementation；
 * - 可用 graph.supportedConfigurations / graph.ignoredProjects 属性覆盖默认行为。
 *
 * 已知非最优（沿用 NIA 原注释）：Graph.invoke 递归遍历；每个工程都全量重算；
 * 始终在配置期执行（单工程通常 < 1ms）。
 */
private class Graph(
    private val root: Project,
    private val dependencies: MutableMap<Project, Set<Pair<Configuration, Project>>> = mutableMapOf(),
    private val plugins: MutableMap<Project, PluginType> = mutableMapOf(),
    private val seen: MutableSet<String> = mutableSetOf(),
) {

    private val ignoredProjects =
        root.providers
            .gradleProperty("graph.ignoredProjects")
            .map { it.split(",").toSet() }
            .orElse(emptySet())
    private val supportedConfigurationsOverride =
        root.providers
            .gradleProperty("graph.supportedConfigurations")
            .map { it.split(",").toSet() }
            .orElse(emptySet())

    private fun isSupported(configuration: Configuration): Boolean =
        if (supportedConfigurationsOverride.get().isNotEmpty()) {
            configuration.name in supportedConfigurationsOverride.get()
        } else {
            val name = configuration.name
            name == "api" ||
                name == "implementation" ||
                name.endsWith("MainApi") ||
                name.endsWith("MainImplementation")
        }

    operator fun invoke(project: Project = root): Graph {
        if (project.path in seen) return this
        seen += project.path
        plugins.putIfAbsent(
            project,
            PluginType.entries.firstOrNull { project.pluginManager.hasPlugin(it.id) } ?: PluginType.Unknown,
        )
        dependencies.compute(project) { _, u -> u.orEmpty() }
        project.configurations
            .matching { isSupported(it) }
            .flatMap { configuration ->
                configuration.dependencies
                    .withType<ProjectDependency>()
                    .map { dependency -> configuration to project.project(dependency.path) }
            }.filter { (_, dependencyProject) -> dependencyProject.path !in ignoredProjects.get() }
            .forEach { (configuration, dependencyProject) ->
                dependencies.compute(project) { _, u -> u.orEmpty() + (configuration to dependencyProject) }
                invoke(dependencyProject)
            }
        return this
    }

    fun dependencies(): Map<String, Set<Pair<String, String>>> = dependencies
        .mapKeys { it.key.path }
        .mapValues { it.value.mapTo(mutableSetOf()) { (c, p) -> c.name to p.path } }

    fun plugins() = plugins.mapKeys { it.key.path }
}

/**
 * 声明顺序有意义，只有第一个匹配会被采用。
 */
internal enum class PluginType(val id: String, val ref: String, val style: String) {
    AndroidApplication(
        id = "minibgm.android.application",
        ref = "android-application",
        style = "fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000",
    ),
    AndroidFeature(
        id = "minibgm.android.feature",
        ref = "android-feature",
        style = "fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000",
    ),
    KmpLibrary(
        id = "minibgm.kmp.library",
        ref = "kmp-library",
        style = "fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    AndroidLibrary(
        id = "minibgm.android.library",
        ref = "android-library",
        style = "fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    Unknown(
        id = "?",
        ref = "unknown",
        style = "fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000",
    ),
}

internal fun Project.configureGraphTasks() {
    if (!buildFile.exists()) return // 跳过没有构建文件的模块
    val dumpTask =
        tasks.register<GraphDumpTask>("graphDump") {
            val graph = Graph(this@configureGraphTasks).invoke()
            projectPath = this@configureGraphTasks.path
            dependencies = graph.dependencies()
            plugins = graph.plugins()
            output = this@configureGraphTasks.layout.buildDirectory.file("mermaid/graph.txt")
            legend = this@configureGraphTasks.layout.buildDirectory.file("mermaid/legend.txt")
        }
    tasks.register<GraphUpdateTask>("graphUpdate") {
        projectPath = this@configureGraphTasks.path
        input = dumpTask.flatMap { it.output }
        legend = dumpTask.flatMap { it.legend }
        output = this@configureGraphTasks.layout.projectDirectory.file("README.md")
    }
}

@CacheableTask
private abstract class GraphDumpTask : DefaultTask() {

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val dependencies: MapProperty<String, Set<Pair<String, String>>>

    @get:Input
    abstract val plugins: MapProperty<String, PluginType>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val legend: RegularFileProperty

    override fun getDescription() = "将本模块（及传递）的项目依赖输出为 Mermaid 图。"

    @TaskAction
    operator fun invoke() {
        output.get().asFile.writeText(mermaid())
        legend.get().asFile.writeText(legend())
        logger.lifecycle(output.get().asFile.toPath().toUri().toString())
    }

    private fun mermaid() = buildString {
        val deps: Set<Dependency> = dependencies.get()
            .flatMapTo(mutableSetOf()) { (project, entries) -> entries.map { it.toDependency(project) } }
        // FrontMatter（GitHub.com 尚不支持）
        appendLine(
            // language=YAML
            """
            ---
            config:
              layout: elk
              elk:
                nodePlacementStrategy: SIMPLE
            ---
            """.trimIndent(),
        )
        appendLine("graph TB")
        // 节点与子图
        val (rootProjects, nestedProjects) = deps
            .map { listOf(it.project, it.dependency) }.flatten().toSet()
            .plus(projectPath.get()) // 本模块无任何依赖时的兜底
            .groupBy { it.substringBeforeLast(":") }
            .entries.partition { it.key.isEmpty() }

        val orderedGroups = nestedProjects.groupBy {
            if (it.key.count { char -> char == ':' } > 1) it.key.substringBeforeLast(":") else ""
        }

        orderedGroups.forEach { (outerGroup, innerGroups) ->
            if (outerGroup.isNotEmpty()) {
                appendLine("  subgraph $outerGroup")
                appendLine("    direction TB")
            }
            innerGroups.sortedWith(
                compareBy(
                    { (group, _) ->
                        deps.filter { dep ->
                            val toGroup = dep.dependency.substringBeforeLast(":")
                            toGroup == group && dep.project.substringBeforeLast(":") != group
                        }.count()
                    },
                    { -it.value.size },
                ),
            ).forEach { (group, projects) ->
                val indent = if (outerGroup.isNotEmpty()) 4 else 2
                appendLine(" ".repeat(indent) + "subgraph $group")
                appendLine(" ".repeat(indent) + "  direction TB")
                projects.sorted().forEach {
                    appendLine(it.alias(indent = indent + 2, plugins.get().getValue(it)))
                }
                appendLine(" ".repeat(indent) + "end")
            }
            if (outerGroup.isNotEmpty()) {
                appendLine("  end")
            }
        }

        rootProjects.flatMap { it.value }.sortedDescending().forEach {
            appendLine(it.alias(indent = 2, plugins.get().getValue(it)))
        }
        // 连线
        if (deps.isNotEmpty()) appendLine()
        deps.sortedWith(compareBy({ it.project }, { it.dependency }, { it.configuration }))
            .forEach { appendLine(it.link(indent = 2)) }
        // 样式
        appendLine()
        PluginType.entries.forEach { appendLine(it.classDef()) }
    }

    private fun legend() = buildString {
        appendLine("graph TB")
        listOf(
            "application" to PluginType.AndroidApplication,
            "feature" to PluginType.AndroidFeature,
            "kmp library" to PluginType.KmpLibrary,
            "android library" to PluginType.AndroidLibrary,
        ).forEach { (name, type) ->
            appendLine(name.alias(indent = 2, type))
        }
        appendLine()
        listOf(
            Dependency("application", "implementation", "feature"),
            Dependency("library", "api", "kmp library"),
        ).forEach {
            appendLine(it.link(indent = 2))
        }
        appendLine()
        PluginType.entries.forEach { appendLine(it.classDef()) }
    }

    private class Dependency(val project: String, val configuration: String, val dependency: String)

    private fun Pair<String, String>.toDependency(project: String) =
        Dependency(project, configuration = first, dependency = second)

    private fun String.alias(indent: Int, pluginType: PluginType): String = buildString {
        append(" ".repeat(indent))
        append(this@alias)
        append("[").append(substringAfterLast(":")).append("]:::")
        append(pluginType.ref)
    }

    private fun Dependency.link(indent: Int) = buildString {
        append(" ".repeat(indent))
        append(project).append(" ")
        append(
            when (configuration) {
                "api" -> "-->"
                "implementation" -> "-.->"
                else -> "-.->|$configuration|"
            },
        )
        append(" ").append(dependency)
    }

    private fun PluginType.classDef() = "classDef $ref $style;"
}

@CacheableTask
private abstract class GraphUpdateTask : DefaultTask() {

    @get:Input
    abstract val projectPath: Property<String>

    @get:InputFile
    @get:PathSensitive(NONE)
    abstract val input: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(NONE)
    abstract val legend: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    override fun getDescription() = "把依赖图写回模块 README.md。"

    @TaskAction
    operator fun invoke() = with(output.get().asFile) {
        if (!exists()) {
            createNewFile()
            writeText(
                """
                # `${projectPath.get()}`

                ## Module dependency graph

                <!--region graph--> <!--endregion-->

                """.trimIndent(),
            )
        }
        val mermaid = input.get().asFile.readText().trimTrailingNewLines()
        val legend = legend.get().asFile.readText().trimTrailingNewLines()
        val regex = """(<!--region graph-->)(.*?)(<!--endregion-->)""".toRegex(DOT_MATCHES_ALL)
        // 手写的 README（模块职责文档等）若还没有图区块，追加到文末而不是跳过
        var text = readText()
        if (!regex.containsMatchIn(text)) {
            text = text.trimEnd() + "\n\n## Module dependency graph\n\n<!--region graph--> <!--endregion-->\n"
        }
        val newText = text.replace(regex) { match ->
            val (start, _, end) = match.destructured
            """
            |$start
            |```mermaid
            |$mermaid
            |```
            |
            |<details><summary>📋 Graph legend</summary>
            |
            |```mermaid
            |$legend
            |```
            |
            |</details>
            |$end
            """.trimMargin()
        }
        writeText(newText)
    }

    private fun String.trimTrailingNewLines() = lines()
        .dropLastWhile(String::isBlank)
        .joinToString(System.lineSeparator())
}
