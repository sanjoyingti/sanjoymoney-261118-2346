package com.jaiho.sss.in

import android.app.Application
import com.jaiho.sss.in.bot.BotEngine
import com.jaiho.sss.in.data.SettingsStore
import com.jaiho.sss.in.service.OverlayService
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Application) {

    val appContext: Application = context
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store: SettingsStore = SettingsStore(context)
    val engine: BotEngine = BotEngine(context, store)

    fun startOverlay(): Boolean {
        if (!AndroidSettings.canDrawOverlays(appContext)) return false
        if (OverlayService.running.value) return true
        val intent = Intent(appContext, OverlayService::class.java)
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
        return true
    }

    fun stopOverlay() {
        runCatching { appContext.stopService(Intent(appContext, OverlayService::class.java)) }
    }
}

class SanjoyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
