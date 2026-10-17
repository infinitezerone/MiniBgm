## 剧本 14：统一分集抽屉与播放源设计系统收敛规范（Unified Episode Drawer & Sources Design System Conformance）
* **规范先例**：SubjectSourcesBottomSheet.kt 与 SubjectDetailOverlays.kt
* **根因警示（来自抽屉套抽屉、多按钮中文裁切与跨 Tab 样式割裂复盘）**：
  * **弹窗套弹窗（反模式）**：点击分集弹「分集微操作抽屉」，在其中点击「播放源」关掉当前抽屉又弹「播放源抽屉」——层级割裂、上下文丢失，让备选播放源彻底沦为隐藏死角；
  * **中文按钮文字截断**：Material 3 Button / FilledTonalButton 默认内边距为两侧各 24dp（合计 48dp）。在 3 按钮横排排版下，每个按钮宽度仅 ~100dp，扣除 48dp 边距与图标后剩余宽度不足 35dp，4 个中文汉字（如「标记已看」）必然被挤压裁切为「标记...」；
  * **跨 Tab 组件漂移（局部最优陷阱）**：在线播放 Tab 使用标准 BgmSourceActionCard（大圆角、左侧图标底盘、主副双行文案、右侧 › 箭头），而 BT 磁力 Tab 为了应付「不能整段隐藏」的测试红线，随手复用了下载卡片内部的 TorrentActionButton 拼凑空态——破坏了设计系统同构性，且漏掉了底栏收起动效。
* **物理防线与操作规范**：
  1. **分集微操作与播放源单一抽屉合流**：
     * 条目页点击任意分集直接路由至 SubjectSourcesBottomSheet(episode = selectedEpisode)；
     * 顶部提供原位切集导航（< 上一集 3 / 28 下一集 >），换集时抽屉无需反复关闭，标题、打卡状态、流媒体直链与 BT 规则在内部流式更新；
     * 分集主起播（Hero 立即播放卡片）直接作为抽屉主体平铺在打卡操作下方，彻底消除抽屉跳抽屉。
  2. **多按钮排版显式设定内边距（防截断铁律）**：
     * 超过 1 个按钮横排分布时，必须显式指定紧凑内容内边距：
       contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)；
     * 严格限制同行动作数量（主操作与打卡分离，操作行保持 <= 2 个按钮），配合极简文案（标记已看 / 取消打卡 / 看到此话），保障 100% 充裕排版宽度。
  3. **设计系统卡片收敛（BgmSourceActionCard 唯一真源）**：
     * 底栏内的一切「规则配置/外部管理」入口，唯一合法组件必须是 BgmSourceActionCard，严禁在不同 Tab 下拼凑私有小按钮；
     * 底栏内部的外部页面跳转动作必须严格经由 
unAfterDismiss / onDismissAndRun 调度收起动画，保持人机交互的顺畅连贯。
  4. **全局播放入口无分集回退与直链嗅探防线（No-Episode Fallback & API Sniffing Guard）**：
     * 条目详情页顶栏 TV 图标等全局播放源入口在未选中分集（selectedEpisodeForSources == null）时，SubjectDetailOverlays 必须按「下一集未看 > 首集」智能回退 ffectiveEpisode，抽屉内部 onlineRules 循环严禁因 pisode == null 整体跳过；
     * API 规则（如 MacCMS）严禁作为媒体直链直接传入 PlayerRoute.streamUrl，isLikelyMediaStream 必须对 .php / pi.php / .json 等接口型 URL 严格断言为非媒体流；
     * 播放器错误浮层（PlayerControlsOverlay）中的「切换下一个源」（onNextSource）与「切换线路」（onOpenSourcesPanel）必须并存呈现，且横向多按钮排版必须带 PaddingValues(horizontal = 12.dp, vertical = 0.dp) 杜绝截字。
  5. **红线拦截**：
     * ArchitectureRulesTest#subject_sources_sheet_must_use_unified_source_action_card_for_management
     * ArchitectureRulesTest#subject_detail_episode_click_must_route_to_unified_sources_sheet
