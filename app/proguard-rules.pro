# kotlinx.serialization：保留 @Serializable 类生成的 serializer
# （kotlinx-serialization 自带 consumer rules，以下为官方推荐的兜底规则）
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.infinitezerone.minibgm.**$$serializer { *; }
-keepclassmembers class com.infinitezerone.minibgm.** {
    *** Companion;
}
-keepclasseswithmembers class com.infinitezerone.minibgm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Kotlinx Coroutines / Ktor / Koin / Compose 均自带 consumer rules，无需额外配置。
# 若 release 出现 Missing class 警告，再按 -dontwarn 原则逐条补充，不做全量抑制。


# BgmModalBottomSheet 自定义 M3 Emphasized Accelerate 动效所需的方法保留
-keepclassmembers class androidx.compose.material3.SheetState {
    void setHideMotionSpec*(androidx.compose.animation.core.FiniteAnimationSpec);
    androidx.compose.animation.core.FiniteAnimationSpec getHideMotionSpec*();
}

# Ktor IntellijIdeaDebugDetector 引用了 Android 缺失的 JVM ManagementFactory
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# Rust ECH 原生库（crates/minibgm-ech / libminibgm_ech.so）JNI 桥接
# EchNativeResponse 由 Rust make_response 反射构造，EchNativeClient 提供 JNI 导出符号。
# 若无显式保留，R8 静态分析会因 Kotlin 侧无 new 构造调用而将 EchNativeResponse 削减为 abstract
# 并移除构造器与字段，导致 JNI 实例化失败，造成 release 包全量网络请求中断。
-keep class com.infinitezerone.minibgm.core.network.ech.EchNativeResponse {
    <init>(...);
    *;
}
-keep class com.infinitezerone.minibgm.core.network.ech.EchNativeClient {
    native <methods>;
    *;
}

