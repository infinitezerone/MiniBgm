package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

object WidgetImageLoader {
    private const val TAG = "WidgetImageLoader"
    private const val THUMB_WIDTH_PX = 150
    private const val THUMB_HEIGHT_PX = 210
    private const val CORNER_RADIUS_PX = 16f
    private const val LOAD_TIMEOUT_MS = 6000L

    /**
     * 异步从本地缓存或网络加载微缩封面 Bitmap。
     * 为避免 IPC Binder 1MB 事务超限 (TransactionTooLargeException)，
     * 对图片进行了精准的微缩下采样（150x210 像素，标准 1:1.4 动画海报比例），
     * 并且直接在 Bitmap 上进行抗锯齿圆角裁剪，确保在所有 OEM 桌面 launcher 下均呈现完美圆角。
     */
    suspend fun loadThumbnailBitmap(
        context: Context,
        url: String,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val normalizedUrl =
                when {
                    url.startsWith("//") -> "https:$url"
                    url.startsWith("http://") -> "https://" + url.removePrefix("http://")
                    else -> url
                }

            // 1. 本地磁盘缓存快速命中（零网络、零延迟）
            val cacheDir = File(context.cacheDir, "widget_covers").apply { if (!exists()) mkdirs() }
            val cacheKey = md5(normalizedUrl)
            val cacheFile = File(cacheDir, "$cacheKey.cover")

            if (cacheFile.exists() && cacheFile.length() > 0) {
                decodeAndProcessBitmap(cacheFile.absolutePath)?.let { return@withContext it }
            }

            // 2. 优先通过 Coil 3 加载（可命中 Coil 的内存缓存或其 250MB 磁盘缓存）
            val coilBitmap =
                withTimeoutOrNull(LOAD_TIMEOUT_MS.milliseconds) {
                    runCatching {
                        val imageLoader = SingletonImageLoader.get(context)
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(normalizedUrl)
                                .size(THUMB_WIDTH_PX, THUMB_HEIGHT_PX)
                                .scale(Scale.FILL)
                                .allowHardware(false) // 桌面小组件 RemoteViews 严禁使用硬件加速 Bitmap
                                .build()
                        val result = imageLoader.execute(request)
                        if (result is SuccessResult) {
                            val drawable = result.image.asDrawable(context.resources)
                            val raw =
                                drawable.toBitmap(
                                    width = THUMB_WIDTH_PX,
                                    height = THUMB_HEIGHT_PX,
                                    config = Bitmap.Config.ARGB_8888,
                                )
                            // 保存到小组件独立缓存供下次离线快速命中
                            runCatching {
                                FileOutputStream(cacheFile).use { out ->
                                    raw.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            }
                            roundCorners(raw, CORNER_RADIUS_PX)
                        } else {
                            Log.w(TAG, "Coil returned non-success for $normalizedUrl")
                            null
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Coil failed to load $normalizedUrl", e)
                    }.getOrNull()
                }

            if (coilBitmap != null) return@withContext coilBitmap

            // 3. 原生 HttpURLConnection 独立直连兜底（支持 HTTP/HTTPS 协议间重定向追踪）
            val directBitmap =
                withTimeoutOrNull(LOAD_TIMEOUT_MS.milliseconds) {
                    runCatching {
                        downloadDirect(context, normalizedUrl, cacheFile)
                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            decodeAndProcessBitmap(cacheFile.absolutePath)
                        } else {
                            null
                        }
                    }.onFailure { e ->
                        Log.e(TAG, "Direct download failed for $normalizedUrl", e)
                    }.getOrNull()
                }

            directBitmap
        }

    private fun getUserAgent(context: Context): String {
        val versionName =
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "1.0.0"
        return "MiniBgm/$versionName (android) (https://github.com/infinitezerone/MiniBgm)"
    }

    private fun downloadDirect(
        context: Context,
        urlStr: String,
        targetFile: File,
    ) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        var currentUrl = urlStr
        var redirects = 0
        val userAgent = getUserAgent(context)

        while (redirects < 3) {
            val conn =
                (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", "image/*")
                }
            try {
                conn.connect()
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl =
                            if (location.startsWith("http://") || location.startsWith("https://")) {
                                location
                            } else {
                                URL(URL(currentUrl), location).toString()
                            }
                        redirects++
                        continue
                    }
                }
                if (code == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                    }
                    break
                } else {
                    Log.w(TAG, "Direct fetch HTTP $code for $currentUrl")
                    break
                }
            } finally {
                conn.disconnect()
            }
        }
        if (tempFile.exists()) tempFile.delete()
    }

    private fun decodeAndProcessBitmap(filePath: String): Bitmap? =
        runCatching {
            // 先只读尺寸以计算 inSampleSize
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(filePath, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var inSampleSize = 1
            while (origWidth / (inSampleSize * 2) >= THUMB_WIDTH_PX &&
                origHeight / (inSampleSize * 2) >= THUMB_HEIGHT_PX
            ) {
                inSampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = BitmapFactory.decodeFile(filePath, options) ?: return null
            val scaled = decoded.scale(THUMB_WIDTH_PX, THUMB_HEIGHT_PX)
            if (scaled != decoded) {
                decoded.recycle()
            }
            roundCorners(scaled, CORNER_RADIUS_PX)
        }.onFailure { e ->
            Log.e(TAG, "Failed to decode cached bitmap: $filePath", e)
        }.getOrNull()

    /**
     * 为 Bitmap 裁剪抗锯齿圆角。
     * 彻底解决 Android 桌面小组件在部分 ROM / Launcher 下无法 clipToOutline 的问题。
     */
    private fun roundCorners(
        src: Bitmap,
        cornerRadiusPx: Float,
    ): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }
        val rect = Rect(0, 0, src.width, src.height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, rect, rect, paint)
        if (output != src) {
            src.recycle()
        }
        return output
    }

    private fun md5(input: String): String =
        runCatching {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrDefault(input.hashCode().toString())
}
