# MiniBgm

<div align="center">

**现代化 Bangumi (bgm.tv) 番组计划 Android 客户端**

放送时间表 · 番剧详情 · 社区吐槽 · 收藏管理 · 搜索发现 · 开播提醒 · 桌面小组件

[![Release](https://img.shields.io/github/v/release/infinitezerone/MiniBgm)](https://github.com/infinitezerone/MiniBgm/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/infinitezerone/MiniBgm/ci.yml?branch=main&label=CI)](https://github.com/infinitezerone/MiniBgm/actions)

</div>

> 🚧 项目处于公开测试阶段，欢迎试用并反馈问题。当前最新版本见 **[Releases](https://github.com/infinitezerone/MiniBgm/releases/latest)**。

## 📸 截图

| 放送时间表 | 番剧详情 |
|---|---|
| ![放送时间表](docs/screenshots/schedule.webp) | ![番剧详情](docs/screenshots/detail.webp) |
| **探索发现** | **搜索** |
| ![探索发现](docs/screenshots/explore.webp) | ![搜索](docs/screenshots/search.webp) |

## ✨ 功能特性

- [x] 🔐 **Bangumi 账号登录** — 跳转 Bangumi 官方页面授权，本应用不经手你的密码；登录状态硬件级加密，只存在你手机里
- [x] 📅 **每周放送时间表** — 按周浏览、时间轴视图、逐话排期（预计/已确认标注）、各话源（巴哈姆特等）直达
- [x] 📺 **番剧详情** — 条目资料、评分 / Rank / 收藏人数、分集分组打卡、角色与演职员
- [x] 💬 **社区功能** — 与网页端同步浏览、发表条目吐槽和评论，社区排版原样呈现，文中链接可直接打开
- [x] 🧭 **探索发现** — 季度片单（新番导视）与淘番榜单，按季度、形式筛选，列表 / 网格双视图
- [x] 🔍 **搜索** — 关键词搜索、类型筛选、综合匹配 / 热门收藏 / 高分优先 / 排名靠前排序
- [x] 📚 **收藏管理** — 浏览收藏、追番状态与观看进度同步、快速打卡
- [x] ⏰ **开播提醒** — 每日汇总"我追的"当日内更新 + 开播前 15 分钟逐集提醒（可关闭，提醒时刻可选）
- [x] 📱 **桌面小组件** — 覆盖全尺寸域的状态分区看板与焦点卡片，点击直达时间表
- [x] ☁️ **后台同步** — 收藏与追番进度自动保持最新，仅在数据有变化时才拉取，省流量也省电

## 📥 获取应用

前往 [Releases](https://github.com/infinitezerone/MiniBgm/releases/latest) 下载最新 APK，直接安装即可（需要 **Android 12 或更高版本**）。

应用内不会检查更新，想看新版本随时回这个页面看看。下载正式版 APK 覆盖安装会保留你的收藏和登录状态；自行构建的调试版与正式版签名不同，需要先卸载已装的版本才能安装。

## 🤝 参与贡献

遇到 Bug 或有功能建议，欢迎[提交 Issue](https://github.com/infinitezerone/MiniBgm/issues)，或在 [Bangumi 开发小组](https://bangumi.tv/group/dev)的发布帖中讨论；反馈崩溃时可在应用内「设置 → 崩溃日志」导出堆栈。

也想动手改代码的话，克隆仓库后构建调试版 APK：

```bash
git clone https://github.com/infinitezerone/MiniBgm.git
cd MiniBgm
./gradlew :app:assembleDebug
```

技术栈为 Kotlin Multiplatform + Jetpack Compose + Material 3；架构红线与验证流程见 **[AGENTS.md](AGENTS.md)**。

## 📄 声明

本项目为个人学习用途的开源作品，仅使用 Bangumi 与 AniList 的公开 API；账号数据仅存于本机，使用本软件产生的任何问题由使用者自行承担。

## 🙏 致谢

- [Bangumi 番组计划](https://bgm.tv) 与其开放的 API
- [AniList](https://anilist.co) 提供的放送排期数据
- [bangumi-data](https://github.com/bangumi-data/bangumi-data) 提供的条目元数据与平台映射
- Google [*Now in Android*](https://github.com/android/nowinandroid) 的架构示范
