package com.jaiho.sss.in.service

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
import com.jaiho.sss.in.SanjoyApp
import com.jaiho.sss.in.bot.BalanceParser
import com.jaiho.sss.in.bot.NodeFinder
import com.jaiho.sss.in.util.SystemUtils
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
        if (pkg == SystemUtils.GAME_PACKAGE) {
            gameForeground = true
            maybeAutoStart()
        } else if (pkg != packageName) {
            gameForeground = false
        }
    }

    private fun maybeAutoStart() {
        val now = System.currentTimeMillis()
        if (now - lastAutoStart < 1500) return
        lastAutoStart = now
        container.scope.launch {
            val s = container.store.settings.first()
            if (!s.autoStartWithGame) return@launch
            if (container.startOverlay()) container.engine.setAutoSpin(true)
        }
    }

    fun isGameForeground(): Boolean {
        if (gameForeground) return true
        return runCatching {
            rootInActiveWindow?.packageName?.toString() == SystemUtils.GAME_PACKAGE
        }.getOrDefault(false)
    }

    fun findSpinNode(): AccessibilityNodeInfo? = NodeFinder.findSpin(rootSafe())

    fun findCollectButton(): AccessibilityNodeInfo? = NodeFinder.findCollect(rootSafe())

    fun scanJackpot(): String? = NodeFinder.scanJackpot(rootSafe())

    fun readBalance(): Double? = BalanceParser.read(rootSafe())

    private fun rootSafe(): AccessibilityNodeInfo? = runCatching { rootInActiveWindow }.getOrNull()

    fun centerOf(node: AccessibilityNodeInfo): Point? {
        val rect = Rect()
        if (!runCatching { node.getBoundsInScreen(rect) }.getOrDefault(false)) return null
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return Point(rect.centerX(), rect.centerY())
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

    fun tapAt(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
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
        container.engine.detach(this)
        if (instance === this) instance = null
    }
}
