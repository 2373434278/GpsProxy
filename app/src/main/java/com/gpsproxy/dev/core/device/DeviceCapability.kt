package com.gpsproxy.dev.core.device

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.gpsproxy.dev.core.i18n.StringsHolder

data class CapabilityIssue(
    val index: Int,
    val text: String,
    val code: String,
)

enum class ContainerSupport {
    SUPPORTED,
    EXPERIMENTAL,
    UNSUPPORTED,
}

data class DeviceCapability(
    val supported: Boolean,
    val issues: List<CapabilityIssue>,
    val apiLevel: Int,
    val abiLabel: String,
    val model: String,
    val manufacturer: String,
    val display: String,
    val containerSupport: ContainerSupport,
) {
    val summary: String
        get() = "$manufacturer $model · Android $apiLevel · $abiLabel"
}

object DeviceCapabilityChecker {

    fun check(): DeviceCapability {
        val issues = mutableListOf<CapabilityIssue>()
        var index = 1
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        if (!is64Bit) {
            issues += CapabilityIssue(
                index = index++,
                text = StringsHolder.text { onboarding.issueNo64Bit },
                code = "ENG-0102",
            )
        }
        val harmonyLike = Build.BRAND.contains("harmony", ignoreCase = true) ||
            Build.MANUFACTURER.contains("harmony", ignoreCase = true)
        if (harmonyLike && !is64Bit) {
            issues += CapabilityIssue(
                index = index,
                text = StringsHolder.text { onboarding.issueHarmony },
                code = "ENG-0102",
            )
        }
        val abis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { StringsHolder.text { onboarding.unknownAbi } }
        val containerSupport = when {
            issues.isNotEmpty() -> ContainerSupport.UNSUPPORTED
            Build.VERSION.SDK_INT <= 34 -> ContainerSupport.SUPPORTED
            else -> ContainerSupport.EXPERIMENTAL
        }
        return DeviceCapability(
            supported = issues.isEmpty(),
            issues = issues,
            apiLevel = Build.VERSION.SDK_INT,
            abiLabel = abis,
            model = Build.MODEL ?: "Android",
            manufacturer = Build.MANUFACTURER ?: "",
            display = Build.DISPLAY ?: "",
            containerSupport = containerSupport,
        )
    }
}

data class PermissionSnapshot(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
    val mockLocation: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val locationGranted: Boolean get() = fineLocation || coarseLocation
    val readyCount: Int
        get() = listOf(locationGranted, notifications, overlay, mockLocation, batteryUnrestricted)
            .count { it }
    val total: Int get() = 5
    val essentialReady: Boolean get() = locationGranted && mockLocation
}

object PermissionUtils {

    fun snapshot(context: Context): PermissionSnapshot = PermissionSnapshot(
        fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED,
        coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED,
        notifications = notificationsEnabled(context),
        overlay = Settings.canDrawOverlays(context),
        mockLocation = isMockLocationAllowed(context),
        batteryUnrestricted = batteryUnrestricted(context),
    )

    fun isMockLocationAllowed(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    context.applicationInfo.uid,
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    context.applicationInfo.uid,
                    context.packageName,
                )
            }
        } catch (_: Exception) {
            AppOpsManager.MODE_ERRORED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppDetails(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
    }

    fun openDeveloperOptions(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    fun openOverlaySettings(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            },
        )
    }

    fun openNotificationSettings(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        )
    }

    @android.annotation.SuppressLint("BatteryLife") // 本工具需要前台服务常驻，属产品既定用法
    fun openBatterySettings(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (!start(context, request)) {
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
