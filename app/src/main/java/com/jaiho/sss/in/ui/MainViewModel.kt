package com.jaiho.sss.in.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jaiho.sss.in.AppContainer
import com.jaiho.sss.in.data.BotStats
import com.jaiho.sss.in.data.Settings
import com.jaiho.sss.in.service.OverlayService
import com.jaiho.sss.in.util.SystemUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermsState(
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val battery: Boolean = false
) {
    val complete: Boolean get() = overlay && accessibility && battery
}

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val ctx: Context = container.appContext

    val settings: StateFlow<Settings> = container.store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), Settings())

    val stats: StateFlow<BotStats> = container.store.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), BotStats())

    val log: StateFlow<List<String>> = container.engine.log
    val active: StateFlow<Boolean> = container.engine.active
    val connected: StateFlow<Boolean> = container.engine.connected
    val overlayRunning: StateFlow<Boolean> = OverlayService.running

    private val _perms = MutableStateFlow(PermsState())
    val perms: StateFlow<PermsState> = _perms.asStateFlow()

    private val _installed = MutableStateFlow(false)
    val gameInstalled: StateFlow<Boolean> = _installed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _perms.value = PermsState(
            overlay = SystemUtils.isOverlayGranted(ctx),
            accessibility = SystemUtils.isAccessibilityEnabled(ctx),
            battery = SystemUtils.isBatteryIgnored(ctx)
        )
        _installed.value = SystemUtils.isGameInstalled(ctx)
    }

    fun openGame(): Boolean {
        refresh()
        return SystemUtils.launchGame(ctx)
    }

    fun openPlayStore() {
        val market = SystemUtils.playStoreIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(market) }
            .onFailure {
                runCatching { ctx.startActivity(SystemUtils.webStoreIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
    }

    fun copyPackage() {
        val manager = ctx.getSystemService(ClipboardManager::class.java) ?: return
        manager.setPrimaryClip(ClipData.newPlainText("package", SystemUtils.GAME_PACKAGE))
    }

    fun openOverlaySettings() = SystemUtils.openOverlaySettings(ctx)

    fun openAccessibilitySettings() = SystemUtils.openAccessibilitySettings(ctx)

    fun openBatterySettings() = SystemUtils.openBatterySettings(ctx)

    fun startBot(): Boolean {
        refresh()
        if (!_perms.value.complete) return false
        if (!container.startOverlay()) return false
        container.engine.setAutoSpin(true)
        return true
    }

    fun stopBot() {
        container.engine.setAutoSpin(false)
        container.stopOverlay()
    }

    fun startOverlayForCalibration(): Boolean {
        refresh()
        if (!_perms.value.overlay) return false
        container.startOverlay()
        OverlayService.requestCalibration()
        return true
    }

    private fun save(block: (Settings) -> Settings) {
        viewModelScope.launch { container.store.update(block) }
    }

    fun setAutoSpin(value: Boolean) {
        save { it.copy(autoSpin = value) }
        container.engine.setAutoSpin(value)
    }

    fun setAlwaysWin(value: Boolean) = save { it.copy(alwaysWin = value) }

    fun setJackpotHunter(value: Boolean) = save { it.copy(jackpotHunter = value) }

    fun setStealth(value: Boolean) = save { it.copy(stealth = value) }

    fun setVibrate(value: Boolean) = save { it.copy(vibrate = value) }

    fun setJackpotToast(value: Boolean) = save { it.copy(jackpotToast = value) }

    fun setKeepScreenOn(value: Boolean) = save { it.copy(keepScreenOn = value) }

    fun setAutoStartWithGame(value: Boolean) = save { it.copy(autoStartWithGame = value) }

    fun setStartOnBoot(value: Boolean) = save { it.copy(startOnBoot = value) }

    fun setLogVerbosity(value: Int) = save { it.copy(logVerbosity = value.coerceIn(0, 2)) }

    fun setInterval(value: Int) =
        save { it.copy(intervalMs = value.coerceIn(Settings.MIN_INTERVAL, Settings.MAX_INTERVAL)) }

    fun stepBet(delta: Int) =
        save { it.copy(betLevel = (it.betLevel + delta).coerceIn(Settings.MIN_BET, Settings.MAX_BET)) }

    fun resetCounters() = container.engine.resetStats()

    fun clearLog() = container.engine.clearLog()

    fun restoreDefaults() {
        viewModelScope.launch { container.store.restoreDefaults() }
    }

    fun resetEverything() {
        viewModelScope.launch {
            container.engine.setAutoSpin(false)
            container.stopOverlay()
            container.store.resetAll()
            container.engine.clearLog()
            container.engine.resetStats()
            refresh()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(container) }
        }
    }
}
