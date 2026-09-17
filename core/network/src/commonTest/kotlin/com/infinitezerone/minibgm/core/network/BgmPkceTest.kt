package com.infinitezerone.minibgm.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BgmPkceTest {
    private fun sha256Hex(input: String): String =
        sha256(input.encodeToByteArray()).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun `sha256 官方向量 - 空串`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
    }

    @Test
    fun `sha256 官方向量 - abc`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    @Test
    fun `sha256 官方向量 - 448位消息填充边界`() {
        assertEquals(
            "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
            sha256Hex(
                "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno" +
                    "ijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
            ),
        )
    }

    @Test
    fun `sha256 官方向量 - 896位消息`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun `sha256 官方向量 - 百万a`() {
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            sha256Hex("a".repeat(1_000_000)),
        )
    }

    @Test
    fun `challenge 格式 - v1 前缀加 43 位无 padding base64url`() {
        val challenge = BgmPkce.challenge(BgmPkce.generateVerifier())
        assertTrue(challenge.startsWith("v1."))
        assertEquals(46, challenge.length)
        assertTrue(challenge.substring(3).all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun `challenge 已知向量 - sha256 空串的标准 base64url`() {
        // sha256("") 的 base64url（-/_ 替换、去 padding），由 NIST 向量推出的锚点
        assertEquals("v1.47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU", BgmPkce.challenge(""))
    }

    @Test
    fun `challenge 确定性且区分不同 verifier`() {
        val first = BgmPkce.generateVerifier()
        val second = BgmPkce.generateVerifier()
        assertNotEquals(first, second)
        assertEquals(BgmPkce.challenge(first), BgmPkce.challenge(first))
        assertNotEquals(BgmPkce.challenge(first), BgmPkce.challenge(second))
    }

    @Test
    fun `verifier 为 64 位小写十六进制`() {
        val verifier = BgmPkce.generateVerifier()
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `challenge 不泄露 verifier 原文`() {
        val verifier = BgmPkce.generateVerifier()
        val challenge = BgmPkce.challenge(verifier)
        assertFalse(challenge.contains(verifier, ignoreCase = true))
    }
}
