package com.aod.study

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.os.*
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.content.getSystemService
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MainActivity : Activity() {

    // -------------------------------------------------------------------------
    // UI containers
    // -------------------------------------------------------------------------
    private lateinit var root: LinearLayout
    private lateinit var modeSwitcher: RadioGroup
    private lateinit var modeContainer: FrameLayout

    // -------------------------------------------------------------------------
    // Shared services
    // -------------------------------------------------------------------------
    private val batteryManager by lazy { getSystemService(Context.BATTERY_SERVICE) as BatteryManager }
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val mainHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // RadioGroup for mode selection
        modeSwitcher = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setBackgroundColor(Color.DKGRAY)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
        }

        val modes = listOf("Benchmark", "Explorer", "Live AOD")
        modes.forEachIndexed { index, title ->
            val rb = RadioButton(this).apply {
                text = title
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            modeSwitcher.addView(rb)
            if (index == 0) rb.isChecked = true
        }

        // Container for mode specific UI
        modeContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(modeSwitcher)
        root.addView(modeContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        setContentView(root)

        // Switch listener
        modeSwitcher.setOnCheckedChangeListener { _, checkedId ->
            when (findViewById<RadioButton>(checkedId).text) {
                "Benchmark" -> showBenchmarkMode()
                "Explorer" -> showExplorerMode()
                "Live AOD" -> showLiveAodMode()
            }
        }

        // Show default mode
        showBenchmarkMode()
    }

    // -------------------------------------------------------------------------
    // Helper utilities
    // -------------------------------------------------------------------------
    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun sp(v: Int): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v.toFloat(), resources.displayMetrics
    )

    private fun copyToClipboard(label: String, text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------------------
    // Mode 1 – Benchmark
    // -------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    private fun showBenchmarkMode() {
        modeContainer.removeAllViews()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val startBtn = Button(this).apply {
            text = "Start OLED Black Benchmark (8 s)"
        }

        val resultView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, 0)
        }

        val copyBtn = Button(this).apply {
            text = "Copy Markdown Report"
            isEnabled = false
        }

        startBtn.setOnClickListener {
            startBtn.isEnabled = false
            resultView.text = "Running benchmark..."
            copyBtn.isEnabled = false

            // Capture initial current
            val startCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

            // Show black screen overlay
            val overlay = View(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            modeContainer.addView(overlay)

            // 8‑second timer
            mainHandler.postDelayed({
                // Remove overlay
                modeContainer.removeView(overlay)

                // Capture final current
                val endCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

                // Compute delta (µA) and convert to mA
                val deltaUa = abs(endCurrent - startCurrent)
                val deltaMa = deltaUa / 1000.0

                // Build markdown report
                val md = """
                    # OLED Black Benchmark
                    **Duration:** 8 seconds  
                    **Current before:** ${startCurrent / 1000} mA  
                    **Current after:** ${endCurrent / 1000} mA  
                    **Δ Current:** ${"%.2f".format(deltaMa)} mA  

                    *Generated by AOD‑Study*
                """.trimIndent()

                resultView.text = md
                copyBtn.isEnabled = true
                startBtn.isEnabled = true
            }, 8_000L)
        }

        copyBtn.setOnClickListener {
            copyToClipboard("Benchmark Report", resultView.text.toString())
        }

        container.addView(startBtn)
        container.addView(resultView)
        container.addView(copyBtn)

        modeContainer.addView(container)
    }

    // -------------------------------------------------------------------------
    // Mode 2 – Hz & Power Explorer
    // -------------------------------------------------------------------------
    private fun showExplorerMode() {
        modeContainer.removeAllViews()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // SeekBar for Hz
        val hzLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "Refresh Rate: 60 Hz"
        }

        val seekBar = SeekBar(this).apply {
            max = 119 // 1..120
            progress = 59 // default 60Hz
        }

        // Preset buttons
        val presetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val presets = listOf(1, 10, 30, 50, 60, 120)
        presets.forEach { hz ->
            val btn = Button(this).apply {
                text = "$hz Hz"
                textSize = 12f
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            btn.setOnClickListener {
                seekBar.progress = hz - 1
            }
            presetLayout.addView(btn)
        }

        // Battery graph view
        val graphView = BatteryGraphView(this)

        // Update label & graph when Hz changes
        fun updateHz(value: Int) {
            hzLabel.text = "Refresh Rate: $value Hz"
            graphView.setRefreshRate(value)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                updateHz(progress + 1)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Initial state
        updateHz(seekBar.progress + 1)

        container.addView(hzLabel)
        container.addView(seekBar)
        container.addView(presetLayout)
        container.addView(graphView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(200)
        ).apply { topMargin = dp(12) })

        modeContainer.addView(container)
    }

    // -------------------------------------------------------------------------
    // Mode 3 – Live AOD Display
    // -------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    private fun showLiveAodMode() {
        modeContainer.removeAllViews()
        val aodRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Clock
        val clockTv = TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Date & battery line
        val infoTv = TextView(this).apply {
            setTextColor(Color.parseColor("#9E9E9E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Notification icons row
        val notifRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val icons = listOf("💬", "✉", "📞", "🔔")
        icons.forEach { emoji ->
            val tv = TextView(this).apply {
                text = emoji
                textSize = 24f
                setPadding(dp(8), 0, dp(8), 0)
            }
            notifRow.addView(tv)
        }

        // Show notifications toggle
        val notifToggle = Switch(this).apply {
            text = "Show Notifications"
            setTextColor(Color.WHITE)
            isChecked = true
        }
        notifToggle.setOnCheckedChangeListener { _, isChecked ->
            notifRow.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Cadence toggle
        val cadenceToggle = Switch(this).apply {
            text = "Minute‑Aligned (1/60 Hz)"
            setTextColor(Color.WHITE)
            isChecked = false
        }

        // Burn‑in shift toggle
        val burnInToggle = Switch(this).apply {
            text = "Burn‑In Shift (±3 px)"
            setTextColor(Color.WHITE)
            isChecked = false
        }

        // Telemetry badge
        val telemetryTv = TextView(this).apply {
            setTextColor(Color.CYAN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.parseColor("#66000000"))
        }

        // Fullscreen button
        val fullscreenBtn = Button(this).apply {
            text = "Enter Fullscreen Immersive AOD"
        }
        fullscreenBtn.setOnClickListener { enterImmersive() }

        // Layout assembly
        val vStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        vStack.addView(clockTv)
        vStack.addView(infoTv)
        vStack.addView(notifRow)
        vStack.addView(notifToggle)
        vStack.addView(cadenceToggle)
        vStack.addView(burnInToggle)
        vStack.addView(telemetryTv)
        vStack.addView(fullscreenBtn)

        aodRoot.addView(vStack)
        modeContainer.addView(aodRoot)

        // -----------------------------------------------------------------
        // Timing & update logic
        // -----------------------------------------------------------------
        var updateRunnable: Runnable? = null
        var burnInRunnable: Runnable? = null

        fun updateClock() {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = cal.get(java.util.Calendar.MINUTE)
            val second = cal.get(java.util.Calendar.SECOND)

            // Clock display (HH:mm)
            clockTv.text = String.format("%02d:%02d", hour, minute)

            // Date & battery line
            val dateStr = java.text.SimpleDateFormat("EEE, MMM d yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(now))
            val battPct = (batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).toString()
            infoTv.text = "$dateStr   Battery: $battPct%"

            // Telemetry (dummy APR % and OLED power)
            val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // µA
            val powerMw = abs(currentNow) * 0.001 // approx mW (very rough)
            val apr = (powerMw / 1000.0) * 100 // dummy APR %
            telemetryTv.text = "APR: ${"%.1f".format(apr)}%   OLED Power: ${"%.1f".format(powerMw)} mW"
        }

        fun scheduleUpdates() {
            updateRunnable?.let { mainHandler.removeCallbacks(it) }
            val intervalMs = if (cadenceToggle.isChecked) {
                // minute‑aligned: compute delay to next minute start
                val now = System.currentTimeMillis()
                val secs = (now / 1000) % 60
                ((60 - secs) * 1000)
            } else {
                1000L // 1 Hz
            }

            updateRunnable = object : Runnable {
                override fun run() {
                    updateClock()
                    mainHandler.postDelayed(this, intervalMs)
                }
            }
            mainHandler.post(updateRunnable!!)
        }

        fun scheduleBurnInShift() {
            burnInRunnable?.let { mainHandler.removeCallbacks(it) }
            if (!burnInToggle.isChecked) {
                aodRoot.translationX = 0f
                aodRoot.translationY = 0f
                return
            }
            burnInRunnable = object : Runnable {
                override fun run() {
                    val dx = Random.nextInt(-3, 4).toFloat()
                    val dy = Random.nextInt(-3, 4).toFloat()
                    aodRoot.translationX = dx
                    aodRoot.translationY = dy
                    mainHandler.postDelayed(this, 60_000L) // every minute
                }
            }
            mainHandler.post(burnInRunnable!!)
        }

        // Listeners
        cadenceToggle.setOnCheckedChangeListener { _, _ -> scheduleUpdates() }
        burnInToggle.setOnCheckedChangeListener { _, _ -> scheduleBurnInShift() }

        // Initial start
        scheduleUpdates()
        scheduleBurnInShift()
    }

    // -------------------------------------------------------------------------
    // Immersive mode helper
    // -------------------------------------------------------------------------
    private fun enterImmersive() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        // Keep screen on while in AOD mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // -------------------------------------------------------------------------

    inner class BatteryGraphView(context: Context) : View(context) {

        private var refreshRateHz: Int = 60
        fun setRefreshRate(hz: Int) {
            refreshRateHz = hz
            invalidate()
        }

    private val maxSamples = 100
    private val samples = FloatArray(maxSamples)
    private var sampleCount = 0

    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    /** Add a new battery level sample (0‑100). */
    fun addSample(value: Float) {
        if (sampleCount < maxSamples) {
            samples[sampleCount] = value
            sampleCount++
        } else {
            // shift left to make room for the newest sample
            System.arraycopy(samples, 1, samples, 0, maxSamples - 1)
            samples[maxSamples - 1] = value
        }
        invalidate()   // request redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // ---- draw dark‑gray grid ----
        // vertical lines (one per sample)
        val vStep = w / maxSamples
        for (i in 0..maxSamples) {
            val x = i * vStep
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }

        // horizontal lines (5 evenly spaced)
        val hLines = 5
        val hStep = h / hLines
        for (i in 0..hLines) {
            val y = i * hStep
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        // ---- draw green battery line ----
        if (sampleCount > 1) {
            val path = Path()
            val xStep = w / (maxSamples - 1)
            for (i in 0 until sampleCount) {
                val x = i * xStep
                // map 0‑100 value to canvas Y (bottom = 0, top = h)
                val y = h - (samples[i] / 100f) * h
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, linePaint)
        }
    }
}
}   // closing brace for MainActivity
