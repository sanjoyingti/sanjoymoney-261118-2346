package com.jaiho.sss.in.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sanjoy_money")

private object K {
    val autoSpin = booleanPreferencesKey("auto_spin")
    val alwaysWin = booleanPreferencesKey("always_win")
    val jackpotHunter = booleanPreferencesKey("jackpot_hunter")
    val stealth = booleanPreferencesKey("stealth")
    val interval = intPreferencesKey("interval")
    val bet = intPreferencesKey("bet")
    val vibrate = booleanPreferencesKey("vibrate")
    val jackpotToast = booleanPreferencesKey("jackpot_toast")
    val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    val autoStartWithGame = booleanPreferencesKey("auto_start_with_game")
    val startOnBoot = booleanPreferencesKey("start_on_boot")
    val verbosity = intPreferencesKey("verbosity")
    val spinX = floatPreferencesKey("spin_x")
    val spinY = floatPreferencesKey("spin_y")
    val collectX = floatPreferencesKey("collect_x")
    val collectY = floatPreferencesKey("collect_y")
    val spinCalibrated = booleanPreferencesKey("spin_calibrated")
    val collectCalibrated = booleanPreferencesKey("collect_calibrated")
    val spins = intPreferencesKey("stat_spins")
    val wins = intPreferencesKey("stat_wins")
    val near = intPreferencesKey("stat_near")
    val jackpots = intPreferencesKey("stat_jackpots")
    val biggest = doublePreferencesKey("stat_biggest")
}

private fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        autoSpin = this[K.autoSpin] ?: d.autoSpin,
        alwaysWin = this[K.alwaysWin] ?: d.alwaysWin,
        jackpotHunter = this[K.jackpotHunter] ?: d.jackpotHunter,
        stealth = this[K.stealth] ?: d.stealth,
        intervalMs = (this[K.interval] ?: d.intervalMs).coerceIn(Settings.MIN_INTERVAL, Settings.MAX_INTERVAL),
        betLevel = (this[K.bet] ?: d.betLevel).coerceIn(Settings.MIN_BET, Settings.MAX_BET),
        vibrate = this[K.vibrate] ?: d.vibrate,
        jackpotToast = this[K.jackpotToast] ?: d.jackpotToast,
        keepScreenOn = this[K.keepScreenOn] ?: d.keepScreenOn,
        autoStartWithGame = this[K.autoStartWithGame] ?: d.autoStartWithGame,
        startOnBoot = this[K.startOnBoot] ?: d.startOnBoot,
        logVerbosity = this[K.verbosity] ?: d.logVerbosity,
        spinX = this[K.spinX] ?: d.spinX,
        spinY = this[K.spinY] ?: d.spinY,
        collectX = this[K.collectX] ?: d.collectX,
        collectY = this[K.collectY] ?: d.collectY,
        spinCalibrated = this[K.spinCalibrated] ?: d.spinCalibrated,
        collectCalibrated = this[K.collectCalibrated] ?: d.collectCalibrated
    )
}

private fun MutablePreferences.write(s: Settings) {
    this[K.autoSpin] = s.autoSpin
    this[K.alwaysWin] = s.alwaysWin
    this[K.jackpotHunter] = s.jackpotHunter
    this[K.stealth] = s.stealth
    this[K.interval] = s.intervalMs
    this[K.bet] = s.betLevel
    this[K.vibrate] = s.vibrate
    this[K.jackpotToast] = s.jackpotToast
    this[K.keepScreenOn] = s.keepScreenOn
    this[K.autoStartWithGame] = s.autoStartWithGame
    this[K.startOnBoot] = s.startOnBoot
    this[K.verbosity] = s.logVerbosity
    this[K.spinX] = s.spinX
    this[K.spinY] = s.spinY
    this[K.collectX] = s.collectX
    this[K.collectY] = s.collectY
    this[K.spinCalibrated] = s.spinCalibrated
    this[K.collectCalibrated] = s.collectCalibrated
}

class SettingsStore(context: Context) {

    private val data = context.applicationContext.dataStore

    val settings: Flow<Settings> = data.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    val stats: Flow<BotStats> = data.data
        .catch { emit(emptyPreferences()) }
        .map {
            BotStats(
                spins = it[K.spins] ?: 0,
                wins = it[K.wins] ?: 0,
                nearMisses = it[K.near] ?: 0,
                jackpots = it[K.jackpots] ?: 0,
                biggestWin = it[K.biggest] ?: 0.0
            )
        }

    suspend fun update(block: (Settings) -> Settings) {
        data.edit { prefs ->
            prefs.write(block(prefs.toSettings()))
        }
    }

    suspend fun saveStats(s: BotStats) {
        data.edit { prefs ->
            prefs[K.spins] = s.spins
            prefs[K.wins] = s.wins
            prefs[K.near] = s.nearMisses
            prefs[K.jackpots] = s.jackpots
            prefs[K.biggest] = s.biggestWin
        }
    }

    suspend fun resetStats() {
        data.edit { prefs ->
            prefs[K.spins] = 0
            prefs[K.wins] = 0
            prefs[K.near] = 0
            prefs[K.jackpots] = 0
            prefs[K.biggest] = 0.0
        }
    }

    suspend fun restoreDefaults() {
        data.edit { prefs ->
            val current = prefs.toSettings()
            val fresh = Settings()
            prefs.write(
                fresh.copy(
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
            )
        }
    }

    suspend fun resetAll() {
        data.edit { it.clear() }
    }
}
