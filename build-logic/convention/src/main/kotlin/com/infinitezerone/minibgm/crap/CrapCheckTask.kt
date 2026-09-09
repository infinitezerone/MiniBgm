package com.infinitezerone.minibgm.crap

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Evaluates CRAP (Change Risk Anti-Patterns) scores across KMP modules using Kover XML reports.
 * Formula: CRAP(m) = CC(m)^2 * (1 - Cov(m))^3 + CC(m)
 *
 * Excludes compiler-generated synthetic bytecode and test harnesses.
 * Enforces a strict threshold (default CRAP <= 30.0), while supporting an explicit technical debt
 * ledger (`crap-baseline.json`) for known high-complexity methods.
 */
abstract class CrapCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val koverReports: ConfigurableFileCollection

    @get:Input
    abstract val threshold: Property<Double>

    @get:Input
    abstract val warnThreshold: Property<Double>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:OutputFile
    abstract val reportOutput: RegularFileProperty

    init {
        threshold.convention(30.0)
        warnThreshold.convention(15.0)
    }

    data class MethodScore(
        val module: String,
        val sourceFile: String,
        val className: String,
        val methodName: String,
        val displayName: String,
        val cc: Int,
        val coveredLines: Int,
        val totalLines: Int,
        val coverage: Double,
        val crap: Double,
        val isBaselined: Boolean,
        val status: Status,
    )

    enum class Status {
        OK,
        WARN,
        BASELINED,
        DANGER,
    }

    @TaskAction
    fun execute() {
        val maxThreshold = threshold.get()
        val warnThresh = warnThreshold.get()
        val baseline = baselineFile.orNull?.asFile?.let { parseBaseline(it) } ?: emptySet()

        val reports = koverReports.files.filter { it.exists() && it.isFile }
        if (reports.isEmpty()) {
            logger.warn("⚠️  [crapCheck] No Kover XML reports found. Skipping.")
            reportOutput.get().asFile.writeText("No reports found.\n")
            return
        }

        val allMethods = mutableListOf<MethodScore>()
        for (report in reports) {
            val moduleName = deriveModuleName(report)
            allMethods.addAll(parseReport(report, moduleName, baseline, maxThreshold, warnThresh))
        }

        allMethods.sortByDescending { it.crap }

        val violations = allMethods.filter { it.status == Status.DANGER }
        val warnings = allMethods.filter { it.status == Status.WARN }
        val baselined = allMethods.filter { it.status == Status.BASELINED }
        val displayMethods = allMethods.filter { it.crap >= warnThresh || it.cc >= 10 || it.isBaselined }

        // Format and render console table
        renderConsoleReport(allMethods, displayMethods, violations, warnings, baselined, maxThreshold, warnThresh)

        // Write output report file
        writeReportFile(reportOutput.get().asFile, allMethods, violations, warnings, baselined, maxThreshold)

        if (violations.isNotEmpty()) {
            val sb = StringBuilder()
            sb.appendLine("❌ CRAP check failed! Found ${violations.size} unbaselined method(s) exceeding threshold ($maxThreshold):")
            for (v in violations) {
                sb.appendLine("   • [${v.module}] ${v.sourceFile} -> ${v.displayName}: CC=${v.cc}, Cov=${String.format("%.1f", v.coverage * 100)}%, CRAP=${String.format("%.1f", v.crap)}")
            }
            sb.appendLine()
            sb.appendLine("💡 How to fix:")
            sb.appendLine("   1. Add unit test coverage for the uncovered branches of these methods.")
            sb.appendLine("   2. Or refactor complex methods (reduce cyclomatic complexity by extracting smaller functions).")
            sb.appendLine("   3. If this is acknowledged legacy debt, record it in crap-baseline.json.")
            throw GradleException(sb.toString())
        }
    }

    private fun parseReport(
        reportFile: File,
        moduleName: String,
        baseline: Set<String>,
        threshold: Double,
        warnThreshold: Double,
    ): List<MethodScore> {
        val results = mutableListOf<MethodScore>()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(reportFile)
        val classNodes = doc.getElementsByTagName("class")

        for (i in 0 until classNodes.length) {
            val classElem = classNodes.item(i) as? Element ?: continue
            val className = classElem.getAttribute("name")
            val sourceFile = classElem.getAttribute("sourcefilename")

            // Skip test classes, fakes, and Room compiler artifacts
            if (shouldSkipClass(className, sourceFile)) continue

            val methodNodes = classElem.getElementsByTagName("method")
            for (j in 0 until methodNodes.length) {
                val methodElem = methodNodes.item(j) as? Element ?: continue
                val methodName = methodElem.getAttribute("name")

                if (shouldSkipMethod(methodName)) continue

                val counters = parseCounters(methodElem)
                val branch = counters["BRANCH"] ?: (0 to 0) // missed to covered
                val line = counters["LINE"] ?: (0 to 0)
                val inst = counters["INSTRUCTION"] ?: (0 to 0)

                val totalBranches = branch.first + branch.second
                val totalLines = line.first + line.second
                val totalInst = inst.first + inst.second

                if (totalBranches == 0 && totalLines == 0 && totalInst == 0) continue
                if (totalBranches == 0 && totalLines == 0) continue

                val cc = if (totalBranches > 0) (totalBranches / 2) + 1 else 1
                val cov = when {
                    totalLines > 0 -> line.second.toDouble() / totalLines
                    totalInst > 0 -> inst.second.toDouble() / totalInst
                    else -> 1.0
                }

                val crap = (cc.toDouble() * cc.toDouble()) * Math.pow(1.0 - cov, 3.0) + cc.toDouble()
                val displayName = formatMethodName(className, methodName)

                val isBaselined = isMethodBaselined(baseline, className, methodName)
                val status = when {
                    crap > threshold && isBaselined -> Status.BASELINED
                    crap > threshold -> Status.DANGER
                    crap > warnThreshold -> Status.WARN
                    else -> Status.OK
                }

                results.add(
                    MethodScore(
                        module = moduleName,
                        sourceFile = sourceFile,
                        className = className,
                        methodName = methodName,
                        displayName = displayName,
                        cc = cc,
                        coveredLines = line.second,
                        totalLines = totalLines,
                        coverage = cov,
                        crap = crap,
                        isBaselined = isBaselined,
                        status = status,
                    )
                )
            }
        }
        return results
    }

    private fun parseCounters(methodElem: Element): Map<String, Pair<Int, Int>> {
        val map = mutableMapOf<String, Pair<Int, Int>>()
        val counterNodes = methodElem.getElementsByTagName("counter")
        for (k in 0 until counterNodes.length) {
            val counter = counterNodes.item(k) as? Element ?: continue
            val type = counter.getAttribute("type")
            val missed = counter.getAttribute("missed").toIntOrNull() ?: 0
            val covered = counter.getAttribute("covered").toIntOrNull() ?: 0
            map[type] = missed to covered
        }
        return map
    }

    private fun shouldSkipClass(className: String, sourceFile: String): Boolean {
        if (sourceFile.endsWith("Test.kt")) return true
        if (sourceFile.startsWith("Fake") || sourceFile.startsWith("Mock")) return true
        if (className.contains("/testing/")) return true
        if (sourceFile.endsWith("_Impl.kt") || className.endsWith("_Impl")) return true
        return false
    }

    private fun shouldSkipMethod(methodName: String): Boolean {
        if (methodName == "<init>" || methodName == "<clinit>") return true
        if (methodName.contains("\$default")) return true
        if (methodName.startsWith("component") && methodName.removePrefix("component").toIntOrNull() != null) return true
        if (methodName in listOf("copy", "equals", "hashCode", "toString")) return true
        return false
    }

    private fun formatMethodName(className: String, methodName: String): String {
        val simpleCls = className.substringAfterLast('/')
        val suspendMatch = Regex("""^([^$]+)\$([^$]+)\$\d+$""").find(simpleCls)
        if (suspendMatch != null && methodName == "invokeSuspend") {
            return "${suspendMatch.groupValues[1]}::${suspendMatch.groupValues[2]} [suspend]"
        }
        val innerMatch = Regex("""^([^$]+)\$([^$]+)$""").find(simpleCls)
        if (innerMatch != null) {
            return "${innerMatch.groupValues[1]}.${innerMatch.groupValues[2]}::$methodName"
        }
        return "$simpleCls::$methodName"
    }

    private fun isMethodBaselined(baseline: Set<String>, className: String, methodName: String): Boolean {
        val full = "$className::$methodName"
        val simple = "${className.substringAfterLast('/')}::$methodName"
        val suspendRoot = if (className.contains('$')) "${className.substringBefore('$')}::$methodName" else null
        return baseline.contains(full) || baseline.contains(simple) || (suspendRoot != null && baseline.contains(suspendRoot))
    }

    private fun parseBaseline(file: File): Set<String> {
        val text = file.readText()
        val objectRegex = Regex("""\{[^{}]*\}""")
        val classRegex = Regex(""""class"\s*:\s*"([^"]+)"""")
        val methodRegex = Regex(""""method"\s*:\s*"([^"]+)"""")
        val set = mutableSetOf<String>()
        for (obj in objectRegex.findAll(text)) {
            val block = obj.value
            val c = classRegex.find(block)?.groupValues?.get(1)?.trim()
            val m = methodRegex.find(block)?.groupValues?.get(1)?.trim()
            if (c != null && m != null) {
                set.add("$c::$m")
                set.add("${c.substringAfterLast('/')}::$m")
            }
        }
        return set
    }

    private fun deriveModuleName(reportFile: File): String {
        val path = reportFile.absolutePath.replace(File.separatorChar, '/')
        val coreMatch = Regex("""/core/([^/]+)/""").find(path)
        if (coreMatch != null) return ":core:${coreMatch.groupValues[1]}"
        val featureMatch = Regex("""/feature/([^/]+)/""").find(path)
        if (featureMatch != null) return ":feature:${featureMatch.groupValues[1]}"
        return reportFile.parentFile?.parentFile?.name ?: "unknown"
    }

    private fun renderConsoleReport(
        allMethods: List<MethodScore>,
        displayMethods: List<MethodScore>,
        violations: List<MethodScore>,
        warnings: List<MethodScore>,
        baselined: List<MethodScore>,
        threshold: Double,
        warnThreshold: Double,
    ) {
        val reset = "\u001B[0m"
        val bold = "\u001B[1m"
        val green = "\u001B[32m"
        val yellow = "\u001B[33m"
        val red = "\u001B[31;1m"
        val magenta = "\u001B[35m"

        println()
        val header = String.format("%-50s | %-4s | %-14s | %-6s | %-10s", "METHOD", "CC", "COVERAGE", "CRAP", "STATUS")
        println("$bold$header$reset")
        println("-".repeat(92))

        for (m in displayMethods) {
            val covStr = if (m.totalLines > 0) {
                "${m.coveredLines}/${m.totalLines} (${String.format("%4.1f", m.coverage * 100)}%)"
            } else {
                String.format("%4.1f%%", m.coverage * 100)
            }
            val methodStr = if (m.displayName.length > 48) m.displayName.take(45) + "..." else m.displayName

            val (color, statusStr) = when (m.status) {
                Status.DANGER -> red to "DANGER"
                Status.BASELINED -> magenta to "BASELINED"
                Status.WARN -> yellow to "WARN"
                Status.OK -> green to "OK"
            }

            val row = String.format("%-50s | %-4d | %-14s | %-6.1f | %-10s", methodStr, m.cc, covStr, m.crap, statusStr)
            println("$color$row$reset")
        }

        println("-".repeat(92))
        println(
            "Summary: Total ${allMethods.size} methods evaluated. " +
                "$green${allMethods.size - warnings.size - violations.size - baselined.size} OK$reset, " +
                "$yellow${warnings.size} WARN (> $warnThreshold)$reset, " +
                "$magenta${baselined.size} BASELINED$reset, " +
                "$red${violations.size} DANGER (> $threshold)$reset\n"
        )
    }

    private fun writeReportFile(
        outputFile: File,
        allMethods: List<MethodScore>,
        violations: List<MethodScore>,
        warnings: List<MethodScore>,
        baselined: List<MethodScore>,
        threshold: Double,
    ) {
        outputFile.parentFile?.mkdirs()
        val sb = StringBuilder()
        sb.appendLine("CRAP Score Report")
        sb.appendLine("Generated at: ${java.time.Instant.now()}")
        sb.appendLine("Threshold: $threshold")
        sb.appendLine("Total evaluated: ${allMethods.size}")
        sb.appendLine("Violations: ${violations.size}")
        sb.appendLine("Warnings: ${warnings.size}")
        sb.appendLine("Baselined: ${baselined.size}")
        sb.appendLine()
        sb.appendLine(String.format("%-50s | %-4s | %-14s | %-6s | %-10s", "METHOD", "CC", "COVERAGE", "CRAP", "STATUS"))
        sb.appendLine("-".repeat(92))
        for (m in allMethods) {
            val covStr = if (m.totalLines > 0) "${m.coveredLines}/${m.totalLines} (${String.format("%.1f", m.coverage * 100)}%)" else "${String.format("%.1f", m.coverage * 100)}%"
            sb.appendLine(String.format("%-50s | %-4d | %-14s | %-6.1f | %-10s", m.displayName.take(50), m.cc, covStr, m.crap, m.status.name))
        }
        outputFile.writeText(sb.toString())
    }
}
