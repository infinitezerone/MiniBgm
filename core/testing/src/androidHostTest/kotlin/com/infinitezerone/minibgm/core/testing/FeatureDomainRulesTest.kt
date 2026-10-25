package com.infinitezerone.minibgm.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** 域规则文件：从 ArchitectureRulesTest 拆出（测试名即规则名，复盘在各测试 KDoc）。 */
class FeatureDomainRulesTest : ArchitectureRuleTestSupport() {

    /**
     * 排期单一真源红线：时刻表仓库不得再依赖官方日历、bangumi-data 固定窗口或算术预测。
     * 这三者正是历史上“漏番 / 假集数”的来源；AniList 周排期是名单与时间的唯一真源。
     */
    @Test
    fun schedule_repository_uses_anilist_as_the_only_roster_source() {
        val repoFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/ScheduleRepository.kt",
            )
        assertTrue(repoFile.isFile, "ScheduleRepository.kt 未找到: ${repoFile.absolutePath}")

        val forbidden =
            listOf(
                "getCalendar(" to "官方日历已废弃：名单/排期真值只能来自 AniList 周排期",
                "getRecentBangumiData(" to "bangumi-data 固定窗口已废弃：只能按 startDate/begin 月按需拉取单月切片",
                "AirEventKind.PREDICTED" to "禁止生成算术预测事件（PREDICTED）",
            )
        val violations = mutableListOf<String>()
        val scanner = KotlinSourceScanner.fromFile(repoFile)
        for ((needle, reason) in forbidden) {
            scanner.containsToken(needle).forEach { match ->
                violations.add("${repoFile.name}:${match.lineNumber} $reason -> ${match.lineContent}")
            }
        }

        if (violations.isNotEmpty()) {
            fail("违反排期单一真源红线（AniList 为名单+时间唯一真源）：\n" + violations.joinToString("\n"))
        }
    }


    /**
     * WebView 可观测性红线（见 AGENTS.md 领域规则）：
     * 生产代码中声明 WebChromeClient 必须实现 onConsoleMessage。
     * 严禁静默吞掉前端控制台与 JS 报错，导致排查陷入盲区。
     */
    @Test
    fun webChromeClient_must_implement_onConsoleMessage() {
        val violations = mutableListOf<String>()
        projectRoot
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && !it.path.replace(File.separatorChar, '/').contains("/build/") }
            .forEach { file ->
                val content = file.readText()
                if (content.contains("WebChromeClient()") || content.contains(": WebChromeClient")) {
                    if (!content.contains("onConsoleMessage")) {
                        val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                        violations.add("$relPath 实现了 WebChromeClient 但未重写 onConsoleMessage")
                    }
                }
            }
        assertTrue(
            violations.isEmpty(),
            "违反 WebView 可观测性红线（见 AGENTS.md 领域规则）：\n" + violations.joinToString("\n"),
        )
    }


    /**
     * 按需动态 `.so` 装载与确认红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. 生产代码凡调用 `System.load(` 从私有目录加载动态库，同文件必须调用 `.setReadOnly()`（Android 10+ W^X 安全约束）、
     *    使用 `Mutex` 防并发重入，并按应用版本隔离目录；
     * 2. `TorrentRepository` 严禁暴露未经用户确认即静默下载组件的 `checkOrInstallEngine` 后门。
     */
    @Test
    fun dynamic_so_loading_enforces_wx_version_isolation_and_no_silent_install() {
        val violations = mutableListOf<String>()
        var foundDynamicLoader = false

        projectRoot
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES && !it.name.startsWith(".") && it.name != "build-logic" }
            .filter { it.isFile && it.extension == "kt" && !it.path.replace(File.separatorChar, '/').contains("/build/") }
            .forEach { file ->
                val relPath = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                val sanitized = KotlinSourceScanner.sanitize(file.readText())

                if (sanitized.contains("fun checkOrInstallEngine")) {
                    violations.add("$relPath 暴露了未确认静默下载入口 checkOrInstallEngine（违反扩展下载显式确认红线）")
                }

                if (sanitized.contains("System.load(")) {
                    foundDynamicLoader = true
                    if (!sanitized.contains(".setReadOnly()")) {
                        violations.add("$relPath 调用了 System.load( 但缺少 .setReadOnly()（违反 Android 10+ W^X 只读安全约束）")
                    }
                    if (!sanitized.contains("Mutex()")) {
                        violations.add("$relPath 调用了 System.load( 但缺少 Mutex() 并发下载互斥保护")
                    }
                    if (!sanitized.contains("cleanStalePluginDirs")) {
                        violations.add("$relPath 调用了 System.load( 但缺少版本目录隔离与旧版本清理 cleanStalePluginDirs")
                    }
                }
            }

        assertTrue(foundDynamicLoader, "未找到包含 System.load( 的按需动态库加载实现")
        assertTrue(
            violations.isEmpty(),
            "违反按需动态 .so 装载安全红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }


    /**
     * 开发者面板「开关必须真实生效」红线（见 AGENTS.md 领域规则 & 剧本 8）：
     *
     * 面板里能拨的每一个开关都必须至少有一个**生产消费方**——只能写设置、只能读设置、
     * 但没人拿它做事，就是"装饰性开关"：用户拨完以为配置生效，实际行为毫无变化，
     * 且全程没有任何提示。历史上同一次审查里连中两例：
     *   - `customMirrorPrefixOverride`（自定义加速镜像前缀）：更新包与 BT 原生扩展的下载
     *     从未读过它，填了前缀照样走默认公共镜像；
     *   - `verboseLoggingEnabled`（详细网络与引擎日志）：日志门限只在启动期按 BuildConfig
     *     决定一次，Release 包里打开开关依旧只有 Warn 以上日志，真机排查形同盲人摸象。
     *
     * 机械判据：每个开关名在「消费方白名单」之外的源码里至少出现一次，
     * 且白名单文件本身必须真的把它接进行为（读设置 → 传参 / 调门限）。
     */
    @Test
    fun developer_panel_settings_must_have_a_real_consumer() {
        val violations = mutableListOf<String>()

        fun raw(relativePath: String): String {
            val file = File(projectRoot, relativePath)
            assertTrue(file.isFile, "$relativePath 未找到")
            return file.readText()
        }

        fun code(relativePath: String): String = KotlinSourceScanner.sanitize(raw(relativePath))

        val downloaderPath =
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidAssetDownloader.kt"
        val downloaderDiPath =
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/di/DataModule.android.kt"
        val appPath = "app/src/main/kotlin/com/infinitezerone/minibgm/MiniBgmApp.kt"

        // 1. 自定义加速镜像前缀：下载器必须暴露读取器，且 DI 必须把它接到 DeveloperSettings 上
        if (!code(downloaderPath).contains("customMirrorPrefixProvider")) {
            violations.add(
                "$downloaderPath 未暴露 customMirrorPrefixProvider：开发者面板配的前缀进不了下载器，只会静默走默认镜像",
            )
        }
        if (!code(downloaderPath).contains("customMirrorPrefixProvider()")) {
            violations.add("$downloaderPath 声明了 customMirrorPrefixProvider 却从未在解析候选地址前读取（死参数）")
        }
        if (!code(downloaderDiPath).contains("customMirrorPrefixProvider") ||
            !code(downloaderDiPath).contains("developerSettings") ||
            !code(downloaderDiPath).contains("customMirrorPrefixOverride")
        ) {
            violations.add(
                "$downloaderDiPath 未把 AssetDownloader 的镜像前缀读取器接到 SettingsRepository.developerSettings.customMirrorPrefixOverride",
            )
        }

        // 2. 详细调试日志：门限必须由开关驱动，而不是只在启动期按 BuildConfig 定死一次
        val appCode = code(appPath)
        if (!appCode.contains("verboseLoggingEnabled")) {
            violations.add("$appPath 未消费 verboseLoggingEnabled：详细调试日志开关在真机上不改变任何日志门限")
        }
        if (!appCode.contains("setMinSeverity")) {
            violations.add("$appPath 未按开关调整 Logger 门限（setMinSeverity）")
        }
        if (!appCode.contains("distinctUntilChanged")) {
            violations.add("$appPath 收集 developerSettings 时缺少 distinctUntilChanged：每次都重设全局日志门限")
        }

        assertTrue(
            violations.isEmpty(),
            "违反开发者面板开关生效红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }


    /**
     * 「已落盘资源」动作诚实性红线（见 AGENTS.md 领域规则 & 剧本 8）：
     *
     * 资源已完整落盘时，「离线下载」必须被禁用，且「已下载」判据必须排除物理文件缺失的任务。
     * 否则两条路径都会骗人：
     *   - 放行点击只会落进 `startDownload` 的「既有完成任务直接返回 taskId」分支，
     *     随后弹出「已加入下载队列，可在通知栏查看进度」——用户盯着通知栏等一个永不出现的进度；
     *   - 若只认 `status == COMPLETED` 而忽略文件已被清理，徽标会宣告「已下载」、
     *     播放按钮指向不存在的 `file://`，同时下载入口被禁，用户卡在既播不了也重下不了的死路。
     */
    @Test
    fun downloaded_resource_actions_must_stay_honest() {
        val path =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())
        val violations = mutableListOf<String>()

        val inAppDownloadGate =
            Regex(
                """R\.string\.feature_subject_source_torrent_in_app_download[\s\S]{0,1200}?enabled\s*=\s*!isAnyBusy\s*&&\s*!isDownloaded""",
            )
        if (!inAppDownloadGate.containsMatchIn(code)) {
            violations.add(
                "$path 的「离线下载」未在 isDownloaded 时禁用：已下载资源点击后只会弹一条「已加入下载队列」的假提示",
            )
        }

        val completedGate = Regex("""fun completedTaskFor\([\s\S]{0,1200}?!task\.isFileMissing""")
        if (!completedGate.containsMatchIn(code)) {
            violations.add(
                "$path 的 completedTaskFor 未排除 isFileMissing：文件已被清理的任务仍会被标成「已下载」，点下去是一条既播不了也重下不了的死路",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反「已落盘资源」动作诚实性红线：\n" + violations.joinToString("\n"),
        )
    }


    /**
     * 播放源底栏必须统一使用设计系统规范卡片（BgmSourceActionCard）：
     * 在线 Tab 与 BT Tab 的空态管理引导必须同构，严禁在 BT 空态中随手借调 TorrentActionButton，
     * 且管理跳转必须严格通过 onDismissAndRun / runAfterDismiss 调度底栏收起动画。
     * 多按钮操作行必须显式声明 contentPadding，彻底杜绝 Material 3 默认 48dp 内边距挤压截断中文。
     */
    @Test
    fun subject_sources_sheet_must_use_unified_source_action_card_for_management() {
        val path =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())
        val violations = mutableListOf<String>()

        val torrentEmptyBlock =
            Regex("""!hasTorrentRules[\s\S]{0,1000}?if\s*\(\s*onManageRules\s*!=\s*null\s*\)[\s\S]{0,600}?BgmSourceActionCard""")
        if (!torrentEmptyBlock.containsMatchIn(code)) {
            violations.add(
                "$path 的 BT 空态配置引导未采用 BgmSourceActionCard 统一卡片，破坏了与在线播放 Tab 的设计系统同构性",
            )
        }

        val torrentAdhocButton = Regex("""!hasTorrentRules[\s\S]{0,1000}?TorrentActionButton""")
        if (torrentAdhocButton.containsMatchIn(code)) {
            violations.add(
                "$path 的 BT 空态配置引导错误借用了 TorrentActionButton，违反设计系统收敛红线",
            )
        }

        val torrentDismissCheck = Regex("""!hasTorrentRules[\s\S]{0,1000}?onDismissAndRun\s*\{\s*onManageRules\(\)\s*\}""")
        if (!torrentDismissCheck.containsMatchIn(code)) {
            violations.add(
                "$path 的 BT 空态管理跳转未包裹 onDismissAndRun 抽屉收起动画",
            )
        }

        val buttonPaddingCheck = Regex("""onToggleWatched[\s\S]{0,400}?contentPadding\s*=\s*PaddingValues\(""")
        if (!buttonPaddingCheck.containsMatchIn(code)) {
            violations.add(
                "$path 的打卡动作按钮未显式声明 contentPadding，在窄屏多按钮排版下将触发 Material 3 默认 48dp 内边距导致的中文文字截断",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反「播放源底栏组件与交互同构」红线：\n" + violations.joinToString("\n"),
        )
    }


    /**
     * 详情页分集点击必须直接路由至统一抽屉（SubjectSourcesBottomSheet）：
     * 严禁退化为「先弹分集微操作、再关弹窗开播放源」的多层嵌套与抽屉跳抽屉反模式。
     */
    @Test
    fun subject_detail_episode_click_must_route_to_unified_sources_sheet() {
        val path =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectDetailScreen.kt"
        val code = KotlinSourceScanner.sanitize(File(projectRoot, path).readText())

        val unifiedRouteCheck =
            Regex(
                """onEpisodeClickForQuickAction\s*=\s*\{\s*episode\s*->[\s\S]{0,150}?selectedEpisodeForSources\s*=\s*episode[\s\S]{0,100}?showSourcesBottomSheet\s*=\s*true""",
            )
        assertTrue(
            unifiedRouteCheck.containsMatchIn(code),
            "$path 的 onEpisodeClickForQuickAction 必须直接打开统一底栏（selectedEpisodeForSources + showSourcesBottomSheet），严禁开孤立抽屉套娃！",
        )
    }


    /**
     * 播放源订阅完整性红线（见 AGENTS.md 领域规则 & 剧本 15）：
     * 1. `SettingsRepository` 刷新/导入订阅源时严禁按 `isAlive` 过滤丢弃规则：
     *    连通性探测纯用于 UI 延迟排序与诊断展示，瞬时丢包或服务端反爬返回 403 绝不代表规则废弃，
     *    过滤 `it.isAlive` 会静默丢弃用户订阅的绝大多数合法可用源（历史上曾导致 5 个 MacCMS 源丢掉 4 个）；
     * 2. `CommunitySubscriptionService` 探测存活判据必须支持 HTTP 403（MacCMS 根路径普遍返回 403 但 API 可用），
     *    严禁仅以 200..399 判死。
     */
    @Test
    fun subscription_sources_must_never_be_discarded_by_probe_status() {
        val settingsRepoFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/SettingsRepository.kt",
            )
        assertTrue(settingsRepoFile.isFile, "SettingsRepository.kt 未找到")
        val repoContent = KotlinSourceScanner.sanitize(settingsRepoFile.readText())

        val violations = mutableListOf<String>()
        if (repoContent.contains(".filter { it.isAlive }") || repoContent.contains(".filter { rule -> rule.isAlive }")) {
            violations.add(
                "SettingsRepository.kt 在导入/刷新订阅时按 isAlive 过滤规则：探测失败会永久丢弃合法规则源，违反订阅完整性红线",
            )
        }

        val serviceFile =
            File(
                projectRoot,
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/CommunitySubscriptionService.kt",
            )
        assertTrue(serviceFile.isFile, "CommunitySubscriptionService.kt 未找到")
        val serviceContent = KotlinSourceScanner.sanitize(serviceFile.readText())

        if (serviceContent.contains("status.value in 200..399")) {
            violations.add(
                "CommunitySubscriptionService.kt 仅将 200..399 视为存活：MacCMS 等源根路径返回 403 会被误判为死亡",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放源订阅完整性红线：\n" + violations.joinToString("\n"),
        )
    }


    /**
     * TVBox JS 爬虫沙盒硬安全隔离红线（见 AGENTS.md 领域规则 & 剧本 15）：
     * 1. [JsSpiderSandbox] 必须显式配置内存限制（`memoryLimit`）与执行超时保护（`evaluationTimeoutMillis`）；
     * 2. 不可信脚本的网络能力必须经由宿主 `PageFetchService` 注入，严禁直连裸 Socket。
     */
    @Test
    fun js_spider_sandbox_must_enforce_resource_limits_and_host_network() {
        val sandboxFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/resolver/spider/JsSpiderSandbox.kt",
            )
        assertTrue(sandboxFile.isFile, "JsSpiderSandbox.kt 未找到")
        val content = KotlinSourceScanner.sanitize(sandboxFile.readText())

        assertTrue(
            content.contains("memoryLimit ="),
            "JsSpiderSandbox 必须显式声明 memoryLimit 内存配额，防止不可信脚本 OOM",
        )
        assertTrue(
            content.contains("evaluationTimeoutMillis ="),
            "JsSpiderSandbox 必须显式声明 evaluationTimeoutMillis 超时保护，防止第三方脚本死循环挂死",
        )
        assertTrue(
            content.contains("PageFetchService"),
            "JsSpiderSandbox 网络请求必须通过 PageFetchService 代理，享有统一的白名单与重定向审计",
        )
    }


    /**
     * TVBox JS Spider 的 ext 必须从站点配置一路传到 `init(ext)`：
     * 真实订阅里 ext 常承载签名、域名或接口开关（字符串或 JSON 对象），
     * 只保留脚本地址而丢掉 ext 会让脚本初始化通过、后续请求全空，表现为“源能导入但永远没结果”。
     *
     * 同时 `req` 必须是同步 API（内部可等待宿主网络），否则标准 CatVod 脚本里
     * `req(...).content` 会拿到 Promise；保留 `await req(...)` 兼容性。
     */
    @Test
    fun tvbox_js_spider_must_forward_site_ext_and_keep_sync_req_compatibility() {
        val modelContent =
            KotlinSourceScanner.sanitize(
                File(
                    projectRoot,
                    "core/model/src/commonMain/kotlin/com/infinitezerone/minibgm/core/model/CommunitySubscription.kt",
                ).readText(),
            )
        assertTrue(
            modelContent.contains("ext = resolveSpiderExt(scriptUrl)"),
            "TvBoxSite.convert 必须把 Spider 的 ext 带到 DiscoveredSource，不能只保留脚本地址",
        )
        assertTrue(
            modelContent.contains("ext = ext"),
            "DiscoveredSource.toPlaybackSourceRule 必须继续透传 ext",
        )

        val resolverContent =
            KotlinSourceScanner.sanitize(
                File(
                    projectRoot,
                    "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/resolver/spider/TvBoxSpiderResolver.kt",
                ).readText(),
            )
        val resolverRaw =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/resolver/spider/TvBoxSpiderResolver.kt",
            ).readText()
        assertTrue(
            resolverRaw.contains("__bgm_call('init'") && resolverRaw.contains("encodeExtArgument(rule.ext)"),
            "TvBoxSpiderResolver 必须把规则 ext 编码后传给 Spider init(ext)",
        )

        val sandboxRaw =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/resolver/spider/JsSpiderSandbox.kt",
            ).readText()
        assertTrue(
            sandboxRaw.contains("globalThis.req = function"),
            "CatVod req 必须保持同步返回，兼容 req(...).content；async 版本会破坏标准脚本",
        )
        assertTrue(
            sandboxRaw.contains("globalThis.pdfh") && sandboxRaw.contains("globalThis.pdfa"),
            "JS Spider 沙盒必须提供 pdfh/pdfa HTML 选择器兼容层",
        )
    }


    /**
     * TVBox 聚合解析链与嗅探引擎安全提速红线（剧本 15）：
     * 1. [TvBoxParseResolverImpl] 必须配置超时看门狗（PARSE_TIMEOUT_MS / withTimeoutOrNull），防止慢解析服务挂死；
     * 2. HTTP 请求必须委托宿主 [PageFetchService] 或 [WebViewCaptureService]，零裸网络调用；
     * 3. [MediaCandidateJudge] 必须拦截图片/字体等重型非媒体静态资源以加速嗅探。
     */
    @Test
    fun tvbox_parses_must_enforce_timeout_and_route_through_page_fetch_or_webview() {
        val resolverFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/resolver/parse/TvBoxParseResolver.kt",
            )
        assertTrue(resolverFile.isFile, "TvBoxParseResolver.kt 未找到")
        val resolverContent = KotlinSourceScanner.sanitize(resolverFile.readText())

        assertTrue(
            resolverContent.contains("withTimeoutOrNull"),
            "TvBoxParseResolver 必须具备超时保护，防止第三方解析接口挂起",
        )
        assertTrue(
            resolverContent.contains("PageFetchService"),
            "TvBoxParseResolver 必须通过 PageFetchService 发起网络请求",
        )
        assertTrue(
            resolverContent.contains("coroutineScope") && resolverContent.contains("Channel"),
            "TvBoxParseResolver 聚合解析 (type=4) 必须并发调度子解析器竞速",
        )

        val judgeFile =
            File(
                projectRoot,
                "core/webview/src/main/kotlin/com/infinitezerone/minibgm/core/webview/MediaCandidateJudge.kt",
            )
        assertTrue(judgeFile.isFile, "MediaCandidateJudge.kt 未找到")
        val judgeContent = KotlinSourceScanner.sanitize(judgeFile.readText())

        assertTrue(
            judgeContent.contains("shouldBlockResource"),
            "MediaCandidateJudge 必须具备非必要静态资源拦截过滤能力",
        )
    }


    /**
     * 下载视频外部调用与相册导出安全规范。
     *
     * 架构约束：
     * 1. app/src/main/res/xml/file_paths.xml 必须声明 downloads/ 目录授权，防止 FileProvider 在拉起外部播放器时报 IllegalArgumentException；
     * 2. StreamingAppLauncher 必须显式添加 FLAG_GRANT_READ_URI_PERMISSION，防止第三方播放器无权读取沙盒媒体；
     * 3. AndroidTorrentRepository 导出至 MediaStore 时必须对 IS_PENDING 与 RELATIVE_PATH 进行规范维护。
     */
    @Test
    fun external_media_sharing_and_gallery_export_must_satisfy_security_and_system_specs() {
        val filePathsXml = File(projectRoot, "app/src/main/res/xml/file_paths.xml")
        assertTrue(filePathsXml.isFile, "file_paths.xml 未找到")
        val filePathsContent = filePathsXml.readText()
        assertTrue(
            filePathsContent.contains("path=\"downloads/\""),
            "file_paths.xml 必须包含 downloads/ 路径映射以支持下载媒体的 FileProvider 安全共享",
        )

        val launcherFile =
            File(projectRoot, "core/navigation/src/main/kotlin/com/infinitezerone/minibgm/core/navigation/StreamingAppLauncher.kt")
        assertTrue(launcherFile.isFile, "StreamingAppLauncher.kt 未找到")
        val launcherContent = launcherFile.readText()
        assertTrue(
            launcherContent.contains("FLAG_GRANT_READ_URI_PERMISSION"),
            "StreamingAppLauncher 必须为外部播放器 Intent 赋予 FLAG_GRANT_READ_URI_PERMISSION 读取权限",
        )

        val repoFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            )
        assertTrue(repoFile.isFile, "AndroidTorrentRepository.kt 未找到")
        val repoContent = repoFile.readText()
        assertTrue(
            repoContent.contains("IS_PENDING"),
            "AndroidTorrentRepository 必须在 Android 10+ 导出相册时控制 IS_PENDING 状态",
        )
    }


    /**
     * 详情页二级 Tab 结构红线（切 tab 抖动复盘）：
     * 旧实现是「单个共享 LazyColumn 换 items + stickyHeader 假吸顶 + 尾部自适应留白 +
     * requestScrollToItem 重锚 + 72dp 阈值手势假滑动」。切到瞬时矮内容的 tab（讨论页
     * loading 占位）时，列表总高度突然小于「当前滚动位置 + 视口」，共享滚动被钳制弹回——
     * Robolectric 最小复现实测吸顶 tab 栏 offset 从 0 掉到 1920px，且头部随弹回重新入视口、
     * headerScrolledPast 翻 false，顶栏标题折叠进度错误归零（用户看到「标题不见了」）；
     * 尾部留白只会在底部追加空间、无法恢复被钳掉的滚动，弹回后永不自愈。
     * 根治结构：HorizontalPager 每页独立滚动 + 头部 nestedScroll 收合 + Tab 栏常驻，
     * 「瞬时矮内容」在结构上不可表达。回退到共享容器换 items 即为本测试绊线。
     */
    @Test
    fun subject_detail_tabs_must_use_pager() {
        val violations = mutableListOf<String>()
        val contentRelPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectDetailContent.kt"
        val screenRelPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectDetailScreen.kt"
        val content = KotlinSourceScanner.sanitize(File(projectRoot, contentRelPath).readText())
        val screen = KotlinSourceScanner.sanitize(File(projectRoot, screenRelPath).readText())

        for (required in listOf("HorizontalPager(", "rememberPagerState", "SubjectHeaderCollapseState")) {
            if (!content.contains(required)) {
                violations.add("$contentRelPath 丢失 `$required`：二级 Tab 必须是 Pager + 每页独立滚动结构")
            }
        }
        for (banned in listOf("stickyHeader", "requestScrollToItem", "detectHorizontalDragGestures")) {
            if (content.contains(banned)) {
                violations.add("$contentRelPath 违规出现 `$banned`：禁止共享 LazyColumn 换 items 假吸顶/假滑动")
            }
        }
        if (screen.contains("SUBJECT_TABS_ITEM_KEY") || screen.contains("SUBJECT_HEADER_ITEM_KEY")) {
            violations.add("$screenRelPath 仍按列表 key 定位吸顶栏/头部：吸顶已由收合结构承担，禁止回退")
        }

        assertTrue(
            violations.isEmpty(),
            "详情页二级 Tab 必须保持 Pager 结构（共享滚动容器切 tab 会被瞬时矮内容钳制弹回）：\n" +
                violations.joinToString("\n"),
        )
    }

    /** [inner] 是否出现在以 [blockToken] 开头、花括号配对的代码块内 */

}
