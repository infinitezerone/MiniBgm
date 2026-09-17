package com.infinitezerone.minibgm.core.network

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/**
 * Worker 侧强制的 PKCE 等价物（bgm.tv 不支持标准 PKCE，机制见 worker/README.md）。
 *
 * - `beginLogin` 生成随机 [generateVerifier]，其 [challenge]（前缀 + base64url(sha256)）
 *   作为 OAuth state 明文进入授权 URL——指纹公开；
 * - verifier 仅存 App 本地（UserPreferences），兑换时随 code 经 HTTPS 交给 Worker，
 *   Worker 重算 sha256(verifier) 与回调 state 比对，不匹配即拒绝。
 * 抢到回调的攻击者只有 code + 指纹，无 verifier 无法通过校验（sha256 单向）。
 * 与 Keystore 存储一致，以"攻击者未 root 设备"为前提；root/Frida 层面超出客户端防御范围。
 */
object BgmPkce {
    /** state 版本前缀：区分方案版本，也防与旧版纯随机 state 混淆 */
    const val CHALLENGE_PREFIX = "v1."

    /** ≥256 位随机：两次 [Uuid.random]（底层 SecureRandom，128 位 × 2，满足 CSRF + 防暴破） */
    fun generateVerifier(): String = Uuid.random().toHexString() + Uuid.random().toHexString()

    /** 公开指纹：`v1.` + base64url(sha256(verifier))，无 padding，43 字符 */
    @OptIn(ExperimentalEncodingApi::class)
    fun challenge(verifier: String): String =
        CHALLENGE_PREFIX +
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(sha256(verifier.encodeToByteArray()))
}

/** RFC 6234 SHA-256 纯 Kotlin 实现（KMP 无官方 crypto 库）；正确性由官方测试向量保证，见 BgmPkceTest */
internal fun sha256(message: ByteArray): ByteArray {
    val k =
        intArrayOf(
            0x428a2f98u.toInt(),
            0x71374491u.toInt(),
            0xb5c0fbcfu.toInt(),
            0xe9b5dba5u.toInt(),
            0x3956c25bu.toInt(),
            0x59f111f1u.toInt(),
            0x923f82a4u.toInt(),
            0xab1c5ed5u.toInt(),
            0xd807aa98u.toInt(),
            0x12835b01u.toInt(),
            0x243185beu.toInt(),
            0x550c7dc3u.toInt(),
            0x72be5d74u.toInt(),
            0x80deb1feu.toInt(),
            0x9bdc06a7u.toInt(),
            0xc19bf174u.toInt(),
            0xe49b69c1u.toInt(),
            0xefbe4786u.toInt(),
            0x0fc19dc6u.toInt(),
            0x240ca1ccu.toInt(),
            0x2de92c6fu.toInt(),
            0x4a7484aau.toInt(),
            0x5cb0a9dcu.toInt(),
            0x76f988dau.toInt(),
            0x983e5152u.toInt(),
            0xa831c66du.toInt(),
            0xb00327c8u.toInt(),
            0xbf597fc7u.toInt(),
            0xc6e00bf3u.toInt(),
            0xd5a79147u.toInt(),
            0x06ca6351u.toInt(),
            0x14292967u.toInt(),
            0x27b70a85u.toInt(),
            0x2e1b2138u.toInt(),
            0x4d2c6dfcu.toInt(),
            0x53380d13u.toInt(),
            0x650a7354u.toInt(),
            0x766a0abbu.toInt(),
            0x81c2c92eu.toInt(),
            0x92722c85u.toInt(),
            0xa2bfe8a1u.toInt(),
            0xa81a664bu.toInt(),
            0xc24b8b70u.toInt(),
            0xc76c51a3u.toInt(),
            0xd192e819u.toInt(),
            0xd6990624u.toInt(),
            0xf40e3585u.toInt(),
            0x106aa070u.toInt(),
            0x19a4c116u.toInt(),
            0x1e376c08u.toInt(),
            0x2748774cu.toInt(),
            0x34b0bcb5u.toInt(),
            0x391c0cb3u.toInt(),
            0x4ed8aa4au.toInt(),
            0x5b9cca4fu.toInt(),
            0x682e6ff3u.toInt(),
            0x748f82eeu.toInt(),
            0x78a5636fu.toInt(),
            0x84c87814u.toInt(),
            0x8cc70208u.toInt(),
            0x90befffau.toInt(),
            0xa4506cebu.toInt(),
            0xbef9a3f7u.toInt(),
            0xc67178f2u.toInt(),
        )

    var h =
        intArrayOf(
            0x6a09e667u.toInt(),
            0xbb67ae85u.toInt(),
            0x3c6ef372u.toInt(),
            0xa54ff53au.toInt(),
            0x510e527fu.toInt(),
            0x9b05688cu.toInt(),
            0x1f83d9abu.toInt(),
            0x5be0cd19u.toInt(),
        )

    // 填充：0x80 + 零至 56 mod 64，再接 8 字节大端比特长度
    val paddedLen = ((message.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedLen)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    val bitLen = message.size.toLong() * 8
    for (i in 0 until 8) {
        padded[paddedLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
    }

    val w = IntArray(64)

    fun rotr(
        x: Int,
        n: Int,
    ): Int = (x ushr n) or (x shl (32 - n))

    for (blockStart in padded.indices step 64) {
        for (t in 0 until 16) {
            val base = blockStart + 4 * t
            w[t] =
                ((padded[base].toInt() and 0xFF) shl 24) or
                ((padded[base + 1].toInt() and 0xFF) shl 16) or
                ((padded[base + 2].toInt() and 0xFF) shl 8) or
                (padded[base + 3].toInt() and 0xFF)
        }
        for (t in 16 until 64) {
            val s0 = rotr(w[t - 15], 7) xor rotr(w[t - 15], 18) xor (w[t - 15] ushr 3)
            val s1 = rotr(w[t - 2], 17) xor rotr(w[t - 2], 19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }

        var a = h[0]
        var b = h[1]
        var c = h[2]
        var d = h[3]
        var e = h[4]
        var f = h[5]
        var g = h[6]
        var hh = h[7]

        for (t in 0 until 64) {
            val sum1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = hh + sum1 + ch + k[t] + w[t]
            val sum0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + maj

            hh = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += hh
    }

    val digest = ByteArray(32)
    for (i in 0 until 8) {
        val word = h[i]
        for (j in 0 until 4) {
            digest[4 * i + j] = (word ushr (24 - 8 * j)).toByte()
        }
    }
    return digest
}
