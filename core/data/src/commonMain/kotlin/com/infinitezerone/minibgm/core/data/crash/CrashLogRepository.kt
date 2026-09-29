package com.infinitezerone.minibgm.core.data.crash

/**
 * 一次崩溃记录。
 *
 * [content] 是完整可读文本（时间、版本、机型、线程、堆栈），不是裸的异常对象——
 * 它要能被用户直接复制粘贴进 Issue，所以在这里就渲染好。
 */
data class CrashLog(
    /** 崩溃发生时刻（epoch 毫秒） */
    val occurredAtMillis: Long,
    val content: String,
)

/**
 * 崩溃日志仓库：读取与清空由 `CrashLogRecorder` 落盘的未捕获异常堆栈。
 *
 * **存在的理由**：release 包经 R8 混淆，用户提交的堆栈里只有 `a.b.c`——没有 mapping
 * 就完全读不了；而本项目没有接入任何崩溃收集 SDK，也没有导出日志的入口。
 * 于是"崩溃时把堆栈留在本地、由用户在设置页导出"是唯一能让堆栈到达开发者手里的通道，
 * 也是 release 页上那份 `mapping.txt` 真正被用上的地方。
 *
 * 只读 + 清空，没有"写入"——写入发生在崩溃线程上，不能是挂起函数，由记录器直接落盘。
 */
interface CrashLogRepository {
    /** 最近一次崩溃记录；从未崩溃过则 null */
    suspend fun latest(): CrashLog?

    /** 清空全部崩溃记录 */
    suspend fun clear()
}
