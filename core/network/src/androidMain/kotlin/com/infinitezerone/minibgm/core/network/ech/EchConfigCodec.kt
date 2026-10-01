package com.infinitezerone.minibgm.core.network.ech

import java.util.Base64

/**
 * 一份 ECHConfig 里对我们有意义的字段。
 */
internal data class EchConfigFields(
    val version: Int,
    val configId: Int,
    val kemId: Int,
    val publicName: String,
)

/**
 * ECHConfig 的解析与准入校验（RFC 9849 / draft-ietf-tls-esni ECHConfigContents）。
 *
 * 结构（逐层长度前缀，必须按字段宽度推进，不能搜索特征串）：
 * ```
 * ECHConfigList { uint16 length; ECHConfig configs<1..>; }
 * ECHConfig     { uint16 version; uint16 length; ECHConfigContents contents; }
 * ECHConfigContents {
 *     HpkeKeyConfig key_config;   // uint8 config_id; uint16 kem_id; opaque public_key<1..2^16-1>;
 *                                 // HpkeSymmetricCipherSuite cipher_suites<4..2^16-4>;
 *     uint8  maximum_name_length;
 *     opaque public_name<1..255>;
 *     Extension extensions<0..2^16-1>;   // 含 mandatory 扩展时不解析内容，只跳过
 * }
 * ```
 *
 * 这里只解析**第一个** ECHConfig。CF 的 HTTPS RR 只发单条，而种子也是单条；解析列表里更多条目会让
 * "用哪一份"变得不确定，而 `public_name` 与 KEM 在所有条目上必然一致，取首条不影响判定。
 *
 * **刻意不解析 extensions**：RFC 规定未识别的 mandatory 扩展（high bit 置位）必须导致整份配置被忽略。
 * 我们不做信任决策、只做字段提取，真正的密码学与扩展处理由 rustls 负责；在这里自行判断 mandatory
 * 反而会引入与 rustls 不一致的第二套语义。
 */
internal object EchConfigCodec {
    /** ECH (draft-18 / RFC 9849) 的 config 版本号。 */
    const val VERSION_DRAFT_18 = 0xfe0d

    /** RFC 9849 强制要求的 HPKE KEM：DHKEM(X25519, HKDF-SHA256)。 */
    const val KEM_X25519_HKDF_SHA256 = 0x0020

    private const val MIN_CONFIG_BYTES = 4
    private const val MAX_PUBLIC_NAME_LENGTH = 255

    /** 纯解析：结构不完整或版本未知时返回 null。不含任何准入判断。 */
    fun parse(configB64: String): EchConfigFields? =
        runCatching {
            val bytes = Base64.getDecoder().decode(configB64.trim())
            if (bytes.size < MIN_CONFIG_BYTES) return@runCatching null

            var cursor = 0
            // list length 字段不参与推进：字节数组的真实长度才是真值，信任它等于接受越界
            cursor += 2
            val version = readUint16(bytes, cursor) ?: return@runCatching null
            cursor += 2
            cursor += 2 // config length
            val configId = bytes.getOrNull(cursor)?.toInt()?.and(0xFF) ?: return@runCatching null
            cursor += 1
            val kemId = readUint16(bytes, cursor) ?: return@runCatching null
            cursor += 2
            val publicKeyLength = readUint16(bytes, cursor) ?: return@runCatching null
            cursor += 2 + publicKeyLength
            val cipherSuitesLength = readUint16(bytes, cursor) ?: return@runCatching null
            cursor += 2 + cipherSuitesLength
            cursor += 1 // maximum_name_length
            val nameLength = bytes.getOrNull(cursor)?.toInt()?.and(0xFF) ?: return@runCatching null
            cursor += 1
            if (nameLength <= 0 || cursor + nameLength > bytes.size) return@runCatching null

            EchConfigFields(
                version = version,
                configId = configId,
                kemId = kemId,
                publicName = bytes.copyOfRange(cursor, cursor + nameLength).decodeToString(),
            )
        }.getOrNull()

    /**
     * 准入校验。拒绝的配置**不允许**进入生效路径，也不允许落盘——一份用不了的配置留在生效位上，
     * 后果是 ECH 静默失效并回退明文 TLS（SNI 外泄），比直接不用它更糟。
     *
     * @return 拒绝原因；null 表示通过。
     */
    fun rejectionReason(fields: EchConfigFields): String? =
        when {
            fields.version != VERSION_DRAFT_18 ->
                "unsupported ECH config version 0x${fields.version.toString(16)}"
            fields.kemId != KEM_X25519_HKDF_SHA256 ->
                "unsupported HPKE KEM 0x${fields.kemId.toString(16)}; RFC 9849 mandates X25519"
            fields.publicName.isBlank() -> "empty public_name"
            fields.publicName.length > MAX_PUBLIC_NAME_LENGTH ->
                "public_name longer than $MAX_PUBLIC_NAME_LENGTH bytes"
            // RFC 9849：public_name 是 DNS 引用标识，被解释为 IPv4 字面量时必须拒绝
            isIpv4Literal(fields.publicName) ->
                "public_name ${fields.publicName} would be interpreted as an IPv4 address"
            else -> null
        }

    /** 解析 + 校验一步到位；任一环节不过即返回 null。 */
    fun parseAndValidate(configB64: String): EchConfigFields? {
        val fields = parse(configB64) ?: return null
        return fields.takeIf { rejectionReason(it) == null }
    }

    private fun isIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255 }
    }

    private fun readUint16(
        bytes: ByteArray,
        offset: Int,
    ): Int? {
        if (offset < 0 || offset + 2 > bytes.size) return null
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }
}
