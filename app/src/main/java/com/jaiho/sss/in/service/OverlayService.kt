package com.jaiho.sss.`in`.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jaiho.sss.`in`.MainActivity
import com.jaiho.sss.`in`.R
import com.jaiho.sss.`in`.SanjoyApp
import com.jaiho.sss.`in`.data.BotStats
import com.jaiho.sss.`in`.data.Settings
import com.jaiho.sss.`in`.data.SettingsStore
import com.jaiho.sss.`in`.ui.overlay.CalibrationOverlay
import com.jaiho.sss.`in`.ui.overlay.OverlayPanel
import com.jaiho.sss.`in`.ui.theme.SanjoyTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val CHANNEL_ID = "sanjoy_bot_channel"
        private const val NOTIFICATION_ID = 4211
        const val ACTION_STOP = "com.jaiho.sss.in.action.STOP"
        const val ACTION_OPEN_PANEL = "com.jaiho.sss.in.action.PANEL"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        @Volatile private var instance: OverlayService? = null
        @Volatile private var calibrationRequested = false

        fun requestCalibration() {
            calibrationRequested = true
            instance?.let { svc -> svc.handler.post { svc.consumeCalibrationRequest() } }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableListOf<Job>()
    private val viewModelStore = ViewModelStore()
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override fun getViewModelStore(): ViewModelStore = viewModelStore

    private lateinit var windowManager: WindowManager
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var calibrationView: View? = null
    private var toastView: View? = null
    private var hiddenJob: Job? = null
    private var edgeAnimator: ValueAnimator? = null

    private var bubbleX = 0
    private var bubbleY = 0
    private var keepScreenOn = true

    private val container get() = (application as SanjoyApp).container
    private val store: SettingsStore get() = container.store
    private val engine get() = container.engine

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createChannel()
        startForegroundInternal()
        engine.resetSession()
        engine.setPanelOpen(false)
        showBubble()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        jobs += scope.launch {
            engine.jackpotEvents.collect { message -> showGoldToast(message) }
        }
        jobs += scope.launch {
            store.settings.collect { settings -> applyKeepScreenOn(settings.keepScreenOn) }
        }
        _running.value = true
        if (calibrationRequested) handler.postDelayed({ consumeCalibrationRequest() }, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_PANEL -> togglePanel()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val p = bubbleParams ?: return
        val screen = screenSize()
        bubbleX = bubbleX.coerceIn(0, (screen.x - dp(64)).coerceAtLeast(0))
        bubbleY = bubbleY.coerceIn(0, (screen.y - dp(64)).coerceAtLeast(0))
        p.x = bubbleX
        p.y = bubbleY
        bubbleView?.let { runCatching { windowManager.updateViewLayout(it, p) } }
    }

    override fun onDestroy() {
        _running.value = false
        if (instance === this) instance = null
        jobs.forEach { it.cancel() }
        hiddenJob?.cancel()
        edgeAnimator?.cancel()
        removeView(bubbleView)
        removeView(panelView)
        removeView(calibrationView)
        removeView(toastView)
        bubbleView = null
        panelView = null
        calibrationView = null
        toastView = null
        engine.setPanelOpen(false)
        engine.setAutoSpin(false)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundInternal() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.notification_channel_desc)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bot)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun baseFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun applyKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        val extra = if (on) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0
        bubbleParams?.let { params ->
            params.flags = baseFlags() or extra
            bubbleView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        }
        panelParams?.let { params ->
            params.flags = baseFlags() or extra
            panelView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        }
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val size = dp(64)
        val screen = screenSize()
        if (bubbleX == 0 && bubbleY == 0) {
            bubbleX = (screen.x * 0.86).toInt()
            bubbleY = (screen.y * 0.32).toInt()
        }
        val view = TextView(this).apply {
            text = getString(R.string.bubble_text)
            contentDescription = getString(R.string.bubble_desc)
            setTextColor(Color.parseColor("#0A0A0C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFD24A"))
                setStroke(dp(3), Color.parseColor("#1DB954"))
            }
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags() or (if (keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bubbleX
        params.y = bubbleY
        attachBubbleTouch(view, params)
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) return
        bubbleView = view
        bubbleParams = params
    }

    private fun attachBubbleTouch(view: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = dp(6)
        val longPress = Runnable { hideBubbleBriefly() }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    handler.postDelayed(longPress, 600)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        params.x = startX + dx
                        params.y = startY + dy
                        bubbleX = params.x
                        bubbleY = params.y
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) snapToEdge(view, params) else togglePanel()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val screen = screenSize()
        val size = dp(64)
        val target = if (params.x + size / 2 < screen.x / 2) 0 else (screen.x - size).coerceAtLeast(0)
        val top = params.y.coerceIn(0, (screen.y - size).coerceAtLeast(0))
        edgeAnimator?.cancel()
        val animator = ValueAnimator.ofInt(params.x, target).apply {
            duration = 180
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                params.y = top
                bubbleX = params.x
                bubbleY = params.y
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
        edgeAnimator = animator
        animator.start()
    }

    private fun hideBubbleBriefly() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        if (panelView != null) closePanel()
        removeView(view)
        bubbleView = null
        bubbleParams = null
        showGoldToast(getString(R.string.bubble_hidden))
        hiddenJob?.cancel()
        hiddenJob = scope.launch {
            delay(3000)
            bubbleX = params.x
            bubbleY = params.y
            showBubble()
        }
    }

    private fun togglePanel() {
        if (panelView != null) closePanel() else openPanel()
    }

    private fun openPanel() {
        if (panelView != null) return
        engine.setPanelOpen(true)
        val view = ComposeView(this).also { it.attachOwners() }
        view.setContent {
            SanjoyTheme {
                OverlayPanel(
                    store = store,
                    engine = engine,
                    scope = scope,
                    onCollapse = { closePanel() },
                    onCalibrate = { openCalibration() },
                    onStopBot = { engine.setAutoSpin(false) },
                    onToast = { message -> showGoldToast(message) }
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags() or (if (keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.y = dp(28)
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            engine.setPanelOpen(false)
            return
        }
        panelView = view
        panelParams = params
    }

    private fun closePanel() {
        val view = panelView ?: return
        removeView(view)
        panelView = null
        panelParams = null
        if (calibrationView == null) engine.setPanelOpen(false)
    }

    private fun consumeCalibrationRequest() {
        calibrationRequested = false
        if (bubbleView == null) showBubble()
        openCalibration()
    }

    private fun openCalibration() {
        if (calibrationView != null) return
        closePanel()
        engine.setPanelOpen(true)
        val view = ComposeView(this).also { it.attachOwners() }
        view.setContent {
            SanjoyTheme {
                val settings by store.settings.collectAsState(Settings())
                CalibrationOverlay(
                    initialSpinX = settings.spinX,
                    initialSpinY = settings.spinY,
                    initialCollectX = settings.collectX,
                    initialCollectY = settings.collectY,
                    onSave = { spinX, spinY, collectX, collectY ->
                        saveCalibration(spinX, spinY, collectX, collectY)
                    },
                    onCancel = { closeCalibration() }
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            engine.setPanelOpen(false)
            return
        }
        calibrationView = view
    }

    private fun saveCalibration(spinX: Float, spinY: Float, collectX: Float, collectY: Float) {
        scope.launch {
            store.update {
                it.copy(
                    spinX = spinX.coerceIn(0f, 1f),
                    spinY = spinY.coerceIn(0f, 1f),
                    collectX = collectX.coerceIn(0f, 1f),
                    collectY = collectY.coerceIn(0f, 1f),
                    spinCalibrated = true,
                    collectCalibrated = true
                )
            }
            engine.logLine(getString(R.string.log_calibration_applied))
            closeCalibration()
            showGoldToast(getString(R.string.calibrate_saved))
        }
    }

    private fun closeCalibration() {
        val view = calibrationView ?: return
        removeView(view)
        calibrationView = null
        if (panelView == null) engine.setPanelOpen(false)
    }

    private fun showGoldToast(message: String) {
        handler.post {
            toastView?.let { old -> removeView(old) }
            val view = TextView(this).apply {
                text = message
                setTextColor(Color.parseColor("#0A0A0C"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(20), dp(12), dp(20), dp(12))
                elevation = dp(8).toFloat()
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.parseColor("#FFD24A"))
                    setStroke(dp(2), Color.parseColor("#C9A227"))
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = dp(96)
            if (runCatching { windowManager.addView(view, params) }.isSuccess) {
                toastView = view
                handler.postDelayed({
                    if (toastView === view) {
                        removeView(view)
                        toastView = null
                    }
                }, 1700)
            }
        }
    }

    private fun removeView(view: View?) {
        if (view == null) return
        runCatching { windowManager.removeView(view) }
    }

    private fun <T : View> T.attachOwners(): T {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
        return this
    }

    private fun screenSize(): Point {
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            point.x = bounds.width()
            point.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getRealSize(point)
        }
        if (point.x <= 0 || point.y <= 0) {
            point.x = resources.displayMetrics.widthPixels
            point.y = resources.displayMetrics.heightPixels
        }
        return point
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    @Suppress("unused")
    private fun statsSnapshot(): BotStats = engine.stats.value
}
