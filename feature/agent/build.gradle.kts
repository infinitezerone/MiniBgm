import com.infinitezerone.minibgm.androidLibrary

plugins {
    alias(libs.plugins.minibgm.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

androidLibrary {
    namespace = "com.infinitezerone.minibgm.feature.agent"
}

dependencies {
    // 工具契约（AgentTool/ToolResult）仍走 MiniAgent；引擎已切换为 Koog
    implementation(libs.miniagent.agent.loop)
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.event.handler)
    // 加密 checkpoint 存储（MiniAgent 复合构建的 Android harness 壳）
    implementation(libs.miniagent.harness.android)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.koog.agents.test)
}
