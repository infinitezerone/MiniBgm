package com.infinitezerone.minibgm.core.datastore

import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserPreferencesSerializerTest {
    @Test
    fun defaultValue_hasNoSessionFields() {
        val default = UserPreferencesSerializer.defaultValue
        assertTrue(default.savedProfiles.isEmpty())
        assertEquals("", default.pendingOAuthVerifier)
    }

    @Test
    fun readFrom_emptyStream_returnsDefaultValue() =
        runTest {
            val emptyStream = ByteArrayInputStream(byteArrayOf())
            val result = UserPreferencesSerializer.readFrom(emptyStream)
            assertEquals(UserPreferencesSerializer.defaultValue, result)
        }

    @Test
    fun writeAndRead_preservesUserPreferences() =
        runTest {
            val original =
                UserPreferences(
                    savedProfiles =
                        mapOf(
                            42L to
                                UserProfile(
                                    id = 42L,
                                    username = "infinitezerone",
                                    nickname = "零一",
                                    sign = "Testing",
                                ),
                        ),
                    pendingOAuthVerifier = "test_verifier",
                    isDarkMode = true,
                    notifyBeforeAirMinutes = 30,
                )

            val outputStream = ByteArrayOutputStream()
            UserPreferencesSerializer.writeTo(original, outputStream)

            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
            val readBack = UserPreferencesSerializer.readFrom(inputStream)

            assertEquals(original, readBack)
            assertEquals("infinitezerone", readBack.savedProfiles[42L]?.username)
        }

    @Test
    fun readFrom_legacyJsonWithSessionFields_dropsSessionFields() =
        runTest {
            // 旧版本 JSON 带已删除的会话字段（activeUserId/isLoggedIn）：读取时忽略
            // 未知键，不视为损坏——会话事实自本版本起唯一由凭据库承载
            val legacyJson =
                """{"activeUserId":42,"isLoggedIn":true,"savedProfiles":{"42":{"id":42,"username":"old","nickname":"旧"}},"isDarkMode":true}"""
            val result = UserPreferencesSerializer.readFrom(ByteArrayInputStream(legacyJson.encodeToByteArray()))

            assertEquals(1, result.savedProfiles.size)
            assertEquals("旧", result.savedProfiles[42L]?.nickname)
            assertEquals(true, result.isDarkMode)
        }
}
