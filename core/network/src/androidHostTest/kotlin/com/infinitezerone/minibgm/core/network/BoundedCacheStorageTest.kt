package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 容量契约：HTTP 缓存只按字节预算淘汰最久未写条目，**不做时间判断**。
 * 新鲜度由服务端 `Cache-Control` / `ETag` 决定，这里如果再算 TTL 就会变成两套真值。
 */
class BoundedCacheStorageTest {
    private val dir = Files.createTempDirectory("minibgm_bounded_cache_test").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private class NoopStorage : CacheStorage {
        override suspend fun store(
            url: Url,
            data: CachedResponseData,
        ) = Unit

        override suspend fun find(
            url: Url,
            varyKeys: Map<String, String>,
        ): CachedResponseData? = null

        override suspend fun findAll(url: Url): Set<CachedResponseData> = emptySet()

        override suspend fun remove(
            url: Url,
            varyKeys: Map<String, String>,
        ) = Unit

        override suspend fun removeAll(url: Url) = Unit
    }

    private fun responseData() =
        CachedResponseData(
            url = Url("https://api.example.com/comment/1"),
            statusCode = HttpStatusCode.OK,
            requestTime = GMTDate(),
            responseTime = GMTDate(),
            version = HttpProtocolVersion.HTTP_1_1,
            expires = GMTDate(),
            headers = Headers.Empty,
            varyKeys = emptyMap(),
            body = ByteArray(0),
        )

    @Test
    fun `超出字节预算时淘汰最久未写条目`() =
        runTest {
            val oldest =
                File(dir, "oldest").apply {
                    writeBytes(ByteArray(60))
                    setLastModified(1_000L)
                }
            val newer =
                File(dir, "newer").apply {
                    writeBytes(ByteArray(60))
                    setLastModified(2_000L)
                }
            val storage = BoundedCacheStorage(NoopStorage(), dir, maxBytes = 100)

            storage.store(Url("https://api.example.com/comment/2"), responseData())

            assertFalse(oldest.exists(), "超出预算时必须先淘汰最久未写的条目")
            assertTrue(newer.exists(), "预算内的条目必须保留")
        }

    @Test
    fun `预算内不淘汰任何条目`() =
        runTest {
            val first =
                File(dir, "first").apply {
                    writeBytes(ByteArray(40))
                    setLastModified(1_000L)
                }
            val second =
                File(dir, "second").apply {
                    writeBytes(ByteArray(40))
                    setLastModified(2_000L)
                }
            val storage = BoundedCacheStorage(NoopStorage(), dir, maxBytes = 100)

            storage.store(Url("https://api.example.com/comment/3"), responseData())

            assertTrue(first.exists())
            assertTrue(second.exists())
        }
}
