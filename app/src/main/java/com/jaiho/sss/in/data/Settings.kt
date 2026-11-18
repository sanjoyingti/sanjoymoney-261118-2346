package com.jaiho.sss.`in`.data

data class Settings(
    val autoSpin: Boolean = false,
    val alwaysWin: Boolean = true,
    val jackpotHunter: Boolean = true,
    val stealth: Boolean = true,
    val intervalMs: Int = 900,
    val betLevel: Int = 1,
    val vibrate: Boolean = true,
    val jackpotToast: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoStartWithGame: Boolean = false,
    val startOnBoot: Boolean = false,
    val logVerbosity: Int = 1,
    val spinX: Float = 0.5f,
    val spinY: Float = 0.85f,
    val collectX: Float = 0.5f,
    val collectY: Float = 0.6f,
    val spinCalibrated: Boolean = false,
    val collectCalibrated: Boolean = false
) {
    companion object {
        const val MIN_INTERVAL = 400
        const val MAX_INTERVAL = 3000
        const val MIN_BET = 1
        const val MAX_BET = 5
    }
}

data class BotStats(
    val spins: Int = 0,
    val wins: Int = 0,
    val nearMisses: Int = 0,
    val jackpots: Int = 0,
    val biggestWin: Double = 0.0
)
