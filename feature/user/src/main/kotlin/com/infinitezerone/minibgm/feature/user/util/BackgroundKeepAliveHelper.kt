package com.infinitezerone.minibgm.feature.user.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.infinitezerone.minibgm.core.common.bgmLogger

/**
 * 后台保活与自启动引导工具：
 * 1. 检查与请求忽略系统电池优化（Doze / 待机对齐白名单）；
 * 2. 智能探测国内主流定制系统（ColorOS / HyperOS / OriginOS / EMUI 等）自启动与后台管理页并一键跳转；
 * 3. 失败时平滑降级至标准应用详细信息页。
 */
object BackgroundKeepAliveHelper {
    private val log = bgmLogger("Bgm/BackgroundKeepAlive")

    /**
     * 检查是否已经处于系统忽略电池优化白名单中。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求忽略电池优化（加入白名单）。
     * 优先尝试直接弹窗授权（ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）；
     * 若受系统限制或无响应，降级打开电池优化列表页。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val packageName = context.packageName
        val requestIntent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        val success = runCatching { context.startActivity(requestIntent) }.isSuccess
        if (!success) {
            val listIntent =
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            runCatching { context.startActivity(listIntent) }.onFailure { e ->
                log.e(e) { "Failed to open battery optimization settings" }
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * 尝试跳转主流国产定制 ROM 的「自启动管理」或「后台管理」页面。
     */
    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val packageName = context.packageName

        val candidateIntents = mutableListOf<Intent>()

        // 1. OPPO / OnePlus / Realme (ColorOS)
        if (manufacturer.contains("oppo") || manufacturer.contains("oneplus") || brand.contains("realme")) {
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.oplus.battery",
                            "com.oplus.powermanager.fuelga设置.PowerConsumptionActivity",
                        )
                },
            )
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                        )
                },
            )
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.startupapp.StartupAppListActivity",
                        )
                },
            )
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.oplus.safecenter",
                            "com.oplus.safecenter.startupapp.StartupAppListActivity",
                        )
                },
            )
        }

        // 2. 小米 / Redmi / POCO (HyperOS / MIUI)
        if (manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        )
                },
            )
        }

        // 3. 华为 / 荣耀 (HarmonyOS / EMUI / MagicOS)
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity",
                        )
                },
            )
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                        )
                },
            )
        }

        // 4. vivo / iQOO (OriginOS / FuntouchOS)
        if (manufacturer.contains("vivo") || brand.contains("iqoo")) {
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                        )
                },
            )
            candidateIntents.add(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                        )
                },
            )
        }

        // 5. 通用自启动 Action
        candidateIntents.add(
            Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            },
        )

        // 尝试按优先级拉起
        for (intent in candidateIntents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                log.i { "Successfully launched OEM autostart settings via ${intent.component}" }
                return
            }
        }

        // 若上述所有特定厂商入口均未匹配或无法打开，降级打开应用详细信息页
        log.d { "No specific OEM autostart page matched, fallback to application details" }
        openAppDetailsSettings(context)
    }

    /**
     * 打开系统应用信息详情页（应用可以从这里直接点击「耗电管理」、「权限管理」等）。
     */
    fun openAppDetailsSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        runCatching { context.startActivity(intent) }.onFailure { e ->
            log.e(e) { "Failed to open application details settings" }
        }
    }
}
