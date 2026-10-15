package com.infinitezerone.minibgm.core.data.download

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JNI 符号绑定红线（见 AGENTS.md 领域规则 & 剧本 8）。
 *
 * Rust 侧导出的符号是扁平的 `Java_com_..._AndroidTorrentStreamEngine_nativeXxx`，
 * 这只有在 native 方法**声明在外层类**时才成立：Kotlin 的 `@JvmStatic` 才会把 `external fun`
 * 的 native 跳板生成到外层类上，`$Companion` 只保留一个普通转发器（JVM 字节码与 DEX 布局一致，
 * 曾用 `javap` / `dexdump` 双向确认）。一旦 `@JvmStatic` 丢失，native 会退回 `$Companion`，
 * 符号随之变成 `..._00024Companion_nativeXxx`，真机调用直接 `UnsatisfiedLinkError` 闪退。
 *
 * 单测跑的是预编译 `.so`，静态扫描也看不到类布局，所以这条绑定必须在 JVM 侧钉死。
 */
class TorrentJniBindingTest {
    private val nativeMethodNames =
        listOf(
            "nativeIsAvailable",
            "nativeStartStream",
            "nativeStopStream",
            "nativeGetStreamStats",
            "nativeGetLastError",
            "nativeGetTargetFilePath",
        )

    @Test
    fun nativeMethodsMustBeDeclaredOnOuterClassForFlatJniSymbols() {
        val engineClass = AndroidTorrentStreamEngine::class.java

        nativeMethodNames.forEach { name ->
            val method = engineClass.declaredMethods.firstOrNull { it.name == name }
            assertTrue(
                method != null,
                "$name 未声明在 ${engineClass.simpleName} 外层类：@JvmStatic 丢失后 native 会落到 Companion，" +
                    "JNI 符号变为 ..._00024Companion_$name，真机 UnsatisfiedLinkError",
            )
            assertTrue(
                Modifier.isNative(method.modifiers) && Modifier.isStatic(method.modifiers),
                "$name 必须是 static native（Rust 侧以 JClass 接参），当前修饰符: ${method.modifiers}",
            )
        }
    }

    @Test
    fun companionObjectMustNotDeclareNativeMethods() {
        val companion = Class.forName("com.infinitezerone.minibgm.core.data.download.AndroidTorrentStreamEngine\$Companion")
        val companionNatives = companion.declaredMethods.filter { Modifier.isNative(it.modifiers) }.map { it.name }

        assertTrue(
            companionNatives.isEmpty(),
            "native 方法落在 Companion 上（$companionNatives）：JNI 符号会变成 ..._00024Companion_*，" +
                "Rust 侧导出的是外层类扁平符号，真机必然 UnsatisfiedLinkError",
        )
    }
}
