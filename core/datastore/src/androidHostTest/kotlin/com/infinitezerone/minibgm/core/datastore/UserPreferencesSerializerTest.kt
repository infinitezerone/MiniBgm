package com.infinitezerone.minibgm.core.datastore

import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserPreferencesSerializerTest {
    @Test
    fun defaultValue_isNotLoggedIn() {
        val default = UserPreferencesSerializer.defaultValue
        assertFalse(default.isLoggedIn)
        assertEquals(0L, default.activeUserId)
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
                    activeUserId = 42L,
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
                    isLoggedIn = true,
                    pendingOAuthVerifier = "test_verifier",
                    isDarkMode = true,
                    notifyBeforeAirMinutes = 30,
                )

            val outputStream = ByteArrayOutputStream()
            UserPreferencesSerializer.writeTo(original, outputStream)

            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
            val readBack = UserPreferencesSerializer.readFrom(inputStream)

            assertEquals(original, readBack)
            assertTrue(readBack.isLoggedIn)
            assertEquals("infinitezerone", readBack.activeProfile?.username)
        }

    @Test
    fun readFrom_legacyJsonWithUnknownKeys_dropsLegacyAccountFields() =
        runTest {
            // 旧版本 JSON 带已删除的平铺账号字段：读取时忽略未知键，不视为损坏
            val legacyJson =
                """{"activeUserId":42,"isLoggedIn":true,"userId":42,"username":"old","nickname":"旧","isDarkMode":true}"""
            val result = UserPreferencesSerializer.readFrom(ByteArrayInputStream(legacyJson.encodeToByteArray()))

            assertTrue(result.isLoggedIn)
            assertEquals(true, result.isDarkMode)
        }
}
