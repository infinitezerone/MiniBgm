package com.infinitezerone.minibgm.crap

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class CrapCheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {

        val crapCheckTask =
            target.tasks.register<CrapCheckTask>("crapCheck") {
                group = "verification"
                description = "Checks CRAP (Change Risk Anti-Patterns) scores across all KMP modules using Kover reports."
                threshold.convention(30.0)
                warnThreshold.convention(15.0)
                baselineFile.convention(target.rootProject.layout.projectDirectory.file("crap-baseline.json"))
                reportOutput.convention(target.layout.buildDirectory.file("reports/crap/crap-report.txt"))
                target.rootProject.subprojects {
                    val subproject = this
                    dependsOn(subproject.tasks.matching { it.name == "koverXmlReportAndroid" })
                    koverReports.from(subproject.layout.buildDirectory.file("reports/kover/reportAndroid.xml"))
                }
            }
    }
}
