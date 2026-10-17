## 剧本 16：播放器手势微交互与指示器防抖抗晃规范（Player Gestures & Stable HUD Typography）
* **规范先例**：`PlayerGestureDetector.kt` 与 `PlayerControlsOverlay.kt`
* **根因警示（来自双击跳针、中央双浮窗打架与滑屏指示器剧烈晃动复盘）**：
  * **双击高频跳针**：双击快进在每次点击时立即调用底层的 Seek 跳转指令，导致快速多次双击或连点时画面产生严重顿挫与跳针感；
  * **双浮窗重叠打架**：双击快进内部既派发了侧边药丸指示（`YouTubeDoubleTapIndicator`），又调用了中央 HUD（`GestureHudState.Seek`），导致中央卡片与侧边药丸两层浮窗重叠遮挡；
  * **动态数字引发容器抽搐晃动**：音量、亮度、进度百分比（`0%`~`100%`）与时间戳文本在比例字体下字形宽度不一（如 `1` 与 `8`）且字符数跳变（2位到4位）；由于包裹容器（`TopCapsuleIndicator`）采用自适应宽度，滑屏时数字的像素级伸缩导致整个 HUD 胶囊在水平方向高频拉伸与抖动。
* **物理防线与操作规范**：
  1. **双击防抖与单次精准提交（Debounced Commit & Accumulation）**：
     * 双击进入 `DOUBLE_TAP_WINDOW_MS`（800ms）等待窗口；后续追加点击自动累加秒数（`+10s` → `+20s` → `+30s`...）；
     * 仅派发侧边灵动药丸（`YouTubeDoubleTapIndicator`），严禁派发中央 `GestureHudState.Seek`；
     * 跳转动作延后至 `DOUBLE_TAP_COMMIT_DELAY_MS`（650ms）延迟 Job，停手后向引擎提交单次精准 Seek。
  2. **文字槽位尺寸固定与表格等宽特性（Fixed Slot & Tabular Numbers）**：
     * 所有随滑动手势高频变化的数字文本，必须显式配置 `fontFeatureSettings = "tnum"`（启用 OpenType 表格等宽数字），使 `0`~`9` 每一个字符占据严格相等的字形宽度；
     * 百分比文字必须显式声明固定槽位宽度，如 `Modifier.width(36.dp)` 配合 `textAlign = TextAlign.End`，杜绝数字从 1 位变 3 位时撑大或缩小外层容器；
     * 屏幕滑动进度指示卡片（`SeekFeedbackCard`）声明 `Modifier.widthIn(min = 160.dp)` 最小稳定宽度，杜绝卡片随秒数晃动。
  3. **红线拦截**：
     * `ArchitectureRulesTest#player_gesture_hud_must_not_jitter_and_use_tnum_and_avoid_dual_hud_conflict` 物理断言 `fontFeatureSettings = "tnum"`、固定宽度及禁止双击调用中央 Seek HUD。
