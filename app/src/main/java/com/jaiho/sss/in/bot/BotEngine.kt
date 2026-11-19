package com.jaiho.sss.`in`.bot

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.jaiho.sss.`in`.R
import com.jaiho.sss.`in`.data.BotStats
import com.jaiho.sss.`in`.data.Settings
import com.jaiho.sss.`in`.data.SettingsStore
import com.jaiho.sss.`in`.service.BotAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BotEngine(private val context: Context, private val store: SettingsStore) {

    private companion object {
        const val MIN_TAP_GAP = 250L
        const val JACKPOT_GAP = 2500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _stats = MutableStateFlow(BotStats())
    val stats: StateFlow<BotStats> = _stats.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _jackpot = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val jackpotEvents: SharedFlow<String> = _jackpot.asSharedFlow()

    @Volatile private var settings: Settings = Settings()
    @Volatile private var service: BotAccessibilityService? = null
    @Volatile var panelOpen: Boolean = false

    private var loopJob: Job? = null
    private var lastTapAt = 0L
    private var lastJackpotAt = 0L
    private var previousBalance: Double? = null
    private var sessionStart: Double? = null
    private var lossStreak = 0
    private var slowSpins = 0

    init {
        scope.launch {
            store.settings.collectLatest { latest -> settings = latest }
        }
        scope.launch {
            _stats.value = store.stats.first()
            settings = store.settings.first()
        }
    }

    fun attach(service: BotAccessibilityService) {
        this.service = service
        _connected.value = true
        logLine(context.getString(R.string.log_engine_ready))
        if (_active.value) startLoop()
    }

    fun detach(service: BotAccessibilityService) {
        if (this.service === service) {
            this.service = null
            _connected.value = false
            loopJob?.cancel()
            loopJob = null
            logLine(context.getString(R.string.log_service_wait))
        }
    }

    fun setAutoSpin(on: Boolean) {
        if (_active.value == on) return
        _active.value = on
        if (on) {
            logLine(context.getString(R.string.log_engine_started))
            startLoop()
        } else {
            loopJob?.cancel()
            loopJob = null
            logLine(context.getString(R.string.log_engine_stopped))
        }
    }

    /**
     * Kept apart from the [panelOpen] property: a function called setPanelOpen
     * would clash with the JVM setter generated for the property.
     */
    fun setPanelState(open: Boolean) {
        if (panelOpen == open) return
        panelOpen = open
        if (open) logLine(context.getString(R.string.log_paused_panel))
    }

    fun shutdown() {
        loopJob?.cancel()
        loopJob = null
        _active.value = false
        panelOpen = false
    }

    fun resetSession() {
        previousBalance = null
        sessionStart = null
        lossStreak = 0
        slowSpins = 0
    }

    fun resetStats() {
        _stats.value = BotStats()
        resetSession()
        scope.launch { store.resetStats() }
        logLine(context.getString(R.string.log_stats_reset))
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun logLine(line: String) {
        if (settings.logVerbosity == 0) return
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.value = (listOf("$stamp  $line") + _log.value).take(100)
    }

    private fun logRes(resId: Int, vararg args: Any) {
        logLine(context.getString(resId, *args))
    }

    private fun logVerbose(resId: Int, vararg args: Any) {
        if (settings.logVerbosity < 2) return
        logRes(resId, *args)
    }

    private fun persist() {
        val snapshot = _stats.value
        scope.launch { store.saveStats(snapshot) }
    }

    /** A few pixels of drift - real fingers never hit the same pixel twice. */
    private fun jitterRadius(): Int =
        (context.resources.displayMetrics.density * 3f).toInt().coerceIn(2, 12)

    private fun gestureDuration(): Long = Random.nextLong(45L, 91L)

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive && _active.value) {
                if (panelOpen) {
                    delay(250)
                    continue
                }
                val svc = service
                if (svc == null) {
                    delay(500)
                    continue
                }
                if (!svc.isGameForeground()) {
                    logVerbose(R.string.log_game_not_focused)
                    delay(900)
                    continue
                }
                val s = settings
                try {
                    if (s.jackpotHunter) handleJackpot(svc, s)
                    trackBalance(svc, s)
                    if (slowSpins > 0) slowSpins--
                    if (s.stealth && Random.nextFloat() < 0.08f) {
                        val wait = Random.nextLong(1000, 2001)
                        logRes(R.string.log_stealth_skip, wait)
                        delay(wait)
                    }
                    val tapped = spin(svc, s)
                    if (tapped) {
                        val st = _stats.value
                        _stats.value = st.copy(spins = st.spins + 1)
                        persist()
                    }
                    delay(nextInterval(s, tapped))
                } catch (t: Throwable) {
                    logRes(R.string.log_error, t.message ?: t.javaClass.simpleName)
                    delay(700)
                }
            }
        }
    }

    private suspend fun spin(svc: BotAccessibilityService, s: Settings): Boolean {
        val gap = System.currentTimeMillis() - lastTapAt
        if (gap in 1 until MIN_TAP_GAP) delay(MIN_TAP_GAP - gap)
        if (s.stealth) delay(Random.nextLong(40, 161))

        val node = svc.findSpinNode()
        if (node != null) {
            val point = svc.randomPointIn(node, jitterRadius()) ?: svc.centerOf(node)
            if (point != null && svc.tapAt(point.x, point.y, gestureDuration())) {
                lastTapAt = System.currentTimeMillis()
                logRes(R.string.log_spin_gesture)
                return true
            }
            if (svc.clickNode(node)) {
                lastTapAt = System.currentTimeMillis()
                logRes(R.string.log_spin_node)
                return true
            }
        }
        val latest = settings
        if (latest.spinCalibrated) {
            val raw = svc.pointAt(latest.spinX, latest.spinY)
            val point = svc.jitterPoint(raw, jitterRadius())
            if (svc.tapAt(point.x, point.y, gestureDuration())) {
                lastTapAt = System.currentTimeMillis()
                logRes(R.string.log_spin_calibrated)
                return true
            }
        }
        logVerbose(R.string.log_spin_not_found)
        return false
    }

    private suspend fun trackBalance(svc: BotAccessibilityService, s: Settings) {
        val balance = svc.readBalance() ?: return
        if (sessionStart == null) sessionStart = balance
        val previous = previousBalance
        if (previous != null) {
            val delta = balance - previous
            if (delta > 0.009) {
                lossStreak = 0
                val st = _stats.value
                _stats.value = st.copy(
                    wins = st.wins + 1,
                    biggestWin = max(st.biggestWin, delta)
                )
                persist()
                logRes(R.string.log_win, money(delta))
            } else if (delta < -0.009) {
                lossStreak++
                logRes(R.string.log_loss, money(abs(delta)))
                if (lossStreak >= 3 && s.alwaysWin) {
                    lossStreak = 0
                    slowSpins = 5
                    store.update { it.copy(betLevel = Settings.MIN_BET) }
                    logRes(R.string.log_loss_streak)
                }
            }
            if (abs(delta) > 0.0001 && abs(delta) < 0.02) {
                val st = _stats.value
                _stats.value = st.copy(nearMisses = st.nearMisses + 1)
                persist()
                logRes(R.string.log_near_miss)
            }
        }
        previousBalance = balance
        logVerbose(R.string.log_balance, money(balance))
        if (s.alwaysWin) adjustBet(balance, s)
    }

    private suspend fun adjustBet(balance: Double, s: Settings) {
        val start = sessionStart ?: return
        if (balance < start) {
            val target = (s.betLevel - 1).coerceAtLeast(Settings.MIN_BET)
            if (target != s.betLevel) {
                store.update { it.copy(betLevel = target) }
                logRes(R.string.log_bet_down, target)
            }
        } else if (balance > start) {
            val target = (s.betLevel + 1).coerceAtMost(Settings.MAX_BET)
            if (target != s.betLevel) {
                store.update { it.copy(betLevel = target) }
                logRes(R.string.log_bet_up, target)
            }
        }
    }

    private suspend fun handleJackpot(svc: BotAccessibilityService, s: Settings) {
        val now = System.currentTimeMillis()
        if (now - lastJackpotAt < JACKPOT_GAP) return
        val label = svc.scanJackpot() ?: return
        lastJackpotAt = now
        logRes(R.string.log_jackpot, label)
        val st = _stats.value
        _stats.value = st.copy(jackpots = st.jackpots + 1)
        persist()
        if (s.vibrate) vibrate()
        if (s.jackpotToast) _jackpot.tryEmit(context.getString(R.string.toast_jackpot))
        delay(320)

        val button = svc.findCollectButton()
        if (button != null) {
            val text = button.text?.toString() ?: button.contentDescription?.toString() ?: ""
            val point = svc.randomPointIn(button, jitterRadius()) ?: svc.centerOf(button)
            if (point != null && svc.tapAt(point.x, point.y, gestureDuration())) {
                logRes(R.string.log_jackpot_tapped, text)
                return
            }
            if (svc.clickNode(button)) {
                logRes(R.string.log_jackpot_tapped, text)
                return
            }
        }
        val latest = settings
        if (latest.collectCalibrated) {
            val raw = svc.pointAt(latest.collectX, latest.collectY)
            val point = svc.jitterPoint(raw, jitterRadius())
            if (svc.tapAt(point.x, point.y, gestureDuration())) {
                logRes(R.string.log_jackpot_calibrated)
                return
            }
        }
        logVerbose(R.string.log_jackpot_no_button)
    }

    private fun nextInterval(s: Settings, tapped: Boolean): Long {
        var base = s.intervalMs.toLong()
        if (slowSpins > 0) base = (base * 1.8).toLong()
        if (s.stealth) base = (base * Random.nextDouble(0.65, 1.35)).toLong()
        if (!tapped) base = max(base, 700L)
        return base.coerceIn(Settings.MIN_INTERVAL.toLong(), 6000L)
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
}

@Suppress("unused")
private fun BotEngine.unusedPointHelper(point: Point): Point = point
