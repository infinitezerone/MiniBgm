package com.infinitezerone.minibgm.core.data.download

import java.io.File

/**
 * 磁力/BT 种子（`.torrent`）缓存的载荷校验与自愈。
 *
 * 背景：`AssetDownloader` 的完成判据只有"字节数达到 Content-Length"，镜像返回 HTTP 200 的
 * HTML 错误页同样会被判 `Completed` 并原子改名落盘；而缓存命中又只看"文件名 + 非空"，
 * 于是坏种子会被永久复用（离线下载还会把路径写进 DB 的 `torrentFilePath`，重启后继续命中），
 * 表现为稳定复现的"解析种子元数据失败"，用户唯一出路是清应用数据。
 *
 * 因此**命中缓存与下载落盘两个入口都必须校验载荷**，校验失败即删除并重下（自愈），
 * 缓存键也不得只按标题命名（同名不同种会永久互串）。
 */
internal object TorrentFileCache {
    /**
     * 校验只需覆盖文件前部：bencode 字典的键出现在数据之前，
     * 即便带上几百条 tracker 的 `announce-list`，`4:info` 键也远在前 1 MiB 内。
     */
    private const val SCAN_BYTES = 1024 * 1024

    private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|]")

    /**
     * 种子载荷结构校验：必须是 bencode 字典（首个非空白字符为 `d`）且含 `4:info` 键。
     *
     * HTML 错误页、JSON 错误体、被截断的半截种子都会在此被拒。
     */
    fun isValidPayload(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return runCatching {
            val limit = minOf(file.length(), SCAN_BYTES.toLong()).toInt()
            val head = ByteArray(limit)
            val read = file.inputStream().use { it.read(head) }
            if (read <= 0) return@runCatching false
            // ISO-8859-1 逐字节映射，保证对二进制段也不丢结构字符
            val text = String(head, 0, read, Charsets.ISO_8859_1)
            val firstMeaningful = text.firstOrNull { !it.isWhitespace() && it != '\uFEFF' }
            firstMeaningful == 'd' && text.contains("4:info")
        }.getOrDefault(false)
    }

    /**
     * 命中缓存时的校验入口：坏种子立即删除并返回 `false`，让调用方重新下载，
     * 杜绝"坏缓存永久命中"（历史上只能靠清应用数据自救）。
     */
    fun isUsableCachedFile(file: File): Boolean {
        if (!file.isFile) return false
        if (isValidPayload(file)) return true
        runCatching { file.delete() }
        return false
    }

    /**
     * 缓存键：优先 `infoHash`（同一磁力必然同一内容），退化到种子直链指纹，最后才用标题。
     *
     * 严禁只按标题命名——同名不同种会永久读到旧种子。
     */
    fun cacheKey(
        magnetUrl: String,
        torrentUrl: String?,
        title: String,
    ): String {
        val infoHash =
            magnetUrl
                .substringAfter("xt=urn:btih:", "")
                .substringBefore("&")
                .trim()
        val raw =
            when {
                infoHash.isNotBlank() -> "hash_${infoHash.lowercase()}"
                !torrentUrl.isNullOrBlank() -> "url_${Integer.toHexString(torrentUrl.hashCode())}"
                else -> "title_${title.trim()}"
            }
        return raw.replace(ILLEGAL_NAME_CHARS, "_").take(120)
    }
}
