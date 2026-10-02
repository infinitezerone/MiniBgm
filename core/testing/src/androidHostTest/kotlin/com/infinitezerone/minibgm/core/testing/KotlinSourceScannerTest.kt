package com.infinitezerone.minibgm.core.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSourceScannerTest {
    @Test
    fun comments_and_strings_are_completely_masked_with_line_numbers_preserved() {
        val snippet =
            """
            package com.example
            // import com.infinitezerone.minibgm.core.network.BgmHttpClient
            /*
             * Color(0xFF0000)
             * import io.ktor.client.HttpClient
             */
            val logMessage = "Color(0x123456) in log string"
            val realColor = Color(0xAABBCC)
            """.trimIndent()

        val scanner = KotlinSourceScanner.fromContent(snippet)

        // 1. imports 必须为空（因为唯一的 import 在注释中）
        assertEquals(0, scanner.imports.size)

        // 2. Color(0x 模式匹配应该只能抓到真实的 realColor (行号 8)
        val colorMatches = scanner.findMatches(Regex("""Color\(0x[0-9a-fA-F]+"""))
        assertEquals(1, colorMatches.size)
        assertEquals(8, colorMatches[0].lineNumber)
        assertTrue(colorMatches[0].lineContent.contains("realColor"))

        // 3. rawContent 与 sanitizedContent 长度必须完全一致
        assertEquals(snippet.length, scanner.sanitizedContent.length)
    }

    @Test
    fun nested_block_comments_are_correctly_handled() {
        val snippet =
            """
            /* outer comment
               /* inner nested comment */
               still in comment: HttpURLConnection
            */
            val actual = 42
            """.trimIndent()

        val scanner = KotlinSourceScanner.fromContent(snippet)
        val matches = scanner.containsToken("HttpURLConnection")
        assertEquals(0, matches.size)
    }

    @Test
    fun raw_multiline_strings_are_masked() {
        val snippet =
            """
            val raw = ${"\"\"\""}
                class FakeRoute : NavKey
                Color(0x112233)
            ${"\"\"\""}
            val valid = 1
            """.trimIndent()

        val scanner = KotlinSourceScanner.fromContent(snippet)
        assertEquals(0, scanner.containsToken("Color(0x").size)
        assertEquals(0, scanner.containsToken("NavKey").size)
    }

    @Test
    fun view_model_mutable_state_flow_detection_is_accurate() {
        val snippet =
            """
            class MyViewModel {
                // private val commentState = MutableStateFlow(0)
                private val _privateState = MutableStateFlow(1)
                val publicState = _privateState.asStateFlow()

                // 违规场景 1：单行公开暴露
                val badState = MutableStateFlow("bad")

                // 违规场景 2：跨行公开暴露
                val multilineBadState:
                    MutableStateFlow<Int> = MutableStateFlow(42)
            }
            """.trimIndent()

        val scanner = KotlinSourceScanner.fromContent(snippet)
        val violations = scanner.findExposedMutableStateFlows()

        // 只有 badState 和 multilineBadState 应该被识别为违规
        // commentState 在注释中，_privateState 是 private，publicState 是 asStateFlow()
        assertEquals(2, violations.size)
        assertEquals(7, violations[0].lineNumber)
        assertTrue(violations[0].lineContent.contains("badState"))
        assertEquals(10, violations[1].lineNumber)
        assertTrue(violations[1].lineContent.contains("multilineBadState"))
    }
}
