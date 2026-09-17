package com.infinitezerone.minibgm.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<UserPreferences> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

    override val defaultValue: UserPreferences = UserPreferences()

    override suspend fun readFrom(input: InputStream): UserPreferences {
        val text = input.readBytes().decodeToString()
        // 空文件（已创建但尚未写入，如首次写入前崩溃遗留 0 字节）按默认值处理，不走 corruption 恢复路径
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(UserPreferences.serializer(), text)
        } catch (exception: IllegalArgumentException) {
            // 只把解析失败视为损坏（SerializationException 继承自 IllegalArgumentException，
            // isLenient 下畸形输入也可能直接抛 IAE）；不放宽到 Throwable，避免连带 Error 一起
            // 触发 corruption handler 清空用户偏好
            throw CorruptionException("Cannot read user preferences.", exception)
        }
    }

    override suspend fun writeTo(
        t: UserPreferences,
        output: OutputStream,
    ) {
        withContext(Dispatchers.IO) {
            output.write(
                json.encodeToString(UserPreferences.serializer(), t).encodeToByteArray(),
            )
        }
    }
}
