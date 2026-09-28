package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reg_time 映射为可空注册时间并派生出注册年份`() {
        val profile =
            json.decodeFromString<UserProfile>(
                """{"id":42,"username":"infinitezerone","reg_time":"2015-03-01T00:00:00+08:00"}""",
            )

        assertEquals("2015-03-01T00:00:00+08:00", profile.regTime)
        assertEquals(2015, profile.registeredYear)
    }

    @Test
    fun `响应缺少 reg_time 时注册时间与年份均为空`() {
        // 旧版本落盘的账号、或服务端后续不再返回该字段，都必须走这条分支
        val profile = json.decodeFromString<UserProfile>("""{"id":42,"username":"infinitezerone"}""")

        assertNull(profile.regTime)
        assertNull(profile.registeredYear)
    }

    @Test
    fun `注册年份兼容多种日期形态`() {
        assertEquals(2015, UserProfile(regTime = "2015-03-01").registeredYear)
        assertEquals(2016, UserProfile(regTime = "2016/07/09 12:30").registeredYear)
        assertEquals(2011, UserProfile(regTime = "2011-12-31T23:59:59Z").registeredYear)
    }

    @Test
    fun `注册年份取不到合理区间时为空而不做兜底猜测`() {
        assertNull(UserProfile(regTime = null).registeredYear)
        assertNull(UserProfile(regTime = "").registeredYear)
        assertNull(UserProfile(regTime = "unknown").registeredYear)
        assertNull(UserProfile(regTime = "0000-01-01").registeredYear)
        assertNull(UserProfile(regTime = "9999-01-01").registeredYear)
    }
}
