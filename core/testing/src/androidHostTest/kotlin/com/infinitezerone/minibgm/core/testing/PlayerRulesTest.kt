package com.infinitezerone.minibgm.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** 域规则文件：从 ArchitectureRulesTest 拆出（测试名即规则名，复盘在各测试 KDoc）。 */
class PlayerRulesTest : ArchitectureRuleTestSupport() {
    /**
     * 播放线路面板是**主题化**面板（竖屏底部弹层与横屏右侧抽屉共用同一份内容），
     * 容器色与文字必须全部来自 `MaterialTheme.colorScheme`。
     *
     * 横屏抽屉曾硬编码 `color = Color.Black.copy(alpha = 0.92f)` + `contentColor = Color.White`，
     * 而内部每一行文字又显式取 `onSurface` / `onSurfaceVariant`：浅色主题下标题、说明文案与
     * 「共 N 条线路」整体变成「深灰字压黑底」，对用户等于抽屉顶部是空的；同时与右侧选集抽屉
     * （surfaceContainerHigh + 左侧圆角）风格割裂。`Color.Black` / `Color.White` 这类语义色
     * 绕开了 `Color(0x...)` 硬编码红线，必须单独把这条面板钉死。
     */
    @Test
    fun player_sources_panel_must_be_theme_driven() {
        val file =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerSourcesBottomSheet.kt",
            )
        if (!file.isFile) return

        val scanner = KotlinSourceScanner.fromFile(file)
        val violations = mutableListOf<String>()
        listOf("Color.Black", "Color.White").forEach { token ->
            scanner.containsToken(token).forEach { match ->
                violations.add("PlayerSourcesBottomSheet.kt:${match.lineNumber} 硬编码 $token -> ${match.lineContent}")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "播放线路面板必须整体走主题 token（浅色主题下黑底 + 深色 onSurface 会让标题与说明文案直接消失）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 播放线路面板必须复用设计系统现成组件，而不是在 feature 里手搓私有样式：
     * 竖屏走 `BgmSourcesBottomSheet`（收起动效 / 导航栏 padding / 头部 / 滚动容器），
     * 次级入口（播放源管理、AI 找源）与空态走 `BgmSourceActionCard` / `SourceEmptyStateCard`。
     *
     * 历史缺陷：手写 `BgmModalBottomSheet` + 私有标题行 + `TextButton`「播放源管理」+ 裸 Text 空态，
     * 于是同一份内容横竖两套样式各自漂移——收起没有动效、空态也没有可达的配置入口。
     */
    @Test
    fun player_sources_panel_must_reuse_design_system() {
        val file =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerSourcesBottomSheet.kt",
            )
        if (!file.isFile) return

        val scanner = KotlinSourceScanner.fromFile(file)
        val violations = mutableListOf<String>()

        listOf("BgmSourcesBottomSheet(", "BgmSourceActionCard(", "SourceEmptyStateCard(").forEach { token ->
            if (scanner.containsToken(token).isEmpty()) {
                violations.add("缺少设计系统组件 $token")
            }
        }
        listOf("BgmModalBottomSheet(", "TextButton(").forEach { token ->
            scanner.containsToken(token).forEach { match ->
                violations.add("PlayerSourcesBottomSheet.kt:${match.lineNumber} 手搓容器/私有按钮 $token -> ${match.lineContent}")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "播放线路面板必须复用设计系统现成组件（BgmSourcesBottomSheet / BgmSourceActionCard / SourceEmptyStateCard）：\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun player_sources_interaction_strictly_centralized() {
        val subjectSrcDir = File(projectRoot, "feature/subject/src/main/kotlin")
        if (!subjectSrcDir.isDirectory) return

        val violations = mutableListOf<String>()
        subjectSrcDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                val rel = file.relativeTo(projectRoot).path
                if (text.contains("fun PlayerSourceSelector(") || text.contains("fun BiliPlayerSourceBar(")) {
                    violations.add("$rel 遗留了废弃的线路选择条，必须使用统一的 PlayerCurrentSourceCard 与 PlayerSourcesBottomSheet")
                }
                if (file.name == "PlayerEpisodeQueueDrawer.kt" &&
                    (text.contains("PlayerSourceTab") || text.contains("selectedSourceIndex"))
                ) {
                    violations.add("$rel 选集抽屉违规嵌入了线路选择器，抽屉必须专注选集，切源收拢至统一浮层")
                }
            }
        assertTrue(
            violations.isEmpty(),
            "违反播放线路统一收敛规范：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun player_media_state_reset_must_preserve_engine_side_preferences() {
        val controllerFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerController.kt",
            )
        if (!controllerFile.isFile) return

        val text = controllerFile.readText()
        // 单集循环（isLooping）由引擎的 repeatMode 持有、且是用户级偏好：换源/换集只允许重置媒体相关字段。
        // 一旦退回 `_state.value = PlaybackState()` 这种无参整体重置，界面会显示"已关闭"而引擎仍在循环，
        // 用户要点两次开关才关得掉（换集续播点属同一类"整体覆盖静默丢字段"陷阱）。
        assertTrue(
            !text.contains("_state.value = PlaybackState()"),
            "PlayerController 禁止用 PlaybackState() 无参整体重置媒体态，必须显式保留 isLooping 等引擎侧偏好",
        )
    }

    /**
     * 播放页长期偏好必须持久化，且只能有一个真源（设置仓库投影回读）。
     *
     * 弹幕开关曾只活在 PlayerViewModel 的内存状态里：`toggleDanmaku()` 就地翻转 `_uiState`，
     * 换集/重进播放页就悄悄回到「开」，用户关掉的弹幕自己长回来，且关闭期间仍在后台拉弹幕；
     * 自动连播是同一形态（关掉后换一集又自动连播）。反向的坑在 UI 侧——把偏好私藏在
     * `remember { mutableStateOf }` 里，仓库层完全不知情，重启即丢。两侧都要钉住：
     * 偏好写设置仓库并由快照投影回读，UI 局部状态只承载瞬态交互。
     */
    @Test
    fun player_view_preferences_must_be_persisted_not_held_in_session_state() {
        val violations = mutableListOf<String>()

        // ① ViewModel：偏好开关必须写设置仓库，严禁就地翻转 _uiState 形成第二事实源
        val viewModelPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt"
        val viewModelCode = KotlinSourceScanner.sanitize(File(projectRoot, viewModelPath).readText())
        playerPersistedPreferences.forEach { (field, setter) ->
            if (Regex("""_uiState\s*\.update\s*\{\s*it\.copy\(\s*$field\s*=""").containsMatchIn(viewModelCode)) {
                violations.add(
                    "$viewModelPath 就地翻转 $field：偏好开关必须写设置仓库（$setter）并由快照投影回读，" +
                        "否则重进播放页/换集即丢，且关闭期间的行为（如弹幕拉取）不会真的停",
                )
            }
            if (!viewModelCode.contains("settingsRepository.$setter(")) {
                violations.add("$viewModelPath 缺少 $setter 调用：$field 会退化成拨了不落库的装饰性开关")
            }
        }

        // ② UI：PlayerScreen 的局部状态只能是瞬态交互（悬浮控件/拖动/横竖屏…）。
        //    新增一处局部状态就必须显式回答「它该不该持久化」——偏好要经 ViewModel 下发
        val screenPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerScreen.kt"
        val screenCode = KotlinSourceScanner.sanitize(File(projectRoot, screenPath).readText())
        screenLocalStateDeclaration.findAll(screenCode).map { it.groupValues[1] }.forEach { name ->
            if (name !in playerScreenTransientState) {
                violations.add(
                    "$screenPath 新增局部状态 `$name`：用户偏好必须持久化（经 SettingsRepository 与快照投影），" +
                        "只有瞬态交互才允许留在 UI 局部状态",
                )
            }
        }

        // ③ 反向：会话级状态（播放速度 / 画面比例这类单次观看意图，单集循环这类引擎会话态）严禁落库。
        //    速度与画幅跟着「这一条视频、这一次观看」走，持久化等于让「上一部调到 2 倍速 / 拉伸成裁切」
        //    静默作用到下一部毫不相干的番上（B 站 / YouTube 的倍速同样不跨视频记忆）；循环由引擎
        //    repeatMode 持有，重启后自己记得"还在循环"是用户没要的意外。落库只可能从这两处发生：
        //    偏好模型的数据类，或设置仓库的字段/setter——两处都闭锁。
        val preferencesPath =
            "core/datastore/src/commonMain/kotlin/com/infinitezerone/minibgm/core/datastore/UserPreferences.kt"
        val repositoryPath =
            "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/SettingsRepository.kt"
        val preferencesCode = KotlinSourceScanner.sanitize(File(projectRoot, preferencesPath).readText())
        val repositoryCode = KotlinSourceScanner.sanitize(File(projectRoot, repositoryPath).readText())
        nonPersistedPlayerState.forEach { (name, reason) ->
            if (Regex("""\b(?:val|var)\s+$name\b""").containsMatchIn(preferencesCode)) {
                violations.add(
                    "$preferencesPath 出现偏好字段 `$name`：$reason，" +
                        "落库会把上一次的选择静默带到下一个毫不相干的视频 / 下一次播放",
                )
            }
            if (Regex("""\b$name\b""").containsMatchIn(repositoryCode)) {
                violations.add(
                    "$repositoryPath 出现 `$name`：$reason，不得进入设置仓库（字段/setter/投影都不行），" +
                        "它只允许活在播放页的局部状态 / 引擎会话里",
                )
            }
        }
        // 换个名字（如 preferredPlaybackSpeed / videoResizeMode）落库同样不允许：两处都按关键词兜一遍，
        // 免得「改个名就绕过」
        listOf(preferencesPath to preferencesCode, repositoryPath to repositoryCode).forEach { (path, code) ->
            Regex("""\b(?:val|var|fun)\s+\w*[Ss]peed\w*\b""").findAll(code).forEach { match ->
                violations.add("$path 出现速度类偏好声明 ${match.value.trim()}：播放速度属单次观看意图，严禁落库")
            }
            Regex("""\b(?:val|var|fun)\s+\w*(?:[Rr]esize|[Aa]spect)\w*\b""").findAll(code).forEach { match ->
                violations.add("$path 出现画面比例类偏好声明 ${match.value.trim()}：画面比例属单次观看意图，严禁落库")
            }
            Regex("""\b(?:val|var|fun)\s+\w*[Ll]oop\w*\b""").findAll(code).forEach { match ->
                violations.add("$path 出现循环播放类偏好声明 ${match.value.trim()}：单集循环属引擎会话态，严禁落库")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反「播放页偏好必须持久化」红线（偏好只活在会话内存里，重进页面即回退且行为不会真的停）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 播放器软字幕图层必须直连 [Player.Listener] 且随 [View.OnAttachStateChangeListener] 挂载/卸载。
     *
     * 事故复盘（2026-10-19）：
     * 旧实现将 [Player.Listener.onCues] 写入 Compose `mutableStateOf(cues)` 并经 `AndroidView.update`
     * 派发给 SubtitleView。当播放器在横竖屏（或 PiP）切换时，因外层使用了 `movableContentOf`，
     * 视图节点被从旧窗口脱离并移至新窗口，在此期间 AndroidViewHolder 的快照观察器被 clear，
     * 导致字幕更新观察永久断裂：字幕永久冻结在切屏时刻的某一帧；且用户在设置面板点击「关闭字幕」时，
     * 尽管 ExoPlayer 正确发出了空字幕组，快照观察器也因断开而无法将空字幕推入 SubtitleView，
     * 使得冻结字幕死死停留在画面中央。
     *
     * 红线要求：
     * 1. 严禁在 PlayerSubtitleOverlay 声明 `mutableStateOf` 中转高频字幕；
     * 2. 必须使用直连视图 [PlayerBoundSubtitleView]；
     * 3. 必须通过 View.OnAttachStateChangeListener 管理窗口附着生命周期，且关闭字幕时强制置空。
     */
    @Test
    fun player_subtitle_overlay_must_bind_directly_to_player_listener_without_compose_state() {
        val violations = mutableListOf<String>()
        val screenPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerScreen.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, screenPath).readText())

        val overlayBody =
            Regex("""fun\s+PlayerSubtitleOverlay\s*\([\s\S]*?\)\s*\{([\s\S]*?)\}""")
                .find(code)
                ?.groupValues
                ?.get(1)

        if (overlayBody?.contains("mutableStateOf") == true) {
            violations.add("$screenPath 的 PlayerSubtitleOverlay 函数体声明了 mutableStateOf：高频字幕事件绝不能经 Compose 快照中转，必须直连 Player.Listener")
        }

        if (!code.contains("PlayerBoundSubtitleView") || !code.contains("OnAttachStateChangeListener")) {
            violations.add("$screenPath 必须使用 PlayerBoundSubtitleView 并在 OnAttachStateChangeListener 中绑定与解绑 Player.Listener")
        }

        if (!code.contains("disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)")) {
            violations.add("$screenPath 的字幕组件必须检查 disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) 并在关闭时清空字幕")
        }

        assertTrue(
            violations.isEmpty(),
            "违反「播放器软字幕图层必须直连监听且安全承接横竖屏迁移」架构红线：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 播放器音视频与字幕轨道快照必须进行防重消歧，且通用语言代码（chi/zho/zh）不得武断硬编码为单一变体。
     *
     * 事故复盘（2026-10-19）：
     * 在压制规范的 MKV 片源（如 Crunchyroll WEB-DL 或合集）中，双语字幕按 ISO 639-2 规范均打上
     * `chi` 标签且未填独立 Track Title。旧实现一刀切将 `chi`/`zh` 映射为「中文 (简体)」，
     * 导致选轨弹窗中同时出现两个同名的「中文 (简体)」，繁体字幕被误标且用户只能盲选。
     * 必须经 [disambiguateTrackOptions] 进行消歧（SC_TC 双中文自动归一为简/繁，多同名轨追加编号）。
     */
    @Test
    fun player_tracks_snapshot_must_disambiguate_duplicate_tracks() {
        val sheetPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerTrackSelectionSheet.kt"
        val code = File(projectRoot, sheetPath).readText()

        assertTrue(
            code.contains("disambiguateTrackOptions(subtitleOptions)"),
            "$sheetPath 的 subtitleTracks 必须经 disambiguateTrackOptions 消歧，防止同语言多轨重名混淆",
        )
        assertTrue(
            code.contains("disambiguateTrackOptions(audioOptions)"),
            "$sheetPath 的 audioTracks 必须经 disambiguateTrackOptions 消歧，防止同语言多音轨重名混淆",
        )
        assertTrue(
            !Regex("""listOf\([^)]*"(?:chi|zh|zho)"[^)]*\)\s*->\s*"中文\s*\(简体\)"""").containsMatchIn(code),
            "$sheetPath 严禁将通用宏语言代号 chi/zh/zho 武断硬编码映射为「中文 (简体)」，必须中立标为「中文」并经消歧处理",
        )
    }

    @Test
    fun comment_sort_order_must_be_a_shared_persisted_preference() {
        val violations = mutableListOf<String>()
        val sharedConsumers =
            listOf(
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt",
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/EpisodeDetailViewModel.kt",
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/TopicDetailViewModel.kt",
            )
        sharedConsumers.forEach { path ->
            val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())

            // ① 写入必须落到两页共用的那条设备侧偏好
            if (!code.contains("settingsRepository.setCommentSortOrder(") &&
                !code.contains("settingsRepository?.setCommentSortOrder(")
            ) {
                violations.add("$path 未调用 setCommentSortOrder：吐槽排序必须写在两页共用的设备侧偏好上")
            }

            // ② 严禁再留一份会话状态——两处各持一份时，这里改成「最新」详情页仍是「热门」，重启又双双回默认
            if (Regex("""MutableStateFlow\s*\(\s*CommentSortOrder""").containsMatchIn(code) ||
                Regex("""\bcommentSortOrder\s*\.\s*value\s*=""").containsMatchIn(code)
            ) {
                violations.add("$path 仍持有评论排序的会话状态：唯一真源必须是与另一页共用的设备侧偏好")
            }

            // ③ 解析必须经唯一入口 fromKey——两处各自写默认值正是「同一语义两套降级」的温床
            if (!code.contains("CommentSortOrder.fromKey(")) {
                violations.add("$path 未经 CommentSortOrder.fromKey 解析持久化键：两页的默认值会各自漂移")
            }
        }

        // ④ 持久化键必须与枚举名解耦：直接落 name 会让一次改名把用户已保存的排序静默打回默认值
        val enumPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/CommentSortOrder.kt"
        if (!Regex("""val\s+sortKey\s*:\s*String""").containsMatchIn(
                KotlinSourceScanner.sanitize(File(projectRoot, enumPath).readText()),
            )
        ) {
            violations.add("$enumPath 缺少 sortKey：持久化键必须独立于枚举名（改名不得重置用户排序偏好）")
        }

        assertTrue(
            violations.isEmpty(),
            "违反「跨页共用偏好必须只有一份真源」红线（两页各持一份会话状态，改一处不动另一处，重启即回默认）：\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun danmaku_match_entry_must_be_gated_by_the_danmaku_toggle() {
        val violations = mutableListOf<String>()

        // ① 控制栏入口：有候选还不够，弹幕关闭时必须一并收起
        val overlayPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerControlsOverlay.kt"
        val overlayCode = KotlinSourceScanner.sanitize(File(projectRoot, overlayPath).readText())
        if (!Regex("""hasCandidates\s*=\s*isDanmakuEnabled\s*&&""").containsMatchIn(overlayCode)) {
            violations.add(
                "$overlayPath 的 DanmakuMatchEntryButton 未按 isDanmakuEnabled 收起：弹幕关掉后仍能进" +
                    "「分集纠正」，选中候选会给已关闭的功能写持久化覆盖并拉一次弹幕",
            )
        }

        // ② 播放设置面板里的「分集纠正」行：同样必须随开关收起
        val screenPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerScreen.kt"
        val screenCode = KotlinSourceScanner.sanitize(File(projectRoot, screenPath).readText())
        if (!Regex(
                """onOpenDanmakuPicker\s*=\s*openDanmakuPicker\s*\.takeIf\s*\{[^}]*isDanmakuEnabled""",
            ).containsMatchIn(screenCode)
        ) {
            violations.add(
                "$screenPath 的 onOpenDanmakuPicker 未按 isDanmakuEnabled 收起：设置面板里的" +
                    "「分集纠正」行会在弹幕关闭时照样可点",
            )
        }

        // ③ ViewModel 侧竞态兜底：关掉开关的那一帧里旧入口仍可能被点到
        val viewModelPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt"
        val viewModelCode = KotlinSourceScanner.sanitize(File(projectRoot, viewModelPath).readText())
        val start = viewModelCode.indexOf("fun selectDanmakuCandidate(")
        if (start < 0) {
            violations.add("$viewModelPath 找不到 selectDanmakuCandidate：闸口必须留在该入口内")
        } else {
            val rest = viewModelCode.substring(start)
            val end = rest.indexOf("\n    fun ")
            val body = if (end > 0) rest.substring(0, end) else rest
            if (!body.contains("isDanmakuEnabled")) {
                violations.add(
                    "$viewModelPath 的 selectDanmakuCandidate 未按 isDanmakuEnabled 闸口：" +
                        "关闭弹幕期间点到旧入口仍会 saveDanmakuOverride + 拉一次弹幕",
                )
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反「弹幕关闭即零请求」红线（入口不随开关收起，关闭期间仍写覆盖/发请求）：\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun danmaku_view_must_use_dynamic_track_allocation_and_not_hash_modulo() {
        val violations = mutableListOf<String>()
        val danmakuViewPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/DanmakuView.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, danmakuViewPath).readText())

        // 1. 禁止使用粗暴的 text.hashCode() % maxTracks
        if (code.contains(".hashCode()") || code.contains("hashCode() %")) {
            violations.add(
                "$danmakuViewPath 包含 hashCode() 弹道取模：粗暴的哈希取模会导致相同文字（如「草」「2333」）" +
                    "或哈希冲突的弹幕物理重叠在同一轨道，且无视文字长度与移动速度差异引发追尾",
            )
        }

        // 2. 必须具备动态排版预计算 computeDanmakuLayout
        if (!code.contains("fun computeDanmakuLayout(") || !code.contains("computeDanmakuLayout(")) {
            violations.add("$danmakuViewPath 缺少 computeDanmakuLayout：弹道分配必须前置预计算，严禁每帧盲算")
        }

        // 3. 绘制循环必须消费 PositionedDanmaku，且严禁在绘制循环里反复 measureText
        if (!code.contains("PositionedDanmaku")) {
            violations.add("$danmakuViewPath 缺少 PositionedDanmaku 预计算实例模型")
        }

        val canvasIdx = code.indexOf("Canvas(modifier =")
        if (canvasIdx > 0) {
            val canvasBody = code.substring(canvasIdx)
            if (canvasBody.contains("measureText(")) {
                violations.add("$danmakuViewPath 在 Canvas 绘制块中调用了 measureText：测量必须在预计算中完成，严禁在 60/120 FPS 绘制帧触发文字测量")
            }
        }

        // 4. 必须具备字幕安全避让逻辑
        if (!code.contains("SUBTITLE_AREA_RATIO") && !code.contains("subtitleAreaRatio")) {
            violations.add("$danmakuViewPath 缺少字幕避让保护区域计算：必须为屏幕底部预留正文字幕安全区")
        }

        assertTrue(
            violations.isEmpty(),
            "违反「弹幕动态排版与防重叠」架构红线：\n" + violations.joinToString("\n"),
        )
    }

    /** 播放页必须落库的偏好字段 → 对应的 SettingsRepository 写入方法 */

    private val playerPersistedPreferences: Map<String, String> =
        mapOf(
            "isDanmakuEnabled" to "setDanmakuEnabled",
            "autoNextEnabled" to "setAutoNextEnabled",
            // 关闭字幕：稳定立场，重启后仍要「字幕关」（引擎参数只表达本次播放）
            "subtitlesDisabled" to "setSubtitlesDisabled",
            "danmakuAlpha" to "setDanmakuAlpha",
            "danmakuDurationMs" to "setDanmakuDurationMs",
        )

    /** PlayerScreen 允许保留的 UI 局部状态（全部是瞬态交互，不是用户偏好） */

    private val playerScreenTransientState: Set<String> =
        setOf(
            "areControlsVisible",
            "isLandscape",
            // 播放速度与画面比例：跟着「这一条视频、这一次观看」走，刻意不持久化（见测试内的反向闭锁）
            "playbackSpeed",
            "resizeMode",
            "isScreenLocked",
            "lastResumedEpisodeId",
            "resumeOverlayPosition",
            "playerReady",
            "selectedPortraitTab",
            "rawStageBounds",
            "qualityAppliedKey",
        )

    /** PlayerScreen 里的 `var x by remember { mutableStateOf(...) }` 声明 */

    private val screenLocalStateDeclaration =

        Regex(
            """^\s*var\s+(\w+)\s+by\s+remember(?:Saveable)?\s*\{\s*mutable\w*StateOf""",
            RegexOption.MULTILINE,
        )

    /**
     * 刻意不落库的播放页会话级状态。
     *
     * 两类：① 单次观看意图（播放速度 / 画面比例——跟着「这一条视频、这一次观看」走）；
     * ② 引擎会话态（单集循环 `isLooping`——由引擎 `repeatMode` 持有，进程内跨换集/换源保留，
     * 但刻意不跨重启记忆；B 站/YouTube 的循环同样不跨视频）。
     */
    private val nonPersistedPlayerState: Map<String, String> =

        mapOf(
            "playbackSpeed" to "播放速度属单次观看意图（B 站 / YouTube 的倍速同样不跨视频记忆）",
            "resizeMode" to "画面比例属单次观看意图（跟着「这一条视频、这一次观看」走）",
            "isLooping" to "单集循环属引擎会话态（由 repeatMode 持有，刻意不跨重启记忆）",
        )

    @Test
    fun exoplayer_engine_media_source_factory_must_wrap_default_data_source() {
        val engineFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/ExoPlayerEngine.kt",
            )
        if (!engineFile.isFile) return

        val text = engineFile.readText()
        // Media3 的 DefaultMediaSourceFactory 若裸传 HttpDataSource.Factory，
        // 播放本地 file://（如离线下载完成的视频）时会尝试强转 HttpURLConnection 导致 ClassCastException 崩溃。
        // 必须通过 DefaultDataSource.Factory(context, httpDataSourceFactory) 包装以支持离线与本地资源回放。
        assertTrue(
            text.contains("DefaultDataSource.Factory(context, httpDataSourceFactory)"),
            "ExoPlayerEngine 的 DefaultMediaSourceFactory 必须使用 DefaultDataSource.Factory 包装以支持离线 file:// 播放",
        )
    }

    @Test
    fun player_view_model_must_filter_out_torrent_rules_from_online_sources() {
        val playerViewModelFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt",
            )
        if (!playerViewModelFile.isFile) return

        val text = playerViewModelFile.readText()
        // 播放器线路必须排除 PlaybackRuleKind.TORRENT，P2P 种子由离线下载和详情页专门面板承接，不得作为在线直链嗅探源。
        assertTrue(
            text.contains("PlaybackRuleKind.TORRENT"),
            "PlayerViewModel 必须处理并过滤 PlaybackRuleKind.TORRENT 种子规则",
        )
    }

    /**
     * 播放器观看记录与断点续播红线：
     * 1. 观看历史与断点续播唯一真源是 [PlaybackHistoryRepository]，按分集 [episodeId] 绑定；
     * 2. [PlayerViewModel] 严禁再引用 SettingsRepository.playbackPositions / savePlaybackPosition / clearPlaybackPosition，
     *    彻底剥离直链 streamUrl 依赖，杜绝双轨数据与跨集串流。
     */
    @Test
    fun player_view_model_playback_resume_must_bind_strictly_to_playback_history_repo() {
        val playerViewModelFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerViewModel.kt",
            )
        if (!playerViewModelFile.isFile) return
        val text = KotlinSourceScanner.sanitize(playerViewModelFile.readText())

        val violations = mutableListOf<String>()
        if (text.contains("settingsRepository.playbackPositions") ||
            text.contains("settingsRepository.savePlaybackPosition") ||
            text.contains("settingsRepository.clearPlaybackPosition")
        ) {
            violations.add("PlayerViewModel 仍存在对 SettingsRepository.playbackPositions 的读写调用，必须统一收拢至 PlaybackHistoryRepository")
        }
        if (!text.contains("playbackHistoryRepository?.saveHistory") ||
            !text.contains("playbackHistoryRepository?.deleteHistory") ||
            !text.contains("playbackHistoryRepository?.getHistoryByEpisode")
        ) {
            violations.add("PlayerViewModel 缺少对 PlaybackHistoryRepository 的 save/delete/getHistoryByEpisode 完整调用闭环")
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放器观看记录与断点续播架构红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 播放源管理与观看记录解耦红线：
     * 1. 观看历史由独立 [HistoryViewModel] 和 [HistoryScreen] 承载，归口个人中心 [UserScreen] 顶栏直达；
     * 2. [PlaybackRulesViewModel] 还原为纯粹的播放源规则与订阅管理，严禁持有 PlaybackHistoryRepository 或混入历史管理。
     */
    @Test
    fun playback_history_must_be_decoupled_from_playback_rules_view_model() {
        val rulesViewModelFile =
            File(
                projectRoot,
                "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/PlaybackRulesViewModel.kt",
            )
        if (!rulesViewModelFile.isFile) return
        val text = KotlinSourceScanner.sanitize(rulesViewModelFile.readText())

        val violations = mutableListOf<String>()
        if (text.contains("PlaybackHistoryRepository") || text.contains("playbackHistory")) {
            violations.add("PlaybackRulesViewModel 仍混入观看记录逻辑（持有 PlaybackHistoryRepository 或暴露 playbackHistory），必须彻底解耦")
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放源规则与观看记录解耦架构红线：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun player_gesture_hud_must_not_jitter_and_use_tnum_and_avoid_dual_hud_conflict() {
        val gestureFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerGestureDetector.kt",
            )
        assertTrue(gestureFile.isFile, "PlayerGestureDetector.kt 未找到")
        val rawContent = gestureFile.readText()
        val content = KotlinSourceScanner.sanitize(rawContent)

        val violations = mutableListOf<String>()

        // 1. 顶部胶囊必须带 tnum 与固定槽位宽度
        if (!rawContent.contains("""fontFeatureSettings = "tnum"""")) {
            violations.add("PlayerGestureDetector.kt 缺少 fontFeatureSettings = \"tnum\" 配置，数字宽度跳变会导致 HUD 剧烈抖动")
        }
        if (!content.contains("Modifier.width(36.dp)")) {
            violations.add("TopCapsuleIndicator 缺少固定文本宽度 Modifier.width(36.dp)，百分比字符数变化会导致胶囊忽长忽短")
        }

        // 2. SeekFeedbackCard 必须有最小宽度约束
        if (!content.contains("widthIn(min = 160.dp)")) {
            violations.add("SeekFeedbackCard 缺少 widthIn(min = 160.dp) 最小宽度约束，拖动快进快退时卡片容易晃动")
        }

        // 3. 双击快进 handleQuickSeek 严禁向中央派发 GestureHudState.Seek，杜绝侧边 YouTube 药丸与中央卡片双重浮窗打架
        val handleQuickSeekBody =
            Regex("""fun handleQuickSeek\([\s\S]*?\{([\s\S]*?)\n\s*\}""")
                .find(content)
                ?.groupValues
                ?.get(1)
        if (handleQuickSeekBody != null && handleQuickSeekBody.contains("GestureHudState.Seek")) {
            violations.add("handleQuickSeek 违规调用了 GestureHudState.Seek，导致双击快进时侧边药丸与中央卡片两层浮窗重叠打架")
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放器手势 HUD 稳定性与防冲突红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 播放器拖动进度唯一真源红线。
     *
     * 同一份拖动状态曾拆成 `isScrubbing` + `scrubProgress` 两个平行变量、两套回调
     * （进度条走 onScrubStart/onScrubbing/onScrubEnd，屏幕手势走 onScrubbing(Boolean, Float)），
     * 代价有三：
     * ① 两者必须同帧变化，分开存能造出「未在拖动，但进度停在旧拖动点」的非法中间态；
     * ② 拖动中按下锁屏键时 `onDragEnd` 提前 return，复位被跳过，进度条永久卡在拖动态；
     * ③ 每帧拖动都经 PlayerScreen 提升的参数向下重组整块控制层。
     *
     * 现在拖动状态收敛到 PlayerScrubState（单字段原子快照），手势与进度条共用同一组回调，
     * seek 提交只有一个出口（onScrubEnd），手势层不再自行提交定位。
     */
    @Test
    fun player_scrub_state_must_be_the_single_source_of_truth() {
        val playerDir = "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player"
        val holderFile = File(projectRoot, "$playerDir/PlayerScrubState.kt")
        assertTrue(holderFile.isFile, "PlayerScrubState.kt 未找到：拖动状态没有唯一真源")

        val violations = mutableListOf<String>()
        val holder = KotlinSourceScanner.sanitize(holderFile.readText())
        if (!holder.contains("internal data class PlayerScrubSnapshot(") ||
            !holder.contains("val active: Boolean") ||
            !holder.contains("val fraction: Float")
        ) {
            violations.add("PlayerScrubSnapshot 必须把 active/fraction 合成一个不可变快照，禁止拆成两个平行变量")
        }
        if (!holder.contains("var state by mutableStateOf(")) {
            violations.add("PlayerScrubState 必须用单个 mutableStateOf 持有快照")
        }

        listOf("PlayerGestureDetector.kt", "PlayerProgressBar.kt", "PlayerControlsOverlay.kt", "PlayerScreen.kt").forEach { name ->
            val code = KotlinSourceScanner.sanitize(File(projectRoot, "$playerDir/$name").readText())
            if (code.contains("scrubProgress")) {
                violations.add("$name 仍出现 scrubProgress：拖动进度必须只经 PlayerScrubState，禁止恢复平行变量")
            }
            if (code.contains("onScrubbing: (isScrubbing: Boolean")) {
                violations.add(
                    "$name 仍保留 onScrubbing(Boolean, Float) 旧协议：手势与进度条必须共用 onScrubStart/onScrubbing/onScrubEnd",
                )
            }
        }

        val gesture = KotlinSourceScanner.sanitize(File(projectRoot, "$playerDir/PlayerGestureDetector.kt").readText())
        if (!gesture.contains("onScrubStart(") || !gesture.contains("onScrubCancel(")) {
            violations.add("PlayerGestureDetector 未把拖动透传给 onScrubStart/onScrubCancel")
        }
        if (gesture.contains("onSeekConfirm")) {
            violations.add("PlayerGestureDetector 仍自行提交 seek：拖动结束必须只走 onScrubEnd 单一出口，否则与进度条提交路径重复")
        }
        if (!gesture.contains("abs(seekTargetMs - initialPositionMs)")) {
            violations.add("PlayerGestureDetector 缺少零位移守卫：原地 seek 在流式源上会触发一次重缓冲")
        }

        val screen = KotlinSourceScanner.sanitize(File(projectRoot, "$playerDir/PlayerScreen.kt").readText())
        if (!screen.contains("PlayerScrubState()")) {
            violations.add("PlayerScreen 未创建 PlayerScrubState：拖动状态必须由单一持有者承载")
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放器拖动进度唯一真源红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 播放器横屏右侧抽屉（PlayerRightDrawerContainer）必须将关闭动效响应绑定至快照流与下树清理，
     * 且 BackHandler 必须按目标显隐状态条件启闭（`enabled = visibleState.targetState`）：
     *
     * 1. 动效结束自动响应：严禁使用 `SideEffect` 判定 `visibleState.isIdle && !visibleState.currentState`。
     *    `SideEffect` 仅在宿主重组时执行，抽屉退出动效结束时内部状态变更不会触发宿主重组，导致 `onRespond()` 永不执行，
     *    `OverlayHostState` 挂起协程永久泄漏，隐蔽层级残留在 UI 树；必须通过 `snapshotFlow` 监听动效完成。
     * 2. 下树兜底响应：必须通过 `DisposableEffect(Unit)` 在 `onDispose` 时触发兜底响应，防止横竖屏切换/页面退出/PiP 强制下树时挂起协程挂死。
     * 3. 返回键条件使能：严禁注册无条件的裸 `BackHandler { onDismissRequest() }`；必须声明 `BackHandler(enabled = visibleState.targetState)`。
     *    否则关闭动画期间或残留层级会永久霸占 Android `OnBackPressedDispatcher` 栈顶，拦截并吞掉所有后续返回键，导致横屏模式下系统返回键彻底失灵。
     */
    @Test
    fun player_right_drawer_must_bind_lifecycle_and_conditional_back_handler() {
        val file =
            File(projectRoot, "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerPanelOverlays.kt")
        assertTrue(file.isFile, "PlayerPanelOverlays.kt 未找到")
        val code = KotlinSourceScanner.sanitize(file.readText())

        val drawerToken = "fun PlayerRightDrawerContainer"
        val drawerStart = code.indexOf(drawerToken)
        assertTrue(drawerStart >= 0, "PlayerRightDrawerContainer 未找到")

        // 提取 PlayerRightDrawerContainer 函数体
        val openBrace = code.indexOf('{', drawerStart)
        assertTrue(openBrace >= 0, "PlayerRightDrawerContainer 函数体未找到")
        var depth = 0
        var closeBrace = openBrace
        while (closeBrace < code.length) {
            when (code[closeBrace]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            closeBrace++
        }
        val drawerBody = code.substring(openBrace, minOf(closeBrace + 1, code.length))

        // 1. 严禁使用 SideEffect 判定退出动效完成
        if (drawerBody.contains("SideEffect")) {
            fail("PlayerRightDrawerContainer 严禁使用 SideEffect：宿主未重组时退出动效结束无法触发响应，导致挂起协程泄漏与返回键失效")
        }

        // 2. 动效完成必须使用 snapshotFlow 收集
        if (!drawerBody.contains("snapshotFlow")) {
            fail("PlayerRightDrawerContainer 必须使用 snapshotFlow 监听退出动效结束并触发 onRespond")
        }

        // 3. 必须包含 DisposableEffect 兜底下树清理
        if (!drawerBody.contains("DisposableEffect") || !drawerBody.contains("onDispose")) {
            fail("PlayerRightDrawerContainer 必须声明 DisposableEffect(Unit).onDispose 进行下树兜底清理，避免横竖屏切换时协程泄漏")
        }

        // 4. BackHandler 必须按 targetState 条件使能
        val hasConditionalBackHandler =
            drawerBody.contains("BackHandler(enabled = visibleState.targetState)") ||
                drawerBody.contains("BackHandler(enabled =")
        if (!hasConditionalBackHandler) {
            fail("PlayerRightDrawerContainer 的 BackHandler 必须声明 enabled = visibleState.targetState，防止关闭后拦截后续系统返回键")
        }
    }

    /**
     * 播放器锁定屏规范：
     * 1. PlayerGestureDetector.kt 在 isLocked 时单击必须调用 onSingleTap()，严禁裸 return 吞掉点击导致无法唤出解锁键；
     * 2. PlayerScreen.kt 的 BackHandler 必须拦截 isScreenLocked，禁止误触物理返回键/侧边手势退出全屏破锁；
     * 3. PlayerScreen.kt 的传感器方向联动必须由 isScreenLocked 守卫，禁止旋转手机退出全屏并解锁；
     * 4. PlayerScreen.kt 进入 PiP 时必须重置 isScreenLocked = false。
     */
    @Test
    fun player_screen_lock_must_prevent_exit_and_allow_tap_wake() {
        val gestureFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerGestureDetector.kt",
            )
        assertTrue(gestureFile.isFile, "PlayerGestureDetector.kt 未找到")
        val gestureCode = KotlinSourceScanner.sanitize(gestureFile.readText())

        // 验证 isLocked 分支调用了 onSingleTap()
        val tapLockPattern = Regex("""if\s*\(\s*isLocked\s*\)\s*\{\s*onSingleTap\(\)\s*return@detectTapGestures\s*\}""")
        assertTrue(
            tapLockPattern.containsMatchIn(gestureCode),
            "PlayerGestureDetector 在 isLocked 时单击必须调用 onSingleTap() 唤出解锁键，严禁直接 return 吞掉点击",
        )

        val screenFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerScreen.kt",
            )
        assertTrue(screenFile.isFile, "PlayerScreen.kt 未找到")
        val screenCode = KotlinSourceScanner.sanitize(screenFile.readText())

        // BackHandler 必须拦截 isScreenLocked
        assertTrue(
            screenCode.contains("if (isScreenLocked)") && mentionsInsideBlock(screenCode, "BackHandler", "isScreenLocked"),
            "PlayerScreen 的 BackHandler 必须显式拦截 isScreenLocked，防止返回手势破锁退出全屏",
        )

        // 传感器转屏必须守卫 isScreenLocked
        val sensorLockPattern = Regex("""LaunchedEffect\([^)]*isScreenLocked[^)]*\)\s*\{[^}]*isScreenLocked[^}]*return@LaunchedEffect""")
        assertTrue(
            sensorLockPattern.containsMatchIn(screenCode),
            "PlayerScreen 的重力感应转屏必须在 isScreenLocked 时跳过，防止物理旋转手机破锁切竖屏",
        )

        // 进入 PiP 必须重置 isScreenLocked
        assertTrue(
            mentionsInsideBlock(screenCode, "LaunchedEffect(isInPipMode)", "isScreenLocked = false"),
            "PlayerScreen 进入 PiP 模式时必须重置 isScreenLocked = false",
        )
    }
}
