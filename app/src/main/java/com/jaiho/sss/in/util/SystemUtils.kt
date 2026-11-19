package com.jaiho.sss.`in`.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import com.jaiho.sss.`in`.R
import com.jaiho.sss.`in`.service.BotAccessibilityService

object SystemUtils {

    /** The real game: its package and its launcher activity inside the Cocos shell. */
    const val GAME_PACKAGE = "com.jaiho.sss.in"
    const val GAME_ACTIVITY = "org.cocos2dx.javascript.AppActivity"

    val GAME_PACKAGES = listOf(GAME_PACKAGE, "com.jaiho.sss")

    const val VERSION = "0.9.69"

    enum class LaunchOutcome { GAME, STORE, NOT_FOUND }

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

    fun isGameInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in GAME_PACKAGES) {
            val found = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }.isSuccess
            if (found) return true
        }
        return runCatching { pm.getLaunchIntentForPackage(GAME_PACKAGE) }.getOrNull() != null
    }

    /**
     * Opens the real Jaiho 777.
     * Order: explicit activity -> normal launcher intent -> store page -> toast with hint.
     */
    fun launchGame(context: Context): LaunchOutcome {
        val explicit = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(GAME_PACKAGE, GAME_ACTIVITY)
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (startSafely(context, explicit)) return LaunchOutcome.GAME

        for (pkg in GAME_PACKAGES) {
            val launch = runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (startSafely(context, launch)) return LaunchOutcome.GAME
            }
        }

        if (startSafely(context, playStoreIntent())) return LaunchOutcome.STORE
        if (startSafely(context, webStoreIntent())) return LaunchOutcome.STORE

        Toast.makeText(
            context,
            context.getString(R.string.game_missing_toast),
            Toast.LENGTH_LONG
        ).show()
        return LaunchOutcome.NOT_FOUND
    }

    private fun startSafely(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess

    fun playStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GAME_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun webStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GAME_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
