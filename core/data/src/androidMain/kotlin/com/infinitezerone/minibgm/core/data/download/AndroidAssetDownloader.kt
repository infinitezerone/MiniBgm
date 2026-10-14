package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.DownloadProgress
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.network.AssetDownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

class AndroidAssetDownloader(
    private val assetDownloadService: AssetDownloadService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AssetDownloader {
    private val logger = bgmLogger("Bgm/AssetDownloader")

    private val pathLocks = ConcurrentHashMap<String, Mutex>()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)

    @Deprecated("建议直接使用 download(...) 返回的独立 Flow，避免多任务共享状态流导致的互相覆写")
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private fun getLockForPath(path: String): Mutex {
        val canonicalKey = runCatching { File(path).canonicalPath }.getOrDefault(File(path).absolutePath)
        return pathLocks.computeIfAbsent(canonicalKey) { Mutex() }
    }

    override fun download(
        rawUrl: String,
        destinationPath: String,
        customMirrorPrefix: String?,
    ): Flow<DownloadState> =
        flow {
            val candidateUrls = ReleaseMirrorResolver.resolveCandidateUrls(rawUrl, customMirrorPrefix)
            if (candidateUrls.isEmpty()) {
                val failure = DownloadState.Failed("无效的下载地址: $rawUrl")
                _downloadState.value = failure
                emit(failure)
                return@flow
            }

            val destFile = File(destinationPath)
            destFile.parentFile?.mkdirs()
            val tempFile = File("$destinationPath$DOWNLOAD_TEMP_SUFFIX")

            val pathLock = getLockForPath(destinationPath)
            pathLock.withLock {
                var lastError: Throwable? = null
                var lastErrorMessage = ""

                for (candidateUrl in candidateUrls) {
                    if (!currentCoroutineContext().isActive) {
                        return@withLock
                    }

                    logger.d { "尝试下载节点: $candidateUrl (原始: $rawUrl)" }

                    try {
                        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

                        val raf = RandomAccessFile(tempFile, "rw")
                        var currentDownloaded = existingBytes
                        var finalTotalBytes = -1L
                        var lastProgressEmitTime = 0L
                        var lastSpeedCalcTime = System.currentTimeMillis()
                        var bytesSinceLastSpeedCalc = 0L
                        var currentSpeed = 0L

                        var isFirstChunk = true
                        try {
                            assetDownloadService.streamAsset(
                                url = candidateUrl,
                                rangeStart = existingBytes,
                            ) { chunk, length, totalBytes, isPartial ->
                                if (isFirstChunk) {
                                    isFirstChunk = false
                                    if (!isPartial) {
                                        // 服务端不支持断点续传（返回 200 OK 全量），重置文件从头写入
                                        raf.setLength(0L)
                                        raf.seek(0L)
                                        currentDownloaded = 0L
                                    } else {
                                        // 服务端返回 206 Partial Content，定位到断点处继续追加
                                        raf.seek(existingBytes)
                                        currentDownloaded = existingBytes
                                    }
                                }

                                raf.write(chunk, 0, length)
                                currentDownloaded += length
                                finalTotalBytes = totalBytes
                                bytesSinceLastSpeedCalc += length

                                val now = System.currentTimeMillis()
                                if (now - lastSpeedCalcTime >= 1000) {
                                    val elapsed = now - lastSpeedCalcTime
                                    currentSpeed = (bytesSinceLastSpeedCalc * 1000) / elapsed
                                    bytesSinceLastSpeedCalc = 0L
                                    lastSpeedCalcTime = now
                                }

                                if (now - lastProgressEmitTime >= 100 || (finalTotalBytes > 0L && currentDownloaded == finalTotalBytes)) {
                                    lastProgressEmitTime = now
                                    val progressState =
                                        DownloadState.Downloading(
                                            DownloadProgress(
                                                bytesDownloaded = currentDownloaded,
                                                totalBytes = finalTotalBytes,
                                                speedBytesPerSec = currentSpeed,
                                            ),
                                        )
                                    _downloadState.value = progressState
                                    emit(progressState)
                                }
                            }
                        } finally {
                            raf.close()
                        }

                        if (finalTotalBytes > 0L && currentDownloaded < finalTotalBytes) {
                            throw IOException("下载流未传输完毕 (已传 $currentDownloaded / 总 $finalTotalBytes)")
                        }

                        // 下载成功，执行原子替换
                        if (destFile.exists()) {
                            destFile.delete()
                        }
                        if (!tempFile.renameTo(destFile)) {
                            tempFile.copyTo(destFile, overwrite = true)
                            tempFile.delete()
                        }

                        val successState =
                            DownloadState.Completed(
                                filePath = destFile.absolutePath,
                                totalBytes = currentDownloaded,
                            )
                        _downloadState.value = successState
                        logger.i { "资产下载成功: ${destFile.absolutePath} ($currentDownloaded 字节)" }
                        emit(successState)
                        return@withLock
                    } catch (e: CancellationException) {
                        logger.d { "下载被取消: $candidateUrl" }
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        lastErrorMessage = e.message ?: "下载异常"
                        logger.w { "节点 $candidateUrl 下载失败: $lastErrorMessage，尝试下一个镜像" }
                    }
                }

                val finalFailure =
                    DownloadState.Failed(
                        message = "所有加速镜像与官方直链均尝试失败: $lastErrorMessage",
                        error = lastError,
                    )
                _downloadState.value = finalFailure
                emit(finalFailure)
            }
        }.flowOn(ioDispatcher)

    override suspend fun downloadAsset(
        rawUrl: String,
        destinationPath: String,
        customMirrorPrefix: String?,
    ): DownloadState =
        withContext(ioDispatcher) {
            download(rawUrl, destinationPath, customMirrorPrefix)
                .filter { it is DownloadState.Completed || it is DownloadState.Failed }
                .first()
        }

    @Deprecated("建议通过取消 collect 所在的 CoroutineScope 终止下载，避免无参数全局取消误伤其他任务")
    override fun cancelDownload() {
        _downloadState.value = DownloadState.Idle
    }
}
