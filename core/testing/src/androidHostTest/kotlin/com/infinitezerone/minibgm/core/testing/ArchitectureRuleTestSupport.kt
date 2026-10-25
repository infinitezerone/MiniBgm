package com.infinitezerone.minibgm.core.testing

import java.io.File

/**
 * 域规则测试共享基座：工程根定位、生产源码目录过滤、跨域共用的小工具。
 * 规则本体按域拆分在 *RulesTest.kt；本类只承载被两个以上域共享的成员。
 */
abstract class ArchitectureRuleTestSupport {
    protected val projectRoot: File by lazy {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        current ?: error("无法定位工程根目录（settings.gradle.kts 未找到），当前路径: ${File(".").canonicalPath}")
    }

    protected fun mentionsInsideBlock(
        code: String,
        blockToken: String,
        inner: String,
    ): Boolean {
        var searchFrom = 0
        while (true) {
            val start = code.indexOf(blockToken, searchFrom)
            if (start < 0) return false
            val open = code.indexOf('{', start + blockToken.length)
            if (open < 0) return false
            var depth = 0
            var index = open
            while (index < code.length) {
                when (code[index]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                index++
            }
            val block = code.substring(open, minOf(index + 1, code.length))
            if (block.contains(inner)) return true
            searchFrom = index + 1
        }
    }

    companion object {
        /** 红线只约束生产源码：build 产物与各测试源集目录一律不下钻 */
        val NON_SOURCE_DIR_NAMES =
            setOf("build", "test", "androidTest", "androidHostTest", "androidDeviceTest", "commonTest")
    }
}
