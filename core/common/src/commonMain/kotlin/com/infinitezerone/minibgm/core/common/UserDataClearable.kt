package com.infinitezerone.minibgm.core.common

/**
 * 账号私有数据清理契约接口。
 *
 * 凡是持有特定用户私有缓存（如 Room 收藏表、搜索历史、通知消息、用户偏好等）的 Repository 或 DataSource，
 * 均实现此接口并注册入 DI。在用户登出或注销某个账号时，由 [AuthRepository] 并发调度执行清理，
 * 避免 [AuthRepository] 与各具体业务模块产生硬依赖耦合。
 */
interface UserDataClearable {
    /** 清理指定账号的私有业务数据（单账号注销/退出） */
    suspend fun clearUserData(userId: Long)

    /** 清理所有账号的私有业务数据（全量登出/重置） */
    suspend fun clearAllUserData()
}
