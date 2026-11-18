package com.jaiho.sss.`in`.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "sanjoy_money"

private object K {
    const val AUTO_SPIN = "auto_spin"
    const val ALWAYS_WIN = "always_win"
    const val JACKPOT_HUNTER = "jackpot_hunter"
    const val STEALTH = "stealth"
    const val INTERVAL = "interval"
    const val BET = "bet"
    const val VIBRATE = "vibrate"
    const val JACKPOT_TOAST = "jackpot_toast"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val AUTO_START_WITH_GAME = "auto_start_with_game"
    const val START_ON_BOOT = "start_on_boot"
    const val VERBOSITY = "verbosity"
    const val SPIN_X = "spin_x"
    const val SPIN_Y = "spin_y"
    const val COLLECT_X = "collect_x"
    const val COLLECT_Y = "collect_y"
    const val SPIN_CALIBRATED = "spin_calibrated"
    const val COLLECT_CALIBRATED = "collect_calibrated"
    const val SPINS = "stat_spins"
    const val WINS = "stat_wins"
    const val NEAR = "stat_near"
    const val JACKPOTS = "stat_jackpots"
    const val BIGGEST = "stat_biggest"
}

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<BotStats> = _stats.asStateFlow()

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            autoSpin = prefs.getBoolean(K.AUTO_SPIN, d.autoSpin),
            alwaysWin = prefs.getBoolean(K.ALWAYS_WIN, d.alwaysWin),
            jackpotHunter = prefs.getBoolean(K.JACKPOT_HUNTER, d.jackpotHunter),
            stealth = prefs.getBoolean(K.STEALTH, d.stealth),
            intervalMs = prefs.getInt(K.INTERVAL, d.intervalMs)
                .coerceIn(Settings.MIN_INTERVAL, Settings.MAX_INTERVAL),
            betLevel = prefs.getInt(K.BET, d.betLevel)
                .coerceIn(Settings.MIN_BET, Settings.MAX_BET),
            vibrate = prefs.getBoolean(K.VIBRATE, d.vibrate),
            jackpotToast = prefs.getBoolean(K.JACKPOT_TOAST, d.jackpotToast),
            keepScreenOn = prefs.getBoolean(K.KEEP_SCREEN_ON, d.keepScreenOn),
            autoStartWithGame = prefs.getBoolean(K.AUTO_START_WITH_GAME, d.autoStartWithGame),
            startOnBoot = prefs.getBoolean(K.START_ON_BOOT, d.startOnBoot),
            logVerbosity = prefs.getInt(K.VERBOSITY, d.logVerbosity).coerceIn(0, 2),
            spinX = prefs.getFloat(K.SPIN_X, d.spinX).coerceIn(0f, 1f),
            spinY = prefs.getFloat(K.SPIN_Y, d.spinY).coerceIn(0f, 1f),
            collectX = prefs.getFloat(K.COLLECT_X, d.collectX).coerceIn(0f, 1f),
            collectY = prefs.getFloat(K.COLLECT_Y, d.collectY).coerceIn(0f, 1f),
            spinCalibrated = prefs.getBoolean(K.SPIN_CALIBRATED, d.spinCalibrated),
            collectCalibrated = prefs.getBoolean(K.COLLECT_CALIBRATED, d.collectCalibrated)
        )
    }

    private fun loadStats(): BotStats = BotStats(
        spins = prefs.getInt(K.SPINS, 0),
        wins = prefs.getInt(K.WINS, 0),
        nearMisses = prefs.getInt(K.NEAR, 0),
        jackpots = prefs.getInt(K.JACKPOTS, 0),
        biggestWin = prefs.getFloat(K.BIGGEST, 0f).toDouble()
    )

    private fun persist(s: Settings) {
        prefs.edit()
            .putBoolean(K.AUTO_SPIN, s.autoSpin)
            .putBoolean(K.ALWAYS_WIN, s.alwaysWin)
            .putBoolean(K.JACKPOT_HUNTER, s.jackpotHunter)
            .putBoolean(K.STEALTH, s.stealth)
            .putInt(K.INTERVAL, s.intervalMs.coerceIn(Settings.MIN_INTERVAL, Settings.MAX_INTERVAL))
            .putInt(K.BET, s.betLevel.coerceIn(Settings.MIN_BET, Settings.MAX_BET))
            .putBoolean(K.VIBRATE, s.vibrate)
            .putBoolean(K.JACKPOT_TOAST, s.jackpotToast)
            .putBoolean(K.KEEP_SCREEN_ON, s.keepScreenOn)
            .putBoolean(K.AUTO_START_WITH_GAME, s.autoStartWithGame)
            .putBoolean(K.START_ON_BOOT, s.startOnBoot)
            .putInt(K.VERBOSITY, s.logVerbosity.coerceIn(0, 2))
            .putFloat(K.SPIN_X, s.spinX.coerceIn(0f, 1f))
            .putFloat(K.SPIN_Y, s.spinY.coerceIn(0f, 1f))
            .putFloat(K.COLLECT_X, s.collectX.coerceIn(0f, 1f))
            .putFloat(K.COLLECT_Y, s.collectY.coerceIn(0f, 1f))
            .putBoolean(K.SPIN_CALIBRATED, s.spinCalibrated)
            .putBoolean(K.COLLECT_CALIBRATED, s.collectCalibrated)
            .apply()
    }

    private fun persistStats(s: BotStats) {
        prefs.edit()
            .putInt(K.SPINS, s.spins)
            .putInt(K.WINS, s.wins)
            .putInt(K.NEAR, s.nearMisses)
            .putInt(K.JACKPOTS, s.jackpots)
            .putFloat(K.BIGGEST, s.biggestWin.toFloat())
            .apply()
    }

    suspend fun update(block: (Settings) -> Settings) {
        val next = block(_settings.value)
        persist(next)
        _settings.value = next
    }

    suspend fun saveStats(s: BotStats) {
        persistStats(s)
        _stats.value = s
    }

    suspend fun resetStats() {
        val fresh = BotStats()
        persistStats(fresh)
        _stats.value = fresh
    }

    suspend fun restoreDefaults() {
        val current = _settings.value
        val next = Settings().copy(
            spinX = current.spinX,
            spinY = current.spinY,
            collectX = current.collectX,
            collectY = current.collectY,
            spinCalibrated = current.spinCalibrated,
            collectCalibrated = current.collectCalibrated,
            keepScreenOn = current.keepScreenOn,
            autoStartWithGame = current.autoStartWithGame,
            startOnBoot = current.startOnBoot,
            logVerbosity = current.logVerbosity
        )
        persist(next)
        _settings.value = next
    }

    suspend fun resetAll() {
        prefs.edit().clear().apply()
        val freshSettings = Settings()
        val freshStats = BotStats()
        _settings.value = freshSettings
        _stats.value = freshStats
    }
}
