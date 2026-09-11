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
