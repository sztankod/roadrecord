package hu.roadrecord.app.service

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import hu.roadrecord.app.MainActivity
import hu.roadrecord.app.R
import hu.roadrecord.app.RoadRecordApplication
import kotlin.math.roundToInt

class NextStopOverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "hu.roadrecord.overlay.SHOW"
        const val ACTION_UPDATE = "hu.roadrecord.overlay.UPDATE"
        const val ACTION_HIDE = "hu.roadrecord.overlay.HIDE"
        const val ACTION_SUSPEND = "hu.roadrecord.overlay.SUSPEND"
        const val ACTION_RESUME = "hu.roadrecord.overlay.RESUME"
        const val EXTRA_NAME = "name"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_CURRENT_NAME = "current_name"
        const val EXTRA_CURRENT_ADDRESS = "current_address"
        const val EXTRA_PIN_NEXT = "pin_next"

        @Volatile
        var visible = false

        @Volatile
        private var instance: NextStopOverlayService? = null

        fun onAppForegroundChanged(inForeground: Boolean) {
            instance?.let { service ->
                service.mainExecutor.execute {
                    if (inForeground) service.hide()
                    else if (service.prefs.getBoolean("requested", false) && Settings.canDrawOverlays(service)) {
                        service.restoreContent()
                        service.ensureShown()
                    }
                }
            }
        }
    }

    private val prefs by lazy { getSharedPreferences("next_stop_overlay", MODE_PRIVATE) }
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentNameView: TextView? = null
    private var currentAddressView: TextView? = null
    private var nameView: TextView? = null
    private var addressView: TextView? = null
    private var currentName = "Úton"
    private var currentAddress = ""
    private var nextName = "Nincs további megálló"
    private var nextAddress = ""
    private var pinnedNextName = ""
    private var pinnedNextAddress = ""

    override fun onCreate() {
        super.onCreate()
        instance = this
        restoreContent()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                prefs.edit().putBoolean("requested", false).apply()
                hide()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SUSPEND -> {
                hide()
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                restoreContent()
                if (prefs.getBoolean("requested", false) && Settings.canDrawOverlays(this)) ensureShown()
                return START_NOT_STICKY
            }

            ACTION_SHOW -> {
                prefs.edit().putBoolean("requested", true).apply()
                readContent(intent)
                if (!RoadRecordApplication.isInForeground && Settings.canDrawOverlays(this)) ensureShown()
                else if (!Settings.canDrawOverlays(this)) stopSelf()
            }

            ACTION_UPDATE -> {
                readContent(intent)
                if (root != null) renderContent()
            }
        }
        return START_NOT_STICKY
    }

    private fun readContent(intent: Intent) {
        val incomingCurrent = intent.getStringExtra(EXTRA_CURRENT_NAME)
        if (pinnedNextName.isNotBlank() && incomingCurrent == pinnedNextName) {
            pinnedNextName = ""
            pinnedNextAddress = ""
        }
        incomingCurrent?.let { currentName = it.ifBlank { "Úton" } }
        intent.getStringExtra(EXTRA_CURRENT_ADDRESS)?.let { currentAddress = it }
        if (intent.getBooleanExtra(EXTRA_PIN_NEXT, false)) {
            pinnedNextName = intent.getStringExtra(EXTRA_NAME).orEmpty()
            pinnedNextAddress = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        }
        if (pinnedNextName.isNotBlank()) {
            nextName = pinnedNextName
            nextAddress = pinnedNextAddress
        } else {
            intent.getStringExtra(EXTRA_NAME)?.let { nextName = it.ifBlank { "Nincs további megálló" } }
            intent.getStringExtra(EXTRA_ADDRESS)?.let { nextAddress = it }
        }
        prefs.edit()
            .putString("current_name", currentName)
            .putString("current_address", currentAddress)
            .putString("next_name", nextName)
            .putString("next_address", nextAddress)
            .putString("pinned_next_name", pinnedNextName)
            .putString("pinned_next_address", pinnedNextAddress)
            .apply()
    }

    private fun restoreContent() {
        currentName = prefs.getString("current_name", currentName) ?: currentName
        currentAddress = prefs.getString("current_address", currentAddress) ?: currentAddress
        nextName = prefs.getString("next_name", nextName) ?: nextName
        nextAddress = prefs.getString("next_address", nextAddress) ?: nextAddress
        pinnedNextName = prefs.getString("pinned_next_name", pinnedNextName) ?: pinnedNextName
        pinnedNextAddress = prefs.getString("pinned_next_address", pinnedNextAddress) ?: pinnedNextAddress
    }

    private fun renderContent() {
        currentNameView?.text = currentName
        currentAddressView?.text = currentAddress
        nameView?.text = nextName
        addressView?.text = nextAddress
    }

    private fun ensureShown() {
        if (root == null) show() else {
            renderContent()
            correctPosition()
        }
    }

    private fun show() {
        val density = resources.displayMetrics.density
        fun text(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            setTextColor(color)
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }

        currentNameView = text(14f, Color.WHITE, true)
        currentAddressView = text(10f, 0xFFCFDCF2.toInt())
        nameView = text(14f, Color.WHITE, true)
        addressView = text(10f, 0xFFCFDCF2.toInt())
        renderContent()

        fun stopColumn(title: String, name: TextView, address: TextView) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(9f, 0xFF8FB7F5.toInt(), true).apply { this.text = title }, LinearLayout.LayoutParams(-1, -2))
            addView(name, LinearLayout.LayoutParams(-1, -2))
            addView(address, LinearLayout.LayoutParams(-1, -2))
        }

        val current = stopColumn("JELENLEGI", currentNameView!!, currentAddressView!!)
        val next = stopColumn("KÖVETKEZŐ", nameView!!, addressView!!)
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                shape = GradientDrawable.OVAL
            }
            contentDescription = "Vissza a RoadRecordba"
            setPadding((5 * density).toInt(), (5 * density).toInt(), (5 * density).toInt(), (5 * density).toInt())
            setOnClickListener { openRoadRecord() }
        }
        val close = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                shape = GradientDrawable.OVAL
            }
            contentDescription = "Lebegő sáv bezárása"
            setOnClickListener {
                prefs.edit().putBoolean("requested", false).apply()
                hide()
                stopSelf()
            }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0xDE0D2B60.toInt())
                cornerRadius = 12 * density
                setStroke((1 * density).roundToInt(), 0x44FFFFFF)
            }
            addView(back, LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt()).apply { marginEnd = (5 * density).toInt() })
            addView(current, LinearLayout.LayoutParams(0, (58 * density).toInt(), 1f))
            addView(View(this@NextStopOverlayService).apply { setBackgroundColor(0x55FFFFFF) }, LinearLayout.LayoutParams((1 * density).toInt(), (42 * density).toInt()).apply { setMargins((8 * density).toInt(), 0, (8 * density).toInt(), 0) })
            addView(next, LinearLayout.LayoutParams(0, (58 * density).toInt(), 1f))
            addView(close, LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt()).apply { marginStart = (5 * density).toInt() })
        }

        val safe = safeBounds()
        val saved = prefs.getInt("y", Int.MIN_VALUE)
        val defaultY = safe.first + (48 * density).roundToInt()
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            horizontalMargin = .02f
            y = if (saved == Int.MIN_VALUE) defaultY else saved.coerceAtLeast(safe.first)
            if (Build.VERSION.SDK_INT >= 31) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = (6 * density).roundToInt()
            }
        }
        val drag = dragListener()
        current.setOnTouchListener(drag)
        next.setOnTouchListener(drag)
        windowManager.addView(container, params)
        root = container
        visible = true
        container.post { correctPosition() }
    }

    private fun dragListener(): View.OnTouchListener {
        var startRawY = 0f
        var startY = 0
        var moved = false
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startY = params?.y ?: 0
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val delta = (event.rawY - startRawY).roundToInt()
                    if (kotlin.math.abs(delta) > 6) moved = true
                    params?.let {
                        it.y = clampY(startY + delta)
                        root?.let { view -> windowManager.updateViewLayout(view, it) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    params?.y?.let { prefs.edit().putInt("y", it).apply() }
                    if (!moved && event.actionMasked == MotionEvent.ACTION_UP) openRoadRecord()
                    true
                }

                else -> false
            }
        }
    }

    private fun openRoadRecord() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
    }

    private fun safeBounds(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= 30) {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout(),
        )
        insets.top to (metrics.bounds.height() - insets.bottom)
    } else {
        val point = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getSize(point)
        val statusId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val navId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val top = if (statusId > 0) resources.getDimensionPixelSize(statusId) else 0
        val bottom = if (navId > 0) resources.getDimensionPixelSize(navId) else 0
        top to (point.y - bottom)
    }

    private fun clampY(value: Int): Int {
        val safe = safeBounds()
        val height = root?.height?.takeIf { it > 0 } ?: (resources.displayMetrics.density * 70).roundToInt()
        return value.coerceIn(safe.first, (safe.second - height).coerceAtLeast(safe.first))
    }

    private fun correctPosition() {
        val p = params ?: return
        p.y = clampY(p.y)
        root?.let { windowManager.updateViewLayout(it, p) }
        prefs.edit().putInt("y", p.y).apply()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        root?.post { correctPosition() }
    }

    private fun hide() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        params = null
        visible = false
    }

    override fun onDestroy() {
        hide()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
