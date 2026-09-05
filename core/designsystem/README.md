# `:core:designsystem`

## 🎯 模块职责
应用的 UI 基础设计系统，基于 Jetpack Compose 与 Material 3 Expressive 构建。提供全局主题（`MiniBgmTheme`）、调色板、排版规范、通用图标与基础通用原子组件（如 `CoverImage`）。

## 🏛️ 依赖关系
* **依赖的上游**：Jetpack Compose、Material 3 与 Coil；不依赖任何项目数据模块
* **被谁依赖**：所有 `:feature:*` 模块, `:app`
* **禁止依赖**：禁止依赖 `:core:data`, `:core:network`, `:core:database` 等数据基础设施模块。

## ⚠️ 架构红线与约束
1. 纯 UI 表现层模块，严禁直接依赖数据仓库层或网络/存储基础设施。
2. 所有组件与色彩必须遵循 Material 3 Expressive 设计规范，支持 Android 12+ 动态取色（Dynamic Color）与深浅色模式自适应。
