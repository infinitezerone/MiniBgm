package com.infinitezerone.minibgm.core.network.ech

import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * ECHConfig 解析与准入校验。
 *
 * 校验规则不是自选的：版本与 KEM 来自 RFC 9849 的强制要求，public_name 拒绝 IPv4 字面量同样出自
 * 规范（public_name 是 DNS 引用标识，被解释成 IP 时必须拒绝）。
 */
class EchConfigCodecTest {
    @Test
    fun parses_the_seeded_cloudflare_config() {
        val fields = EchConfigCodec.parseAndValidate(EchConfigStore.DEFAULT_CLOUDFLARE_ECH_CONFIG)

        assertNotNull(fields)
        assertEquals(EchConfigCodec.VERSION_DRAFT_18, fields.version)
        assertEquals(EchConfigCodec.KEM_X25519_HKDF_SHA256, fields.kemId)
        assertEquals("cloudflare-ech.com", fields.publicName)
        assertEquals(253, fields.configId)
    }

    @Test
    fun parses_the_config_cloudflare_publishes_in_dns_today() {
        // 取自 cloudflare-ech.com / crypto.cloudflare.com 的 HTTPS RR（两者 ech 值一致）
        val published =
            "AEX+DQBBSwAgACCSnk0m7tsUp6JZ0lQeHADjq8jqHICYYFZRDF9ff8PXVgAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA="

        val fields = EchConfigCodec.parseAndValidate(published)

        assertNotNull(fields)
        // 与种子同为 cloudflare-ech.com，但 configId 不同（75 vs 93）——DNS 通道给的才是当前那份
        assertEquals("cloudflare-ech.com", fields.publicName)
        assertEquals(75, fields.configId)
    }

    @Test
    fun parses_a_config_whose_public_name_is_not_cloudflares() {
        val tlsEch =
            "AEn+DQBFKwAgACABWIHUGj4u+PIggYXcR5JF0gYk3dCRioBW8uJq9H4mKAAIAAEAAQABAANAEnB1YmxpYy50bHMtZWNoLmRldgAA"

        val fields = EchConfigCodec.parseAndValidate(tlsEch)

        assertNotNull(fields)
        assertEquals("public.tls-ech.dev", fields.publicName)
    }

    @Test
    fun rejects_an_unsupported_config_version() {
        assertNull(EchConfigCodec.parseAndValidate(buildConfig(version = 0xabcd, publicName = "example.com")))
    }

    @Test
    fun rejects_an_unsupported_hpke_kem() {
        // RFC 9849 只强制 X25519；P-256(0x0010)/others 不予采纳
        assertNull(EchConfigCodec.parseAndValidate(buildConfig(kem = 0x0010, publicName = "example.com")))
    }

    @Test
    fun rejects_a_public_name_that_would_be_read_as_an_ipv4_address() {
        assertNull(EchConfigCodec.parseAndValidate(buildConfig(publicName = "203.0.113.7")))
    }

    @Test
    fun rejects_a_blank_public_name() {
        assertNull(EchConfigCodec.parseAndValidate(buildConfig(publicName = "")))
    }

    @Test
    fun rejects_a_truncated_config() {
        val truncated = Base64.getDecoder().decode(EchConfigStore.DEFAULT_CLOUDFLARE_ECH_CONFIG).copyOf(8)
        assertNull(EchConfigCodec.parseAndValidate(Base64.getEncoder().encodeToString(truncated)))
    }

    @Test
    fun rejects_input_that_is_not_base64() {
        assertNull(EchConfigCodec.parseAndValidate("not a base64 config !!!"))
        assertNull(EchConfigCodec.parseAndValidate(""))
    }

    /** 按 ECHConfigList 的线格式拼一份可控配置，用来精确命中各条拒绝分支。 */
    private fun buildConfig(
        version: Int = EchConfigCodec.VERSION_DRAFT_18,
        kem: Int = EchConfigCodec.KEM_X25519_HKDF_SHA256,
        configId: Int = 7,
        publicName: String,
    ): String {
        val name = publicName.encodeToByteArray()
        val contents =
            ByteArrayOutputStream()
                .apply {
                    write(configId and 0xFF)
                    write((kem shr 8) and 0xFF)
                    write(kem and 0xFF)
                    write(0x00)
                    write(0x02)
                    write(0x01)
                    write(0x02)
                    write(0x00)
                    write(0x04)
                    write(0x00)
                    write(0x01)
                    write(0x00)
                    write(0x01)
                    write(0x0A)
                    write(name.size and 0xFF)
                    write(name)
                    write(0x00)
                    write(0x00)
                }.toByteArray()
        val config =
            ByteArrayOutputStream()
                .apply {
                    write((version shr 8) and 0xFF)
                    write(version and 0xFF)
                    write((contents.size shr 8) and 0xFF)
                    write(contents.size and 0xFF)
                    write(contents)
                }.toByteArray()
        val list =
            ByteArrayOutputStream()
                .apply {
                    write((config.size shr 8) and 0xFF)
                    write(config.size and 0xFF)
                    write(config)
                }.toByteArray()
        return Base64.getEncoder().encodeToString(list)
    }
}
