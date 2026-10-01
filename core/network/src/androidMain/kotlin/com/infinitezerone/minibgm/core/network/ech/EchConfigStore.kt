package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import java.io.File

/**
 * ECH 配置种子提供与历史遗留文件清理。
 *
 * ## 架构设计：返璞归真与带内自愈 (In-Band Self-Healing)
 * 1. **初始引导种子 ([DEFAULT_CLOUDFLARE_ECH_CONFIG])**：
 *    提供冷启动首次握手必需的 ECHConfig 结构与 public_name (`cloudflare-ech.com`)。
 * 2. **协议原生带内自愈 (RFC 9849 §6.1.6)**：
 *    当 Cloudflare 轮换密钥时，服务端会在 TLS 握手阶段下发权威的 `retry_configs`。
 *    底层 Rust 客户端直接在当次连接内部瞬时短路并完成重试，建立 HTTP/2 长连接。
 * 3. **连接池复用 (HTTP/2 Multiplexing)**：
 *    建立成功的连接注入 Hyper 1.0 连接池（TTL 120s），后续业务并发请求直接复用已建好的 Stream，
 *    零握手延迟，完全摆脱对脆弱外部 DoH 的硬编码依赖与自举死锁。
 * 4. **RFC 9849 §6.1.6 绝对合规**：
 *    服务端下发的 `retry_configs` 仅用于 Rust 底层单连接重试，绝不回填全局内存、绝不跨连接复用、
 *    绝不落盘持久化，彻底杜绝明文 `config_id` 追踪向量（Tracking Vector）。
 */
internal object EchConfigStore {
    /** 内置引导配置（Cloudflare，public_name = cloudflare-ech.com）。 */
    const val DEFAULT_CLOUDFLARE_ECH_CONFIG: String =
        "AEX+DQBBIQAgACDepu5XFhjNsTaJykFP5aC6eLhW+KKUXtgKGuWt2c26QgAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA="

    /** 旧版本按 host 落盘的 retry 派生配置前缀。 */
    private const val LEGACY_CONFIG_PREFIX = "ech_active_config_"

    /** 短暂存在过的 HTTPS RR 派生缓存前缀（该通道已撤除）。 */
    private const val LEGACY_DNS_PREFIX = "ech_dns_config_"

    private const val FILE_SUFFIX = ".txt"

    private val logger = bgmLogger("Bgm/EchConfigStore")

    private val seedPublicName: String? by lazy { EchConfigCodec.parse(DEFAULT_CLOUDFLARE_ECH_CONFIG)?.publicName }

    fun init(filesDir: File) {
        purgeLegacyConfigFiles(filesDir)
    }

    /** 当前生效的引导配置。 */
    fun getActiveConfig(): String = DEFAULT_CLOUDFLARE_ECH_CONFIG

    /** 当前生效配置声明的 public_name（外层 SNI）。 */
    val activePublicName: String?
        get() = seedPublicName

    /**
     * 清除历史落盘配置。
     *
     * 必须**删除**而不是"读进来兼容"：这些字节在旧版本里来自 retry_configs，继续沿用等于把
     * RFC 明令禁止持久化的数据带进新版本。
     */
    private fun purgeLegacyConfigFiles(filesDir: File) {
        val legacyFiles =
            filesDir.listFiles { file ->
                file.name.endsWith(FILE_SUFFIX) &&
                    (file.name.startsWith(LEGACY_CONFIG_PREFIX) || file.name.startsWith(LEGACY_DNS_PREFIX))
            } ?: return
        legacyFiles.forEach { file ->
            if (file.delete()) {
                logger.i { "[ECH_CONFIG:PURGE] removed persisted ECH config ${file.name}" }
            } else {
                logger.w { "[ECH_CONFIG:PURGE] failed to remove ${file.name}" }
            }
        }
    }
}
