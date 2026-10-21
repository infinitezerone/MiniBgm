# 设计令牌来源表（Provenance）

本表回答一个问题：每个 token 为什么是这个值。来源分为三类：

- **规范继承**：直接采用成熟设计体系的结构或数值。
- **规范骨架 + 项目调参**：沿用成熟体系的角色或阶梯，但为了 MiniBgm 的内容密度、设备条件或现有观感调整了具体值。
- **项目自研**：没有可直接移植的公开规范，数值来自本项目实测、视觉校准或降级策略。

「参考某产品」只表示视觉方向，不代表逐 token 复制。没有公开 token 规范的系统（例如 Apple Liquid Glass、HyperOS、ColorOS）统一标为 `PROJECT`。

## 来源标记

| 标记 | 含义 |
| --- | --- |
| `M3` | Material Design 3 官方规范 |
| `M3A` | Material 3 Adaptive / Window Size Class |
| `M2` | Material Design 2 官方规范 |
| `TW` | Tailwind CSS 官方色板 |
| `BRAND` | Bangumi 或内容平台品牌色 |
| `OS` | 当前系统 UI 的玻璃/光效视觉方向，仅作视觉参考 |
| `PROJECT` | MiniBgm 实测、调参或降级策略，无公开规范可逐值对应 |

## 令牌来源

| Token 家族 | 代码位置 | 当前取值 | 来源 | 性质与说明 |
| --- | --- | --- | --- | --- |
| 品牌锚点 | `theme/Color.kt` | `BgmPink #F09199`、`#FEDFE1`、`#D05A3F` | `BRAND` | Bangumi 经典樱花粉，作为品牌种子色保留。 |
| M3 颜色角色 | `theme/Color.kt`、`theme/Theme.kt` | primary / secondary / tertiary / error / surface / outline 等完整角色 | `M3` | 采用 M3 角色结构；十六进制值为手工维护，不依赖运行时生成。`dynamicColor=true` 时由系统 Material You 配色接管。 |
| AMOLED 表面阶梯 | `theme/Color.kt`、`theme/Theme.kt` | `#000000`、`#0A0A0C`、`#101114`、`#16181C`、`#1D1F24` | `PROJECT` | M3 没有 AMOLED 纯黑档；仅替换表面与容器，品牌色和文本角色沿用深色档。 |
| 收藏状态色 | `theme/SemanticColors.kt` | 主色 Tailwind 400；浅色容器 50/100；前景 700/800；暗色容器自定义 | `TW` + `PROJECT` | indigo / emerald / pink / amber 使用 400 + 50 + 800，slate 使用 400 + 100 + 700；暗色容器为项目校准。原注释写 Radix，已校正为 Tailwind。 |
| 放送、评分、类型色 | `theme/SemanticColors.kt` | `#FF5722`、`#3F51B5`、`#F59E0B`、Material 50/700 阶梯等 | `M2` + `M3` + `BRAND` + `PROJECT` | 放送橙和经典徽章蓝接近 Material 色板；评分金接近 Tailwind amber；类型色以 Material 50/700 为底；平台色取 Bilibili / Mikan 品牌色。暗色容器为项目值。 |
| 字体 | `theme/Type.kt` | display 57/45/36，headline 32/28/24，title 20/16/14，body 16/14/12，label 14/12/11 | `M3` + `PROJECT` | 15 级 type scale 来自 M3；`titleLarge` 使用 20/26（M3 默认 22/28），行高、字重和字距按中日文条目密度微调；字体族使用系统默认。 |
| 圆角 | `theme/Shape.kt` | `4 / 8 / 12 / 16 / 24dp` | `M3` + `PROJECT` | 前四档对齐 M3 Shapes；`extraLarge` 采用 24dp，而 M3 默认是 28dp，用于悬浮导航和底部抽屉。 |
| 玻璃形状 | `theme/LuminousTokens.kt` | 玻璃 32dp、抽屉顶部 28dp、内层 20dp | `PROJECT` | 独立于 `BgmShapes` 的材质形状阶梯，只服务玻璃 chrome。修改时不能假设它属于 M3 圆角阶梯。 |
| 间距 | `theme/Spacing.kt` | `4 / 8 / 12 / 16 / 20 / 24 / 32dp`；chrome 另有 `6 / 10 / 48 / 64 / 88 / 152dp` | `M3` + `PROJECT` | 基础阶梯沿用 4dp baseline grid；48dp 对应 Material 最小触控靶区；6/10/64/88dp 是悬浮底栏、胶囊呼吸和 Snackbar 避让的实测值；152dp 是悬浮底栏背后渐隐 scrim 的高度，用于替代实时模糊保证文字对比度。页面级边距仍由各 feature 持有。 |
| 阴影高度 | `theme/Elevation.kt` | `0 / 2 / 10 / 12dp` | `M3` + `PROJECT` | 借用 M3 的 elevation 语义，但不使用 M3 的 0/1/3/6/8/12 档位；数值按内容框、玻璃 chrome、瞬态浮层三种重量校准。 |
| 透明度 | `theme/Opacity.kt` | `0.12 / 0.35 / 0.40 / 0.45 / 0.60 / 0.65 / 0.75 / 0.85` | `PROJECT` | 项目自定义语义档，用于骨架、分隔、描边、弱提示、Scrim 和高对比叠层。部分值接近 M3 state layer 的 0.12/0.38，但不是官方 token 映射。 |
| 动效 | `theme/Motion.kt` | 完整 M3 `short1–extraLong4` 时长；7 条 M3 easing；Standard / Expressive / Aquamorphic 各 `fast/default/slow` 弹簧组（Aquamorphic 采用 ColorOS 水生临界阻尼 0.78/850f）；旧 API 保留 `FastOutSlowIn` 与 `0.82/0.80` | `M3` + `M2` + `OS` + `PROJECT` | `Durations` / `Easings` / `Springs` 为 M3 原始令牌；`Aquamorphic` 为 ColorOS 15 水生流体微动效调优参数；`spatialSpring` / `effectsSpring` 通过 `Scheme` + `Speed` 选择空间与视效参数；`standardTween` / `standardSpring` / `snappySpring` 是兼容层，保留既有观感；骨架屏 1500ms 为项目值。 |
| 自适应断点 | `theme/AdaptiveLayout.kt` | compact `<600dp`、medium `600–839dp`、expanded `>=840dp` | `M3A` | 宽度断点来自 Material 3 Window Size Class；高度小于 480dp 时强制归入 `COMPACT` 是项目策略，不是 M3 规则。 |
| 封面比例 | `theme/Dimens.kt` | 海报 `0.7`，人物 `0.72` | `PROJECT` | 海报当前使用约 5:7，而非标准 2:3（0.667）；人物立绘略瘦于海报。两者都是全站唯一来源。 |
| 玻璃材质 | `theme/LuminousTokens.kt` | Subtle `0.68/0.46`、Regular `0.78/0.58`、Prominent `0.86/0.70`；glow `0.16/0.22/0.20` | `OS` + `PROJECT` | 视觉方向参考当前 Apple / HyperOS / ColorOS 的玻璃与光效，但没有公开 token 规范可逐值复制；三层 alpha、描边、内高光和 backdrop 均为项目校准。省电模式或低内存设备降级为实体材质。 |
| 实体大圆盘按钮 | `component/BgmCircleActionButton.kt` | 40dp 纯净实体圆盘、无边框、0dp 阴影（纯平实色）、surfaceContainerHighest 底色 | `OS` + `PROJECT` | 对齐现代旗舰系统应用与 ChatGPT 等无边框纯净实体大圆盘基座交互；零描边，彻底摒弃粗糙的 Material 投影，依靠纯净填充明度差自然浮起。 |
| 双向天幕渐隐遮罩 | `component/BgmTopChromeScrim.kt`、`BgmFloatingChromeScrim.kt` | 顶部 108dp、底部 136dp 垂直多段平滑衰减渐变 | `OS` + `PROJECT` | 向上滚动时卡片与文本慢慢沉入顶端天幕消融，与状态栏完全融为一体；向下滚动时柔和掩映内容并平滑托起无边框实体 Dock 药丸。 |
| 顶栏双模式 | `component/BgmTopBarMode.kt`、`component/BgmTopAppBar.kt` | `Immersive`（全透明+天幕遮罩穿透）与 `Solid`（实体表面底色+状态栏覆盖+hairline底边） | `OS` + `M3` + `PROJECT` | 对齐现代旗舰系统多场景诉求：内容浏览流（日历、发现、详情）走沉浸天幕穿透；管理与配置（设置、规则、下载）走实体稳固结构。 |

## 已知偏离与保留决定

这些不是遗漏，而是当前有意保留的差异。改值前应先确认是否要同步改变视觉行为。

| 项目 | 当前值 | 成熟基线 | 保留原因 |
| --- | --- | --- | --- |
| `titleLarge` | `20sp / 26sp` | M3 `22sp / 28sp` | 降低中日文长标题的占用，保持列表密度。 |
| `BgmShapes.extraLarge` | `24dp` | M3 `28dp` | 与悬浮导航、底部抽屉的现有轮廓一致。 |
| `BgmElevation` | `2 / 10 / 12dp` | M3 elevation levels `0/1/3/6/8/12` | 用语义重量替代档位编号，避免所有浮层共用同一阴影。 |
| `BgmMotion.standardEasing` | `FastOutSlowIn` | M3 standard `CubicBezier(0.2, 0, 0, 1)` | 保留现有过场观感；若切换到严格 M3 动效，需要单独做视觉验收。 |
| 悬浮底栏紧凑几何 | `theme/Spacing.kt` | 52dp 紧凑微胶囊、18dp 图标、4dp 垂直呼吸、72dp Snackbar 避让 | `PROJECT` | 削减虚高 16dp，从 68dp 臃肿形态收敛为 52dp 灵动修长琴键，释放首屏浏览空间。 |
| 踏实内容 UI 规范 | 全站通用卡片与组件 | 纯净实体色阶、零死描边（`border = null` 或低透明度融合）、2dp 柔和环境微阴影 | `OS` + `PROJECT` | 内容层（卡片、Tag、分集项）同源遵守踏实、纯净实体风格：拒绝塑料半透与颗粒噪点，依靠纯色阶反差（`surfaceContainerLow`）建立层级，保障高密度排版与阅读沉浸感。 |
| 玻璃材质 | 三层自定义 alpha 与形状 | 无公开 token 规范 | 属于品牌视觉层，不是可移植的规范 token。 |

## Motion Token 约定

- 新代码优先使用 `BgmMotion.spatialSpring`、`BgmMotion.effectsSpring`、`BgmMotion.m3StandardTween` 和 `BgmMotion.functionalTween`。
- 弹簧默认 `Scheme.Expressive + Speed.Default`；系统组件或需要更克制的过渡时显式选 `Scheme.Standard`，不要逐页面手调阻尼与刚度。
- 位置、尺寸、旋转属于 spatial；透明度、颜色属于 effects。effects 不允许过冲。
- 进度、弹幕、骨架、滚动和手势跟随属于 functional，使用 `functionalTween` 或线性时间轴，不使用弹簧。
- `BgmMotion.standardTween`、`standardSpring`、`snappySpring` 只作为旧调用兼容层；新代码不要继续扩大使用面。
- `BgmMotion.standardEasing` 仍是 M2 的 FastOutSlowIn。需要严格 M3 曲线的调用点应显式使用 `m3StandardTween`，不要依赖默认值。

## 尚未 token 化

以下项目目前仍散落在组件或 feature 中，不属于本表的「已规定」范围：

- 图标尺寸：`13/14/16/18/20/22/24/28/32/36/48/56dp` 等。
- 触控靶区：`48dp` 仍是隐式约定，没有统一 token。
- 内容与弹层最大宽度：例如 `BgmBottomSheet` 的 `600dp`。
- 页面栅格与页边距：当前有意由各 feature 持有。
- 层级顺序：已有视觉 elevation，没有 background/content/chrome/overlay/modal 的 z-layer 语义。
- 通用描边宽度：目前只有 `BgmSpacing.hairline = 1dp`。

## 维护规则

1. 新增 token 必须在本表增加一行，写清来源标记、当前值和选择理由。
2. 修改 token 数值时，必须在同一次改动中更新本表；只改代码不更新来源，视为文档不完整。
3. 使用 `M3` 时必须区分角色继承、type scale 继承和 M3 Expressive 动效；不要用一个标记覆盖所有差异。
4. 没有公开规范的视觉层统一标 `PROJECT`，不要写成「参考 M3」或「参考 Apple 规范」。
5. 视觉参考类 token 必须同时记录降级策略，例如玻璃材质在省电模式或低内存设备上回退实体材质。
