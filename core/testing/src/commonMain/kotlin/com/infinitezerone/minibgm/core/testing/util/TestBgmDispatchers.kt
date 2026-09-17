package com.infinitezerone.minibgm.core.testing.util

import com.infinitezerone.minibgm.core.common.BgmDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * 构造用于单元测试的 [BgmDispatchers]，默认全部分配给 [testDispatcher]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testBgmDispatchers(testDispatcher: TestDispatcher = UnconfinedTestDispatcher()): BgmDispatchers =
    BgmDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
