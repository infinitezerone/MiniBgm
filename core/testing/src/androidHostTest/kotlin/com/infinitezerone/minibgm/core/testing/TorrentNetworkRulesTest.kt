package com.infinitezerone.minibgm.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** 域规则文件：从 ArchitectureRulesTest 拆出（测试名即规则名，复盘在各测试 KDoc）。 */
class TorrentNetworkRulesTest : ArchitectureRuleTestSupport() {
    /**
     * ECH 引擎必须把「自有请求超时」翻译成 Ktor 的 `HttpRequestTimeoutException`（IOException）。
     *
     * 直接抛 `CancellationException` 的后果（详情页整页失败的复盘）：重试插件显式排除取消语义，
     * 一次超时就判死整次请求；调用方也无法区分「我们等超时了」与「用户取消了」。
     * 同一个文件里 body 读取处早就翻译成 IOException，请求阶段却原样抛取消——两套相反契约。
     */
    @Test
    fun ech_engine_must_translate_its_own_timeout() {
        val file =
            File(
                projectRoot,
                "core/network/src/androidMain/kotlin/com/infinitezerone/minibgm/core/network/ech/EchHttpClientEngine.kt",
            )
        if (!file.isFile) return

        val text = file.readText()
        assertTrue(
            text.contains("HttpRequestTimeoutException("),
            "EchHttpClientEngine 必须把自有超时翻译成 HttpRequestTimeoutException（IOException），" +
                "严禁直接 throw cancellation——重试插件会把它当成调用方取消",
        )
    }

    /**
     * 网络切换必须真的重置探测冷却与解析缓存，并且**真的被注册**。
     *
     * 平台契约：地址与可达性绑定在「当前网络」上。监听器写了却没接线，等于换网后仍要等满
     * 10 分钟冷却——这正是「明明有网却一直超时」的成因，而且静态上完全看不出来。
     */
    @Test
    fun ech_network_switch_must_be_wired_and_reset_state() {
        val monitor =
            File(
                projectRoot,
                "core/network/src/androidMain/kotlin/com/infinitezerone/minibgm/core/network/ech/EchNetworkMonitor.kt",
            )
        val app = File(projectRoot, "app/src/main/kotlin/com/infinitezerone/minibgm/MiniBgmApp.kt")
        if (!monitor.isFile || !app.isFile) return

        val monitorText = monitor.readText()
        assertTrue(
            monitorText.contains("registerDefaultNetworkCallback"),
            "网络监听必须走 ConnectivityManager.registerDefaultNetworkCallback（平台契约）",
        )
        assertTrue(
            monitorText.contains("CloudflareEdgeProbe.resetCooldown()"),
            "换网必须重置探测冷却，否则要等满 10 分钟才会重新发现边缘",
        )
        assertTrue(
            monitorText.contains("AdaptiveDnsResolver.invalidateCaches()"),
            "换网必须丢弃旧网络的 DNS 与候选缓存",
        )
        assertTrue(
            monitorText.contains("EchRuntime.clearConnectionPool()"),
            "换网必须清空底层长连接池，否则会在已失效的旧网络 Socket 上死等超时",
        )
        assertTrue(
            app.readText().contains("startNetworkMonitor("),
            "监听器写了却没在 App 启动时注册，等于没有——必须调用 EchRuntime.startNetworkMonitor",
        )
    }

    @Test
    fun stream_engines_never_use_fake_placeholders_or_unbound_sockets() {
        val dataDir = File(projectRoot, "core/data")
        assertTrue(dataDir.isDirectory, "core/data 目录未找到: ${dataDir.absolutePath}")

        val violations = mutableListOf<String>()
        val forbiddenTokens =
            listOf(
                "minibgm_streamer_placeholder",
                "minibgm_fake",
            )

        dataDir
            .resolve("src")
            .walkTopDown()
            .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val content = sourceFile.readText()
                val relPath = sourceFile.relativeTo(projectRoot).path.replace(File.separatorChar, '/')

                forbiddenTokens.forEach { token ->
                    if (content.contains(token)) {
                        violations.add("$relPath 违规包含虚假流占位标记 '$token'")
                    }
                }

                if (content.contains("ServerSocket(0)") && (relPath.contains("Torrent") || relPath.contains("Stream"))) {
                    violations.add("$relPath 严禁在流媒体引擎中使用 ServerSocket(0) 伪造空端口分发流地址（必须由真实常驻服务端或 JNI 监听绑定）")
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "违反流媒体发流真实性规范（严禁伪造 placeholder 虚假文件或未绑定的空 Socket 伪地址欺骗播放器）：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    /**
     * ECH 边缘优选池防污染与本地回环流分流红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `EchHttpClientEngine` 捕获 `CancellationException` 时必须区分真实请求超时（`TimeoutCancellationException`）
     *    与正常协程取消（如 Coil 列表滚动取消图片请求），严禁无条件调用 `recordFailure` 导致 `EchEdgePool` 黄金节点被误杀；
     * 2. 本地回环 HTTP 流必须保留 `isLoopbackHttpUrl` 判定（用于放宽 socket 超时），
     *    但分流动作统一收敛到平台 `RoutingEngine`；`AssetDownloadService` 严禁自建 CIO client、
     *    也严禁把 `http://127.0.0.1` 的 P2P 视频流送进仅支持 `https://` 的 `EchHttpClientEngine`；
     * 3. `crates/minibgm-torrent/src/server.rs` 必须开启 `overwrite: true` 并在初始化异常时 `cancel_token.cancel()`。
     */
    @Test
    fun ech_and_torrent_stream_boundaries_prevent_pool_poisoning_and_loopback_failures() {
        val violations = mutableListOf<String>()

        val echEngineFile =
            File(
                projectRoot,
                "core/network/src/androidMain/kotlin/com/infinitezerone/minibgm/core/network/ech/EchHttpClientEngine.kt",
            )
        assertTrue(echEngineFile.isFile, "EchHttpClientEngine.kt 未找到")
        val echContent = KotlinSourceScanner.sanitize(echEngineFile.readText())
        if (!echContent.contains("TimeoutCancellationException")) {
            violations.add(
                "EchHttpClientEngine.kt 捕获 CancellationException 时未区分 TimeoutCancellationException，正常协程取消会误杀 EchEdgePool 节点",
            )
        }

        val assetServiceFile =
            File(
                projectRoot,
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/AssetDownloadService.kt",
            )
        assertTrue(assetServiceFile.isFile, "AssetDownloadService.kt 未找到")
        val assetContent = KotlinSourceScanner.sanitize(assetServiceFile.readText())
        if (!assetContent.contains("isLoopbackHttpUrl")) {
            violations.add(
                "AssetDownloadService.kt 丢失 isLoopbackHttpUrl 判定，回环流将无法放宽 socket 读取超时",
            )
        }
        if (assetContent.contains("HttpClient(CIO)")) {
            violations.add(
                "AssetDownloadService.kt 不得自建 CIO client：回环分流必须统一由 RoutingEngine 负责",
            )
        }
        val routingEngineFile =
            File(
                projectRoot,
                "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/transport/RoutingEngine.kt",
            )
        assertTrue(routingEngineFile.isFile, "RoutingEngine.kt 未找到")
        val routingContent = KotlinSourceScanner.sanitize(routingEngineFile.readText())
        if (!routingContent.contains("URLProtocol.HTTP") || !routingContent.contains("plainEngine")) {
            violations.add(
                "RoutingEngine.kt 未把 http 协议路由到 plain 引擎：回环/明文流会被 EchHttpClientEngine 拒绝",
            )
        }

        val torrentServerFile = File(projectRoot, "crates/minibgm-torrent/src/server.rs")
        if (torrentServerFile.isFile) {
            val serverRs = torrentServerFile.readText()
            if (!serverRs.contains("overwrite: true")) {
                violations.add(
                    "crates/minibgm-torrent/src/server.rs AddTorrentOptions 缺少 overwrite: true，二次播放/下载同一剧集将报 File exists (os error 17)",
                )
            }
            if (!serverRs.contains("cancel_token.cancel()")) {
                violations.add("crates/minibgm-torrent/src/server.rs 缺少 cancel_token.cancel() 清理守卫")
            }
            if (!serverRs.contains("resolve_target_file_path")) {
                violations.add(
                    "crates/minibgm-torrent/src/server.rs 未暴露真实落盘路径 resolve_target_file_path，将导致离线下载退化为双倍磁盘写入",
                )
            }
        }

        val torrentRepoFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            )
        assertTrue(torrentRepoFile.isFile, "AndroidTorrentRepository.kt 未找到")
        val repoContent = KotlinSourceScanner.sanitize(torrentRepoFile.readText())
        if (!repoContent.contains("getActiveTargetFilePath") || !repoContent.contains("monitorDirectEngineDownload")) {
            violations.add(
                "AndroidTorrentRepository.kt 未使用 getActiveTargetFilePath + monitorDirectEngineDownload 直接复用 librqbit 落盘文件（严禁走二次 HTTP 拷贝双写）",
            )
        }
        if (!repoContent.contains("scheduleNextIfIdle") || !repoContent.contains("setDownloadSessionProtected")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少串行队列调度 scheduleNextIfIdle 或会话保护 setDownloadSessionProtected，多任务或退出播放器将互相掐断",
            )
        }
        if (!repoContent.contains("peerConnectTimeoutMs") || !repoContent.contains("PEER_CONNECT_TIMEOUT_MESSAGE")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少做种节点连接超时熔断守卫 peerConnectTimeoutMs / PEER_CONNECT_TIMEOUT_MESSAGE，死种或 UDP NAT 阻断将永久卡死串行队列",
            )
        }
        if (!repoContent.contains("isPreempting") || !repoContent.contains("withTimeout")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少抢占互斥状态守卫 isPreempting 或起播获取超时守卫 withTimeout，并发抢占将发生会话掐断或死种永久挂起",
            )
        }
        if (!repoContent.contains("torrentTaskDao") || !repoContent.contains("TorrentTaskDao")) {
            violations.add(
                "AndroidTorrentRepository.kt 必须依赖 TorrentTaskDao 进行 ACID 任务持久化，严禁裸文件 tasks_manifest.json 覆写作为主存储",
            )
        }
        if (repoContent.contains("persistTasksSnapshot")) {
            violations.add(
                "AndroidTorrentRepository.kt 严禁使用全量快照覆写 persistTasksSnapshot，高频进度不得落库，关键状态必须单行有序提交",
            )
        }
        if (!repoContent.contains("restoreDeferred") || !repoContent.contains("cancelAndJoin")) {
            violations.add(
                "AndroidTorrentRepository.kt 必须包含 restoreDeferred 启动门闩与 cancelAndJoin 停机同步守卫",
            )
        }

        val dataModuleAndroidFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/di/DataModule.android.kt",
            )
        assertTrue(dataModuleAndroidFile.isFile, "DataModule.android.kt 未找到")
        val dataModuleContent = KotlinSourceScanner.sanitize(dataModuleAndroidFile.readText())
        if (dataModuleContent.contains("getOrNull<TorrentTaskDao>()") ||
            dataModuleContent.contains("torrentTaskDao = getOrNull()")
        ) {
            violations.add(
                "DataModule.android.kt 中 torrentTaskDao 必须强定义注入 get()，严禁使用 getOrNull() 静默回退",
            )
        }

        val streamEngineFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            )
        assertTrue(streamEngineFile.isFile, "AndroidTorrentStreamEngine.kt 未找到")
        val engineContent = KotlinSourceScanner.sanitize(streamEngineFile.readText())
        if (!engineContent.contains("downloadSessionProtected") || !engineContent.contains("playerSessionActive")) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 缺少 downloadSessionProtected / playerSessionActive 双消费者生命周期协调守卫",
            )
        }

        val sourcesSheetFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt",
            )
        if (sourcesSheetFile.isFile) {
            val sheetContent = KotlinSourceScanner.sanitize(sourcesSheetFile.readText())
            if (sheetContent.contains("torrentStreamEngine.startStream(") ||
                sheetContent.contains("torrentStreamEngine.") ||
                !sheetContent.contains("torrentRepository.startStreamDownload(")
            ) {
                violations.add(
                    "SubjectSourcesBottomSheet.kt 必须完全面向 torrentRepository 调度边下边播与插件校验，严禁直接引用或调用 torrentStreamEngine.*",
                )
            }
        }

        assertTrue(
            violations.isEmpty(),
            "违反 ECH 优选池防污染与 BT 流播/下载单盘协同红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * JNI 符号绑定与流统计失败信号红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. Kotlin 侧 6 个 `external fun` 必须各自带 `@JvmStatic`：只有 `@JvmStatic` 会把 native 跳板
     *    生成在**外层类** `AndroidTorrentStreamEngine` 上（`$Companion` 只留转发器），符号名才成立为扁平的
     *    `Java_com_..._AndroidTorrentStreamEngine_nativeXxx`；漏掉即变成 `..._00024Companion_nativeXxx`，
     *    真机 `UnsatisfiedLinkError` 闪退——而单测与静态检查面对的都是预编译 `.so`，这条链路只能靠契约守住；
     * 2. Rust 侧必须逐个导出与之同名的符号（改名或漏一个都是同一场真机闪退）；
     * 3. Kotlin 侧必须保留 `stats.size < STATS_ARRAY_SIZE`（=5）的失败判据，且 Rust 侧无活跃会话时必须走
     *    `stats_to_jni` 的 `None` 分支返回长度不足的数组——严禁全零数组兜底：那会把"引擎失联"伪装成
     *    "健康的零进度"，`MAX_CONSECUTIVE_STAT_FAILURES` 永不计数，`streamState` 既不发射也不收敛 Error，
     *    下游 `first {}` 与死种看门狗双双无界挂起（任务永远停在 DOWNLOADING）；
     * 4. 第 5 位（下标 4）的引擎状态码与 `nativeGetLastError` 必须成对存在：librqbit 的
     *    `TorrentStatsState::Error`（磁盘写入失败 / 初始化校验失败）过去被折叠成"0 速度 0 Peer"，
     *    用户只能在死种看门狗超时后被告知「连不上做种节点」——真实原因被伪装成死种。
     */
    @Test
    fun jni_symbol_binding_and_stats_failure_signal_keep_kotlin_and_rust_in_lockstep() {
        val violations = mutableListOf<String>()

        val nativeMethods =
            listOf(
                "nativeIsAvailable",
                "nativeStartStream",
                "nativeStopStream",
                "nativeGetStreamStats",
                "nativeGetLastError",
                "nativeGetTargetFilePath",
            )

        val engineFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            )
        assertTrue(engineFile.isFile, "AndroidTorrentStreamEngine.kt 未找到")
        val engineContent = KotlinSourceScanner.sanitize(engineFile.readText())

        // @JvmStatic 必须紧贴各自的声明站点：只断言"文件里出现过 @JvmStatic"会在漏掉其中一个时静默放行。
        var cursor = 0
        nativeMethods.forEach { name ->
            val index = engineContent.indexOf("external fun $name", cursor)
            if (index < 0) {
                violations.add(
                    "AndroidTorrentStreamEngine.kt 缺少 external fun $name（Rust 侧符号将无人绑定）",
                )
                return@forEach
            }
            if (!engineContent.substring(cursor, index).contains("@JvmStatic")) {
                violations.add(
                    "AndroidTorrentStreamEngine.kt 的 external fun $name 缺少 @JvmStatic：" +
                        "native 会落在外层类之外，JNI 符号变为 ..._00024Companion_$name，真机 UnsatisfiedLinkError",
                )
            }
            cursor = index
        }

        if (!engineContent.contains("stats.size < STATS_ARRAY_SIZE")) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 缺少 stats.size < STATS_ARRAY_SIZE 失败判据：长度不足的统计数组会被当成有效值，引擎失联将不再收敛 Error",
            )
        }
        if (!engineContent.contains("STATS_ARRAY_SIZE = 5") || !engineContent.contains("STATS_INDEX_STATE = 4")) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 统计数组布局常量（长度 5 / 状态码下标 4）与 Rust 侧 stats_to_jni 失配",
            )
        }
        if (!engineContent.contains("STATS_STATE_ENGINE_ERROR") ||
            !engineContent.contains("getLastError") ||
            !engineContent.contains("resolveEngineErrorReason")
        ) {
            violations.add(
                "AndroidTorrentStreamEngine.kt 未消费引擎级错误状态码/错误原文：librqbit 判定种子 Error（磁盘写满、初始化校验失败）时只会表现为 0 速度，" +
                    "用户将在 45 秒后被死种看门狗告知「连不上做种节点」，真实原因被伪装成死种",
            )
        }

        val rustLibFile = File(projectRoot, "crates/minibgm-torrent/src/lib.rs")
        assertTrue(rustLibFile.isFile, "crates/minibgm-torrent/src/lib.rs 未找到")
        val rustLib = rustLibFile.readText()
        nativeMethods.forEach { name ->
            val symbol =
                "Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_$name"
            if (!rustLib.contains("pub extern \"system\" fn $symbol(")) {
                violations.add(
                    "crates/minibgm-torrent/src/lib.rs 未导出 $symbol：Kotlin 侧调用将 UnsatisfiedLinkError",
                )
            }
        }
        if (!rustLib.contains("fn stats_to_jni(") || !rustLib.contains("None => return empty_stats_array(&env)")) {
            violations.add(
                "crates/minibgm-torrent/src/lib.rs nativeGetStreamStats 缺少无会话失败路径（stats_to_jni 返回 None 必须落为长度 0 的数组），全零数组兜底会伪装成健康零进度",
            )
        }
        if (!rustLib.contains("-> Option<[i64; 5]>") || !rustLib.contains("env.new_long_array(5)")) {
            violations.add(
                "crates/minibgm-torrent/src/lib.rs 统计数组长度不是 5：Kotlin 侧按 STATS_ARRAY_SIZE=5 判失败，" +
                    "长度错位会让每次读取都被判为失败并误收敛 STATS_UNAVAILABLE_MESSAGE",
            )
        }
        if (!rustLib.contains("STATS_STATE_ENGINE_ERROR")) {
            violations.add(
                "crates/minibgm-torrent/src/lib.rs 未把引擎错误态编码进统计数组第 5 位：Kotlin 侧无从区分「引擎级致命错误」与「死种 0 速度」",
            )
        }
        val rustServerFile = File(projectRoot, "crates/minibgm-torrent/src/server.rs")
        assertTrue(rustServerFile.isFile, "crates/minibgm-torrent/src/server.rs 未找到")
        val rustServer = rustServerFile.readText()
        if (!rustServer.contains("TorrentStatsState::Error") || !rustServer.contains("engine_error")) {
            violations.add(
                "crates/minibgm-torrent/src/server.rs 未把 librqbit TorrentStatsState::Error 映射进 StreamStats：致命错误会退化成 0 速度 0 Peer",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反 JNI 符号绑定与流统计失败信号红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 播放优先红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 单实例 librqbit 会话下，后台离线下载起任何新任务都会 release 掉当前会话；若此时前台播放器
     * 正在消费该本地回环流（"看第 5 集时下第 6 集"），用户的画面会被直接掐断且全程无提示。
     * 因此下载调度必须同时具备两半，缺一不可：
     * 1. 礼让：播放器消费本地流期间保持 QUEUED（判据 `isPlayerSessionActive()`），
     *    只有用户显式点播（`allowPlayerTakeover`）才允许接管会话；
     * 2. 唤醒：订阅 `playerSessionActive` 流，在播放器释放会话时重新调度——只读一次布尔值感知不到
     *    "释放"，被礼让的任务会永久停在 QUEUED（通知栏挂着下载中却永远 0 字节）。
     * 契约侧必须把该状态暴露为可观察流，否则第 2 半无从实现。
     */
    @Test
    fun offline_download_must_yield_to_active_playback_session() {
        val violations = mutableListOf<String>()

        val repoFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            )
        assertTrue(repoFile.isFile, "AndroidTorrentRepository.kt 未找到")
        val repoContent = KotlinSourceScanner.sanitize(repoFile.readText())
        if (!repoContent.contains("isPlayerSessionActive")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少 isPlayerSessionActive 礼让判据：后台下载会在播放期间起新任务并掐断用户正在看的本地流",
            )
        }
        if (!repoContent.contains("allowPlayerTakeover")) {
            violations.add(
                "AndroidTorrentRepository.kt 缺少 allowPlayerTakeover 显式接管开关：用户主动点播的边下边播会被自己的礼让判据卡死到超时",
            )
        }
        if (!repoContent.contains("playerSessionActive.collect")) {
            violations.add(
                "AndroidTorrentRepository.kt 未订阅 playerSessionActive：播放器释放会话后无人唤醒，被礼让的任务将永久停在 QUEUED",
            )
        }

        val contractFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/download/TorrentStreamEngine.kt",
            )
        assertTrue(contractFile.isFile, "TorrentStreamEngine.kt 未找到")
        val contractContent = KotlinSourceScanner.sanitize(contractFile.readText())
        if (!contractContent.contains("val playerSessionActive: StateFlow<Boolean>")) {
            violations.add(
                "TorrentStreamEngine 契约未暴露 val playerSessionActive: StateFlow<Boolean>：调度侧无法感知播放会话释放，被礼让的排队任务只能在应用重启后消化",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反播放优先红线：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力文件挑选链路红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `server.rs` 必须以 `paused: true` 添加种子，并在锁定单集后调用 `session.unpause(&handle)`：
     *    否则元数据阶段会先按整包视频选片再收敛单集，合集/BD 包会白下其他剧集的分片；
     * 2. 视频扩展名白名单必须单一真源（`VIDEO_EXTENSIONS` 同时派生 `only_files_regex` 与 `is_video_file`），
     *    严禁出现第二份硬编码扩展名清单；
     * 3. 目标单集必须经 `select_target_video_file` 挑选，且多视频文件未命中集数时必须显式失败，
     *    严禁静默回退体积最大文件（合集/BD 包会稳定挑错集且全程无提示）。
     */
    @Test
    fun torrent_file_selection_locks_single_episode_before_starting_download() {
        val torrentServerFile = File(projectRoot, "crates/minibgm-torrent/src/server.rs")
        assertTrue(torrentServerFile.isFile, "crates/minibgm-torrent/src/server.rs 未找到")
        val serverRs = torrentServerFile.readText()
        // 单测里为锁定派生结果会显式写出期望正则字面量，漂移检查只看生产代码
        val productionRs = serverRs.substringBefore("#[cfg(test)]")

        val violations = mutableListOf<String>()
        if (!productionRs.contains("paused: true")) {
            violations.add(
                "server.rs 未以 paused: true 添加种子：元数据阶段会先按整包视频选片，合集/BD 包将白下其他剧集分片",
            )
        }
        if (!productionRs.contains(".unpause(&handle)")) {
            violations.add(
                "server.rs 锁定单集后未调用 session.unpause(&handle)，paused 添加将导致永不开始下载",
            )
        }
        if (!productionRs.contains("VIDEO_EXTENSIONS")) {
            violations.add("server.rs 视频扩展名白名单未收口到 VIDEO_EXTENSIONS 单一真源")
        }
        if (productionRs.contains("mkv|mp4|webm|avi|flv|ts|m2ts")) {
            violations.add("server.rs 存在第二份硬编码视频扩展名清单，会与 VIDEO_EXTENSIONS 静默漂移")
        }
        if (!productionRs.contains("select_target_video_file") || !productionRs.contains("未能按集数")) {
            violations.add(
                "server.rs 必须经 select_target_video_file 挑选目标单集，并对多视频未命中集数显式失败（严禁静默回退体积最大文件）",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力文件挑选链路红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力分集隔离与种子载荷校验红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `AndroidTorrentRepository.startDownload` 去重必须同时比对分集身份键 `episodeKey`：
     *    合集/BD 包内所有分集共用同一磁力，只比 infoHash 会让点第 N 集命中第 M 集的既有任务，
     *    直接复用其 `streamUrl`（或已完成任务的 `file://` 落盘路径），静默播错集且全程无提示；
     * 2. `AndroidTorrentStreamEngine.startStream` 会话复用必须比对 `activeEpisodeHint`：
     *    同磁力换集必须重新解析目标文件，否则复用上一集的流与 `targetFile`；
     * 3. `SubjectSourcesBottomSheet` 的已完成任务快捷复用必须同样比对 `episodeKey`；
     * 4. 种子（`.torrent`）载荷必须经 `TorrentFileCache` 校验：镜像返回 HTTP 200 的 HTML 错误页时
     *    `AssetDownloader` 只按字节数判成功，缓存命中若只看「文件名 + 非空」会让坏种子被永久复用
     *    （离线下载还会把它写进 DB 的 `torrentFilePath`，重启后继续命中）；
     * 5. 起播等待超时必须以 `Result.failure` 返回：`getOrThrow()` 抛出的 `CancellationException`
     *    会连带取消调用方协程，UI 的 `onFailure` 永不执行，用户全程无任何提示。
     */
    @Test
    fun torrent_episode_isolation_and_payload_validation_guards_are_enforced() {
        val torrentRepoFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            )
        val streamEngineFile =
            File(
                projectRoot,
                "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            )
        val sheetFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt",
            )
        assertTrue(torrentRepoFile.isFile, "AndroidTorrentRepository.kt 未找到")
        assertTrue(streamEngineFile.isFile, "AndroidTorrentStreamEngine.kt 未找到")
        assertTrue(sheetFile.isFile, "SubjectSourcesBottomSheet.kt 未找到")

        val repoContent = KotlinSourceScanner.sanitize(torrentRepoFile.readText())
        val engineContent = KotlinSourceScanner.sanitize(streamEngineFile.readText())
        val sheetContent = KotlinSourceScanner.sanitize(sheetFile.readText())

        val violations = mutableListOf<String>()
        if (!repoContent.contains("task.episodeKey == incomingEpisodeKey")) {
            violations.add(
                "AndroidTorrentRepository.startDownload 去重缺少分集身份键比对（episodeKey）：合集/BD 包内换集将复用上一集的 streamUrl 或 file:// 落盘路径，静默播错集",
            )
        }
        if (!repoContent.contains("TorrentFileCache.isValidPayload(") ||
            !repoContent.contains("TorrentFileCache.isUsableCachedFile(")
        ) {
            violations.add(
                "AndroidTorrentRepository 下载/复用种子文件未经 TorrentFileCache 校验载荷：镜像 HTML 错误页会被当作成功种子永久复用",
            )
        }
        // runTaskPipeline 从 DB 取回的 torrentFilePath 同样必须校验：坏种子一旦写进 DB，
        // 每次启动都会把该路径直接喂给引擎，坏种子永久复现（用户只能清应用数据自救）。
        val pipelineStart = repoContent.indexOf("private suspend fun runTaskPipeline")
        val pipelineEnd = repoContent.indexOf("private suspend fun monitorDirectEngineDownload")
        if (pipelineStart < 0 || pipelineEnd <= pipelineStart) {
            violations.add(
                "AndroidTorrentRepository.runTaskPipeline 结构锚点失效：红线无法定位种子路径复用逻辑，请同步更新红线",
            )
        } else if (!repoContent.substring(pipelineStart, pipelineEnd).contains("TorrentFileCache.isValidPayload(")) {
            violations.add(
                "AndroidTorrentRepository.runTaskPipeline 直接复用 DB 中的 torrentFilePath 而未校验载荷：失效或坏种子会被永久复用",
            )
        }
        if (repoContent.contains("getOrThrow()")) {
            violations.add(
                "AndroidTorrentRepository 使用 getOrThrow() 表达业务失败：CancellationException 会连带取消调用方协程，UI onFailure 永不执行",
            )
        }
        if (!repoContent.contains("catch (e: TimeoutCancellationException)") ||
            !repoContent.contains("STREAM_ACQUISITION_TIMEOUT_MESSAGE")
        ) {
            violations.add(
                "AndroidTorrentRepository.startStreamDownload 缺少起播等待超时守卫：死种将无界挂起或直接取消调用方协程",
            )
        }
        // 用词边界锚定标识符本体：改名（activeEpisodeHintX）或只留注释都不得蒙混过关
        if (Regex("\\bactiveEpisodeHint\\b").findAll(engineContent).count() < 4 ||
            !engineContent.contains("activeEpisodeHint == normalizedEpisodeHint") ||
            !engineContent.contains("activeEpisodeHint = null")
        ) {
            violations.add(
                "AndroidTorrentStreamEngine 会话复用缺少 activeEpisodeHint 声明/比对/赋值/重置四处闭环：同磁力换集将复用上一集的会话与 targetFile",
            )
        }
        if (!engineContent.contains("TorrentFileCache.isUsableCachedFile(")) {
            violations.add(
                "AndroidTorrentStreamEngine 预取/复用本地种子文件未经 TorrentFileCache 校验载荷，坏种子会随 DB 路径被永久复用",
            )
        }
        // 选源面板既有任务复用判据：锚定 `completedTaskFor` 的「分集身份键 + 源同一性」双判据本体。
        // 曾经锚定旧的 `task.episodeKey == requestedEpisodeKey` 字面量——面板重构把比对收进
        // `completedTaskFor` 后该字面量消失，红线必须以结构（函数 + 两处判据）而非旧写法为锚，
        // 否则「重命名/抽函数」这类等价重构会被误判为违规，而真正的判据删失又会漏检。
        if (!sheetContent.contains("fun completedTaskFor(") ||
            !sheetContent.contains("task.episodeKey ==") ||
            !sheetContent.contains("matchesResourceSource(resource)")
        ) {
            violations.add(
                "SubjectSourcesBottomSheet 已完成任务快捷复用缺少「分集身份键 + 源同一性」双判据：点第 N 集会直接播第 M 集已落盘的文件，或让不同来源互相复用落盘路径",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力分集隔离与种子载荷校验红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力链路「交互与业务逻辑」红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. 分集检索未命中只允许回退到合集项（`episodeSort == null`），严禁回退整份 Feed：
     *    否则请求第 5 集会看到第 1-4 集，点单文件资源还会被 Rust 侧「单一候选即选中」静默播成第 1 集；
     * 2. 多规则结果合并去重必须走 `TorrentMediaResource.dedupeKey`（infoHash → magnetUrl → torrentUrl → title）：
     *    只按 `infoHash.ifBlank { magnetUrl }` 判重时，未携带 40 位哈希的 `.torrent` 直链条目会集体退化为
     *    空串，`distinctBy` 只保留第一条，静默丢掉同 Feed 的其余条目；
     * 3. 条目级（未选分集）动作严禁硬编码假集数（`?: 1f`）：那会把所有条目级资源塌缩成同一个
     *    `sort:1.0` 身份键，并把 `episodeHint="1"` 喂给 Rust 的分集选片逻辑；必须回退资源自身 `episodeSort`；
     * 4. 离线下载（后台大任务）必须经 `BgmOverlayHost` 显式确认并披露体积/可用空间；
     * 5. 流统计轮询一次失败严禁 `break`（会永久中断状态流，下游 `first{}` 与死种看门狗双双无界挂起），
     *    必须容忍瞬时失败并只在连续失败超阈值时收敛为 `Error`；
     * 6. 流进度必须经 `TorrentRepository.streamState` 暴露给 UI，feature 层严禁直引 `TorrentStreamEngine`。
     */
    @Test
    fun torrent_ui_and_business_guards_enforce_confirmation_dedupe_and_stream_feedback() {
        val violations = mutableListOf<String>()

        fun read(relativePath: String): String {
            val file = File(projectRoot, relativePath)
            assertTrue(file.isFile, "$relativePath 未找到")
            return KotlinSourceScanner.sanitize(file.readText())
        }

        // 1. 分集未命中只回退合集项
        val feedRepo = read("core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentFeedRepository.kt")
        if (feedRepo.contains("matched.isNotEmpty()")) {
            violations.add(
                "TorrentFeedRepository.filterAndSort 用 matched.isNotEmpty() 回退了整份 Feed：分集未命中会让用户看到并点到别的集",
            )
        }
        if (!feedRepo.contains("resources.filter { it.episodeSort == null }")) {
            violations.add(
                "TorrentFeedRepository.filterAndSort 缺少「只回退合集项（episodeSort == null）」的兜底",
            )
        }

        // 2. 去重键必须收敛到 dedupeKey
        listOf(
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectDetailViewModel.kt",
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/EpisodeDetailViewModel.kt",
        ).forEach { path ->
            val content = read(path)
            if (content.contains("infoHash.ifBlank")) {
                violations.add(
                    "$path 仍在就地拼 `infoHash.ifBlank { magnetUrl }` 去重键：无哈希的 .torrent 直链条目会被静默丢弃，改用 dedupeKey",
                )
            }
            if (!content.contains("distinctBy { it.dedupeKey }")) {
                violations.add("$path 未使用 TorrentMediaResource.dedupeKey 做去重")
            }
        }

        // 3~5. 磁力 UI 与流引擎
        val sheet =
            read("feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt")
        if (Regex("\\?:\\s*1f").containsMatchIn(sheet)) {
            violations.add(
                "SubjectSourcesBottomSheet 仍把条目级动作硬编码成「第 1 话」（?: 1f）：身份键会塌缩、episodeHint 会喂假集数",
            )
        }
        if (!sheet.contains("resource.episodeSort")) {
            violations.add("SubjectSourcesBottomSheet 未回退资源自身 episodeSort 作为分集身份")
        }
        val downloadStart = sheet.indexOf("suspend fun downloadTorrentResource")
        val downloadEnd = sheet.indexOf("private suspend fun ensureTorrentPluginInstalled")
        if (downloadStart < 0 || downloadEnd <= downloadStart) {
            violations.add("SubjectSourcesBottomSheet.downloadTorrentResource 结构锚点失效，请同步更新红线")
        } else {
            val downloadBody = sheet.substring(downloadStart, downloadEnd)
            if (!downloadBody.contains("overlayHostState.await(")) {
                violations.add(
                    "离线下载未走 BgmOverlayHost 显式确认：后台大任务严禁静默入队",
                )
            }
            if (!downloadBody.contains("feature_subject_source_torrent_download_confirm_title") ||
                !downloadBody.contains("getStorageUsage()")
            ) {
                violations.add("离线下载确认弹窗未披露资源体积与设备可用空间")
            }
            val confirmIndex = downloadBody.indexOf("overlayHostState.await(")
            val startIndex = downloadBody.indexOf("startDownload(")
            if (confirmIndex < 0 || startIndex < 0 || confirmIndex > startIndex) {
                violations.add("离线下载的 startDownload 必须晚于用户确认（当前确认未前置于落库调用）")
            }
        }

        val streamEngine =
            read("core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt")
        if (streamEngine.contains("getStreamStats() }.getOrNull() ?: break")) {
            violations.add(
                "AndroidTorrentStreamEngine 流统计轮询仍以 `?: break` 永久中断状态流：下游 first{} 与死种看门狗会无界挂起",
            )
        }
        if (!streamEngine.contains("MAX_CONSECUTIVE_STAT_FAILURES")) {
            violations.add("AndroidTorrentStreamEngine 缺少连续失败阈值，瞬时抖动与真实故障无法区分")
        }

        // 6. 流状态只经仓库契约暴露
        val repoContract = read("core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentRepository.kt")
        if (!repoContract.contains("val streamState: StateFlow<TorrentStreamState>")) {
            violations.add("TorrentRepository 未暴露只读 streamState，UI 无法呈现起播进度")
        }
        // 播放来源面板只允许经仓库契约消费流进度（引擎控制面另有既定入口，不在此红线范围）
        val sheetPath = "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        if (sheet.contains("import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine")) {
            violations.add("$sheetPath 直引 TorrentStreamEngine：流进度只能经 TorrentRepository.streamState 消费")
        }
        if (!sheet.contains("torrentRepository.streamState")) {
            violations.add("$sheetPath 未消费 TorrentRepository.streamState，起播进度与失败原因对用户不可见")
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力交互与业务逻辑红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 磁力实现收敛红线（见 AGENTS.md 领域规则 & 剧本 8）：
     * 1. `infoHash` 解析必须收敛到 `:core:model` 的 `magnetInfoHash` 唯一真源：
     *    历史上引擎 / 仓库 / 缓存 / UI 四处各写一遍，引擎侧还降级成 `"unknown_hash"` 哨兵、
     *    其余处返回空串——同一语义两套降级，是"未知磁力互相误判为同一源"的温床；
     * 2. 传输速率与体积格式化只允许走 `formatTransferSpeed` / `DownloadStorageUsage.formatBytes`
     *    真源，严禁页面内再手搓 `KB/s` 分支（副本漂移会让同一数值在两处显示不一致）；
     * 3. 种子缓存键的直链指纹必须是强摘要（SHA-256 截断），严禁 `hashCode().toHexString()`
     *    这种 32 位可碰撞的弱哈希——碰撞会让两个不同种子命中同一缓存文件，而载荷校验只验
     *    bencode 结构不验身份，会接受"合法但错误"的种子；
     * 4. 「停止起播」必须真停底层任务（经 `onTaskQueued` 拿到 taskId → `pauseDownload`）：
     *    只 `busyJob?.cancel()` 会让任务继续占着单实例 P2P 队列，并在下载页留下用户从未创建过的条目；
     * 5. 既有任务比对必须含 `torrentUrl` 兜底：只比 `magnetUrl` 时 torrentUrl-only 资源两侧都是空串，
     *    `"" == ""` 会互相误命中并复用错误的流地址或落盘路径。
     */
    @Test
    fun torrent_implementation_consolidation_guards_are_enforced() {
        val violations = mutableListOf<String>()
        val infoHashSourceOfTruth =
            "core/model/src/commonMain/kotlin/com/infinitezerone/minibgm/core/model/TorrentMediaResource.kt"

        fun raw(relativePath: String): String {
            val file = File(projectRoot, relativePath)
            assertTrue(file.isFile, "$relativePath 未找到")
            return file.readText()
        }

        fun code(relativePath: String): String = KotlinSourceScanner.sanitize(raw(relativePath))

        // 1. infoHash 解析唯一真源（原始文本扫描：违规形态必然落在字符串字面量里，sanitize() 会抹掉字符串）
        val handrolledParse = "substringAfter(\"xt=urn:btih:\""
        listOf("core", "feature", "app", "sync").forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val rel = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    if (rel != infoHashSourceOfTruth && file.readText().contains(handrolledParse)) {
                        violations.add("$rel 重复手写 infoHash 解析，必须调用 :core:model 的 magnetInfoHash 唯一真源")
                    }
                }
        }
        if (!code(infoHashSourceOfTruth).contains("fun magnetInfoHash(")) {
            violations.add("$infoHashSourceOfTruth 缺少 magnetInfoHash 唯一真源")
        }

        // 2. 哨兵降级彻底退场（引擎曾用 "unknown_hash"，其余处返回空串）
        listOf(
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/AndroidTorrentStreamEngine.kt",
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/TorrentFileCache.kt",
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt",
            "core/testing/src/commonMain/kotlin/com/infinitezerone/minibgm/core/testing/repository/FakeTorrentStreamEngine.kt",
        ).forEach { path ->
            if (raw(path).contains("unknown_hash")) {
                violations.add("$path 仍残留 infoHash 哨兵降级 unknown_hash：未知源必须统一以空串 + isBlank 判定")
            }
        }

        // 3. 速率 / 体积格式化唯一真源
        val downloadScreen = "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/DownloadScreen.kt"
        if (!code(downloadScreen).contains("formatTransferSpeed(")) {
            violations.add("$downloadScreen 未消费 formatTransferSpeed 真源")
        }
        if (raw(downloadScreen).contains("KB/s") || raw(downloadScreen).contains("MB/s")) {
            violations.add("$downloadScreen 内联手搓速率单位，必须调用 formatTransferSpeed")
        }
        val torrentModels = "core/model/src/commonMain/kotlin/com/infinitezerone/minibgm/core/model/TorrentModels.kt"
        if (!code(torrentModels).contains("fun formatTransferSpeed(")) {
            violations.add("$torrentModels 缺少 formatTransferSpeed 真源")
        }
        if (!code(torrentModels).contains("fun formatDownloadProgressPair(")) {
            violations.add("$torrentModels 缺少 formatDownloadProgressPair 真源（已下载/总量进度文案）")
        }
        if (!code(torrentModels).contains("DownloadStorageUsage.formatBytes")) {
            violations.add("$torrentModels 体积格式化未收敛到 DownloadStorageUsage.formatBytes 真源")
        }
        val downloadModels = "core/model/src/commonMain/kotlin/com/infinitezerone/minibgm/core/model/DownloadModels.kt"
        listOf("formattedSpeed", "formattedProgress").forEach { duplicate ->
            if (code(downloadModels).contains(duplicate)) {
                violations.add(
                    "$downloadModels 仍残留 $duplicate 重复格式化实现：速率与体积格式化只能有 :core:model 的单一真源",
                )
            }
        }
        val rssParser = "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/resolver/TorrentRssParser.kt"
        if (!code(rssParser).contains("DownloadStorageUsage.formatBytes(")) {
            violations.add("$rssParser 体积格式化未收敛到 DownloadStorageUsage.formatBytes 真源")
        }

        // 4. 种子缓存键强摘要
        val cache = "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/download/TorrentFileCache.kt"
        if (!raw(cache).contains("SHA-256") || !code(cache).contains("MessageDigest")) {
            violations.add("$cache 种子缓存键未使用 SHA-256 强摘要")
        }
        if (code(cache).contains("toHexString(")) {
            violations.add("$cache 仍用 toHexString(hashCode()) 弱指纹做缓存键：32 位碰撞会命中错误种子")
        }

        // 5. 停止起播必须真停任务 + 源同一性必须含 torrentUrl
        val sheetPath =
            "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt"
        val sheet = code(sheetPath)
        if (!sheet.contains("pauseDownload(")) {
            violations.add("$sheetPath 停止起播未调用 pauseDownload：只取消等待协程会让任务继续占着 P2P 队列")
        }
        if (!sheet.contains("onTaskQueued")) {
            violations.add("$sheetPath 未接住 onTaskQueued 回调：拿不到 taskId 就无法真正停止底层任务")
        }
        if (sheet.contains("onCancelBusy")) {
            violations.add("$sheetPath 仍暴露 onCancelBusy 旧语义：停止起播的语义是真停任务，不是只取消等待")
        }
        if (!sheet.contains("matchesResourceSource") || !sheet.contains("torrentUrl")) {
            violations.add("$sheetPath 既有任务比对缺少 torrentUrl 兜底：torrentUrl-only 资源会以空磁力互相误命中")
        }
        val repoContract =
            "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentRepository.kt"
        if (!code(repoContract).contains("onTaskQueued")) {
            violations.add("$repoContract.startStreamDownload 未声明 onTaskQueued：UI 无法在任务落库后拿到身份")
        }
        val repoImpl =
            "core/data/src/androidMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AndroidTorrentRepository.kt"
        if (!code(repoImpl).contains("onTaskQueued")) {
            violations.add("$repoImpl 未实现 onTaskQueued 契约")
        }

        assertTrue(
            violations.isEmpty(),
            "违反磁力实现收敛红线（见 AGENTS.md 领域规则 & 剧本 8）：\n" + violations.joinToString("\n"),
        )
    }

    /**
     * 应用内更新下载的可见性红线（见 AGENTS.md 领域规则 & 剧本 12）：
     * 更新 APK 是数十 MB 的后台大任务，而设置页长期只弹一次固定文案「正在下载新版本… 0%」——
     * 真实进度（`DownloadState.Downloading`）与取消入口（`UpdateRepository.cancelDownload` /
     * `UserViewModel.cancelUpdateDownload`）整条链路都已实现且状态一直在发射，却没有任何消费者：
     * 用户既看不到进度也无法中止，只能杀进程，而固定文案还在持续宣称一个假的 0%。
     *
     * 机械判据：
     * 1. 进度必须来自真实状态（`DownloadState.Downloading` + `percentage`），且未知总量
     *    （`isIndeterminate`，服务端未给 Content-Length）不得当成 0%；
     * 2. 必须提供取消入口（`cancelUpdateDownload(`）；
     * 3. 严禁把百分比硬编码成常量传给下载中文案（原始文本扫描，字符串字面量在 sanitize 后会被抹掉）。
     */
    @Test
    fun update_flow_must_surface_real_progress_and_cancel() {
        val relativePath = "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/SettingsScreen.kt"
        val file = File(projectRoot, relativePath)
        assertTrue(file.isFile, "$relativePath 未找到")
        val raw = file.readText()
        val code = KotlinSourceScanner.sanitize(raw)
        val violations = mutableListOf<String>()

        if (!code.contains("DownloadState.Downloading")) {
            violations.add("$relativePath 未消费 DownloadState.Downloading：更新下载对用户无进度")
        }
        if (!code.contains("isIndeterminate")) {
            violations.add("$relativePath 未区分未知总量（isIndeterminate）：无 Content-Length 时会显示假的 0%")
        }
        if (!code.contains("percentage")) {
            violations.add("$relativePath 未使用进度百分比：更新下载进度不可见")
        }
        if (!code.contains("cancelUpdateDownload(")) {
            violations.add("$relativePath 未提供取消入口：后台大任务无法中止，用户只能杀进程")
        }
        val hardcodedPercent = Regex("feature_user_update_downloading[^)]*\"\\d+%\"")
        if (hardcodedPercent.containsMatchIn(raw)) {
            violations.add(
                "$relativePath 仍把更新下载进度硬编码成固定百分比：文案必须由 DownloadState.Downloading 驱动",
            )
        }

        assertTrue(
            violations.isEmpty(),
            "违反应用内更新下载可见性红线（见 AGENTS.md 领域规则 & 剧本 12）：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun core_data_and_feature_subject_sources_must_not_hardcode_proprietary_torrent_domains() {
        val violations = mutableListOf<String>()
        val checkedDirs = listOf("core/data", "core/network", "feature/subject")
        val forbiddenDomain = "mikanani.me"

        checkedDirs.forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    if (text.contains(forbiddenDomain)) {
                        violations.add(
                            "${file.relativeTo(projectRoot).path} 违规硬编码专用种子站点域名 $forbiddenDomain，必须使用通用的 TorrentFeedRepository 与规则驱动体系",
                        )
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "违反 P2P 种子协议中立性红线（核心数据层与详情层严禁硬编码特定第三方站点域名）：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun production_sources_must_not_inject_hardcoded_public_trackers() {
        val violations = mutableListOf<String>()
        val checkedDirs = listOf("core", "feature", "app", "sync")
        // 原始文本扫描：违规形态必然落在字符串字面量里（sanitize() 会抹掉字符串，此处不可用）。
        val forbiddenTokens =
            listOf(
                "&tr=" to "磁链 Tracker 查询参数",
                "/announce" to "Tracker announce 端点",
            )

        checkedDirs.forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    forbiddenTokens.forEach { (token, label) ->
                        if (text.contains(token)) {
                            violations.add(
                                "${file.relativeTo(projectRoot).path} 违规硬编码公共 $label（$token）",
                            )
                        }
                    }
                }
        }

        assertTrue(
            violations.isEmpty(),
            "违反 P2P 零第三方广播红线（严禁硬编码公共 Tracker 注入请求，寻道只走磁链自带声明与 DHT）：\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * 隐私与安全隔离红线：
     * 开源仓库中严禁硬编码任何开发者个人私有域名、网关端点（如私有反代、私人 DDNS 域名）；
     * OAuth 令牌兑换与弹幕网关等端点必须由 CI（GitHub Secrets）或本地 local.properties 注入，
     * 避免个人凭据与域名随开源代码泄漏。
     */
    @Test
    fun production_sources_must_not_hardcode_private_proxy_domains() {
        val violations = mutableListOf<String>()
        val checkedDirs = listOf("core", "feature", "app", "sync")
        val forbiddenDomains = listOf("zero" + "nex", "dpdns" + ".org")

        checkedDirs.forEach { dirPath ->
            val dir = File(projectRoot, dirPath)
            if (!dir.isDirectory) return@forEach
            dir
                .walkTopDown()
                .onEnter { it.name !in NON_SOURCE_DIR_NAMES }
                .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") && it.name != "ArchitectureRulesTest.kt" }
                .forEach { file ->
                    val text = file.readText()
                    forbiddenDomains.forEach { domain ->
                        if (text.contains(domain, ignoreCase = true)) {
                            violations.add(
                                "${file.relativeTo(projectRoot).path} 违规硬编码私有代理域名 $domain，必须由 CI / local.properties 注入",
                            )
                        }
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "违反私有网关与域名隔离红线（严禁在源码中硬编码私有端点）：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun image_loader_routing_must_fail_open_for_unknown_third_party_hosts() {
        val moduleFile =
            File(
                projectRoot,
                "app/src/main/kotlin/com/infinitezerone/minibgm/di/ImageLoaderModule.kt",
            )
        assertTrue(moduleFile.isFile, "ImageLoaderModule.kt 未找到")
        val content = KotlinSourceScanner.sanitize(moduleFile.readText())

        val violations = mutableListOf<String>()

        // 未知域名必须 fail-open：严禁在 execute 内同步阻塞调用 isEchSupported
        if (content.contains("EchCapabilityDetector.isEchSupported(host")) {
            violations.add("ImageRoutingEngine 在主请求路径违规同步调用了 isEchSupported，阻断了首图立即加载")
        }

        // 必须通过 probeAsyncIfUnknown 异步探测
        if (!content.contains("EchCapabilityDetector.probeAsyncIfUnknown(host)")) {
            violations.add("ImageRoutingEngine 缺少 probeAsyncIfUnknown(host) 异步探测，未知图床无法自愈升级走 ECH")
        }

        // 超时与异常必须降级且标记冷却，不能因通用 CancellationException 误抛导致断流
        if (!content.contains("TimeoutCancellationException")) {
            violations.add("ImageRoutingEngine 必须捕获 TimeoutCancellationException 降级至 fallbackEngine，避免将超时误当做外部取消而中断图片加载")
        }
        if (!content.contains("EchCapabilityDetector.markEchFailure(host)")) {
            violations.add("ImageRoutingEngine 降级时必须调用 EchCapabilityDetector.markEchFailure(host) 记录冷却，避免后续图片持续卡顿 4 秒")
        }

        assertTrue(
            violations.isEmpty(),
            "违反第三方图床 Fail-Open 路由红线：\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun torrent_resource_card_must_not_expose_technical_dual_source_badges_and_use_lean_actions() {
        val sheetFile =
            File(
                projectRoot,
                "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt",
            )
        assertTrue(sheetFile.isFile, "SubjectSourcesBottomSheet.kt 未找到")
        val rawContent = sheetFile.readText()
        val content = KotlinSourceScanner.sanitize(rawContent)

        val violations = mutableListOf<String>()

        // 1. 严禁展示"双源可用"这类给用户制造认知负担的抓取技术细节徽章
        if (rawContent.contains("feature_subject_source_torrent_channel_dual") ||
            content.contains("resource.isDualSource")
        ) {
            violations.add("SubjectSourcesBottomSheet 违规暴露了技术细节徽标「双源可用」，增加了用户的认知负担")
        }

        // 2. 种子卡片必须具备 TorrentFeedDiskCache 磁盘缓存契约
        val repoFile =
            File(
                projectRoot,
                "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/TorrentFeedRepository.kt",
            )
        assertTrue(repoFile.isFile, "TorrentFeedRepository.kt 未找到")
        val repoContent = repoFile.readText()
        if (!repoContent.contains("TorrentFeedDiskCache")) {
            violations.add("TorrentFeedRepository 缺少 TorrentFeedDiskCache 磁盘持久化缓存支持，无法保证离线秒开")
        }

        assertTrue(
            violations.isEmpty(),
            "违反种子资源卡片去噪与持久化缓存红线：\n" + violations.joinToString("\n"),
        )
    }

    /** 手工构造 ViewModel 的 Koin 块；`{ params -> ... }`、`{ (arg: T) -> ... }` 与单行写法都要覆盖 */
}
