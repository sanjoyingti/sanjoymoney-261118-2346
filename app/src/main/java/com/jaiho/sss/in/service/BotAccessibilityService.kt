package com.jaiho.sss.`in`.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jaiho.sss.`in`.SanjoyApp
import com.jaiho.sss.`in`.R
import com.jaiho.sss.`in`.bot.BalanceParser
import com.jaiho.sss.`in`.bot.NodeFinder
import com.jaiho.sss.`in`.util.SystemUtils
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BotAccessibilityService? = null
            private set
    }

    private var gameForeground = false
    private var lastAutoStart = 0L

    private val container get() = (application as SanjoyApp).container

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        container.engine.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in SystemUtils.GAME_PACKAGES) {
            gameForeground = true
            maybeAutoStart()
        } else if (pkg != packageName) {
            // Our own overlay windows keep the "game in front" state untouched.
            gameForeground = false
        }
    }

    /**
     * Fires when the game surfaces: brings up the bubble and switches the engine on.
     * Runs either when the user opened the game from the home screen or when
     * "auto start with game" is enabled.
     */
    private fun maybeAutoStart() {
        val now = System.currentTimeMillis()
        if (now - lastAutoStart < 1500) return
        lastAutoStart = now
        container.scope.launch {
            val s = container.store.settings.first()
            if (!s.autoStartWithGame && !container.autoStartArmed) return@launch
            if (!SystemUtils.isOverlayGranted(this@BotAccessibilityService)) return@launch
            container.autoStartArmed = false
            if (container.startOverlay()) {
                container.engine.setAutoSpin(true)
                container.engine.logLine(getString(R.string.log_auto_started))
            }
        }
    }

    fun isGameForeground(): Boolean {
        if (gameForeground) return true
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        return pkg != null && pkg in SystemUtils.GAME_PACKAGES
    }

    fun findSpinNode(): AccessibilityNodeInfo? = NodeFinder.findSpin(rootSafe())

    fun findCollectButton(): AccessibilityNodeInfo? = NodeFinder.findCollect(rootSafe())

    fun scanJackpot(): String? = NodeFinder.scanJackpot(rootSafe())

    fun readBalance(): Double? = BalanceParser.read(rootSafe())

    private fun rootSafe(): AccessibilityNodeInfo? = runCatching { rootInActiveWindow }.getOrNull()

    fun centerOf(node: AccessibilityNodeInfo): Point? {
        val rect = Rect()
        runCatching { node.getBoundsInScreen(rect) }
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return Point(rect.centerX(), rect.centerY())
    }

    /**
     * Human-like tap point: a random spot inside the button, inset from its edges,
     * then nudged by a couple of pixels. Real fingers never hit the exact centre.
     */
    fun randomPointIn(node: AccessibilityNodeInfo, jitterPx: Int): Point? {
        val rect = Rect()
        runCatching { node.getBoundsInScreen(rect) }
        if (rect.width() < 4 || rect.height() < 4) return null
        val marginX = (rect.width() * 0.2f).toInt().coerceAtLeast(1)
        val marginY = (rect.height() * 0.2f).toInt().coerceAtLeast(1)
        val left = (rect.left + marginX).coerceAtMost(rect.right - 1)
        val right = (rect.right - marginX).coerceAtLeast(left + 1)
        val top = (rect.top + marginY).coerceAtMost(rect.bottom - 1)
        val bottom = (rect.bottom - marginY).coerceAtLeast(top + 1)
        val x = Random.nextInt(left, right)
        val y = Random.nextInt(top, bottom)
        return jitterPoint(Point(x, y), jitterPx)
    }

    fun jitterPoint(point: Point, radiusPx: Int): Point {
        if (radiusPx <= 0) return point
        val size = screenSize()
        val maxX = (size.x - 2).coerceAtLeast(2)
        val maxY = (size.y - 2).coerceAtLeast(2)
        val x = (point.x + Random.nextInt(-radiusPx, radiusPx + 1)).coerceIn(1, maxX)
        val y = (point.y + Random.nextInt(-radiusPx, radiusPx + 1)).coerceIn(1, maxY)
        return Point(x, y)
    }

    fun pointAt(fractionX: Float, fractionY: Float): Point {
        val size = screenSize()
        val x = (size.x * fractionX).toInt().coerceIn(0, (size.x - 1).coerceAtLeast(0))
        val y = (size.y * fractionY).toInt().coerceIn(0, (size.y - 1).coerceAtLeast(0))
        return Point(x, y)
    }

    fun screenSize(): Point {
        val result = Point()
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return result
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            result.x = bounds.width()
            result.y = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            result.x = metrics.widthPixels
            result.y = metrics.heightPixels
        }
        return result
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = if (node.isClickable) node else null
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (target == null && current != null && depth < 6) {
            current = runCatching { current?.parent }.getOrNull()
            if (current?.isClickable == true) target = current
            depth++
        }
        val finalTarget = target ?: return false
        return runCatching {
            finalTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
    }

    fun tapAt(x: Int, y: Int, durationMs: Long = 60L): Boolean {
        val duration = durationMs.coerceIn(25L, 140L)
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()
        return runCatching { dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        runCatching { container.engine.detach(this) }
        if (instance === this) instance = null
    }
}
