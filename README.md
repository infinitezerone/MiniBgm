# MiniBgm

<div align="center">

**现代化 Bangumi (bgm.tv) 番组计划 Android 客户端**

放送时间表 · 番剧详情 · 社区吐槽 · 收藏管理 · 搜索发现

[![Release](https://img.shields.io/github/v/release/infinitezerone/MiniBgm)](https://github.com/infinitezerone/MiniBgm/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/infinitezerone/MiniBgm/ci.yml?branch=main&label=CI)](https://github.com/infinitezerone/MiniBgm/actions)

</div>

> 🚧 项目处于公开测试阶段（v0.1.0），欢迎试用并反馈问题。

## 📸 截图

| 放送时间表 | 番剧详情 |
|---|---|
| ![放送时间表](docs/screenshots/schedule.png) | ![番剧详情](docs/screenshots/detail.png) |
| **探索发现** | **搜索** |
| ![探索发现](docs/screenshots/explore.png) | ![搜索](docs/screenshots/search.png) |

## ✨ 功能特性

- [x] 🔐 **Bangumi 账号登录** — OAuth 授权登录，凭据 Android Keystore 硬件级加密、仅保存在本机
- [x] 📅 **每周放送时间表** — 按周浏览、时间轴视图、各话源（巴哈姆特等）直达
- [x] 📺 **番剧详情** — 条目资料、评分 / Rank / 收藏人数、分集分组打卡、角色与演职员
- [x] 💬 **社区功能** — 条目吐槽与评论（next API）、BBCode 富文本渲染、网页链接原生识别
- [x] 🧭 **探索发现** — 当季社区热评、瀑布流发现
- [x] 🔍 **搜索** — 关键词搜索、类型筛选、综合匹配 / 热门收藏 / 高分优先 / 排名靠前排序
- [x] 📚 **收藏管理** — 浏览收藏、追番状态与观看进度同步、快速打卡
- [x] ☁️ **后台同步** — WorkManager 周期同步与 ETag 条件缓存

**规划中**

- [ ] 开播提醒
- [ ] 桌面小组件
- [ ] 离线缓存

## 📥 获取应用

前往 [Releases](https://github.com/infinitezerone/MiniBgm/releases/latest) 下载最新 APK 直接安装（需要 **Android 12+**）。

也可以自行构建（需要 JDK 25 与 Android SDK）：

```bash
git clone https://github.com/infinitezerone/MiniBgm.git
cd MiniBgm
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 🛠 技术栈

Kotlin Multiplatform（core 层跨平台）· Jetpack Compose（Material 3 Expressive）· Navigation 3 · Ktor 3 · Room 3 · DataStore · Coil 3 · Koin 4 · WorkManager · AGP 9

模块化 Clean Architecture（modeled on Google's [*Now in Android*](https://github.com/android/nowinandroid)）：`app` + `core`（model / common / network / database / datastore / data / designsystem / navigation / testing）+ `feature`（schedule / subject / search / user）+ `sync:work`。

## 🤝 参与贡献

遇到 Bug 或有功能建议，欢迎[提交 Issue](https://github.com/infinitezerone/MiniBgm/issues)，或在 [Bangumi 开发小组](https://bangumi.tv/group/dev)的发布帖中讨论。

## 📄 声明

本项目为个人学习用途的开源作品，仅使用 Bangumi 开放 API；账号数据仅存于本机，使用本软件产生的任何问题由使用者自行承担。

## 🙏 致谢

- [Bangumi 番组计划](https://bgm.tv) 与其开放的 API
- [bangumi-data](https://github.com/bangumi-data/bangumi-data) 提供的放送数据
- Google [*Now in Android*](https://github.com/android/nowinandroid) 的架构示范
