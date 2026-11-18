package com.jaiho.sss.in.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import com.jaiho.sss.in.service.BotAccessibilityService

object SystemUtils {

    val GAME_PACKAGES = listOf("com.jaiho.sss.in", "com.jaiho.sss")
    const val GAME_PACKAGE = "com.jaiho.sss.in"
    const val VERSION = "0.9.69"

    fun isOverlayGranted(context: Context): Boolean = AndroidSettings.canDrawOverlays(context)

    fun isBatteryIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BotAccessibilityService::class.java)
        val short = "${context.packageName}/.service.BotAccessibilityService"
        val enabled = runCatching {
            AndroidSettings.Secure.getString(
                context.contentResolver,
                AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false
        return enabled.split(':').any { it.equals(expected.flattenToString(), true) || it.equals(short, true) }
    }

    fun isGameInstalled(context: Context): Boolean = gameLaunchIntent(context) != null

    fun gameLaunchIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (pkg in GAME_PACKAGES) {
            val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (intent != null && pkg != context.packageName) return intent
        }
        return runCatching { pm.getLaunchIntentForPackage(GAME_PACKAGE) }.getOrNull()
    }

    fun launchGame(context: Context): Boolean {
        val intent = gameLaunchIntent(context) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun playStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GAME_PACKAGE"))

    fun webStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GAME_PACKAGE"))

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION)) } }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= 33
}
