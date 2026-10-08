package com.aod.study

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var modeSwitcher: RadioGroup
    private lateinit var container: FrameLayout

    // Benchmark mode views
    private lateinit var benchmarkStartBtn: Button
    private lateinit var benchmarkReportTv: TextView
    private lateinit var benchmarkCopyBtn: Button
    private lateinit var blackOverlay: View

    // Interactive mode views
    private lateinit var clockTv: TextView
    private lateinit var freqSeekBar: SeekBar
    private lateinit var freqValueTv: TextView
    private lateinit var presetContainer: LinearLayout
    private lateinit var batteryGraph: BatteryGraphView

    private val handler = Handler(Looper.getMainLooper())
    private var benchmarkStartTime: Long = 0L
    private var benchmarkDurationMs: Long = 8000L // 8 seconds

    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            clockTv.text = now
            handler.postDelayed(this, 1000L)
        }
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val currentNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    it.getIntExtra(BatteryManager.EXTRA_CURRENT_NOW, 0)
                } else {
                    0
                }
                batteryGraph.addSample(currentNow / 1000f) // convert to mA
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Mode switcher
        modeSwitcher = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            val mode1 = RadioButton(this@MainActivity).apply {
                text = "Benchmark"
                id = View.generateViewId()
                isChecked = true
            }
            val mode2 = RadioButton(this@MainActivity).apply {
                text = "Interactive"
                id = View.generateViewId()
            }
            addView(mode1)
            addView(mode2)
            setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == mode1.id) {
                    showBenchmarkMode()
                } else {
                    showInteractiveMode()
                }
            }
        }

        // Container for mode content
        container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        rootLayout.addView(modeSwitcher)
        rootLayout.addView(container)

        setContentView(rootLayout)

        initBenchmarkMode()
        initInteractiveMode()

        // Start with benchmark mode
        showBenchmarkMode()
    }

    // -------------------- Benchmark Mode --------------------
    private fun initBenchmarkMode() {
        val benchmarkLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        benchmarkStartBtn = Button(this).apply {
            text = "Start OLED Black Test"
            setOnClickListener { startBenchmark() }
        }

        benchmarkReportTv = TextView(this).apply {
            textSize = 16f
            setPadding(16, 16, 16, 16)
            visibility = View.GONE
        }

        benchmarkCopyBtn = Button(this).apply {
            text = "Copy Report"
            visibility = View.GONE
            setOnClickListener { copyReportToClipboard() }
        }

        blackOverlay = View(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }

        benchmarkLayout.addView(benchmarkStartBtn)
        benchmarkLayout.addView(benchmarkReportTv)
        benchmarkLayout.addView(benchmarkCopyBtn)

        container.addView(benchmarkLayout)
        container.addView(blackOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun startBenchmark() {
        benchmarkStartBtn.isEnabled = false
        benchmarkReportTv.visibility = View.GONE
        benchmarkCopyBtn.visibility = View.GONE
        blackOverlay.visibility = View.VISIBLE
        benchmarkStartTime = System.currentTimeMillis()
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentAtStart = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        handler.postDelayed({
            blackOverlay.visibility = View.GONE
            val elapsed = System.currentTimeMillis() - benchmarkStartTime
            val currentAtEnd = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val avgCurrentMa = ((Math.abs(currentAtStart) + Math.abs(currentAtEnd)) / 2) / 1000
            val device = "${Build.MANUFACTURER} ${Build.MODEL}"
            val android = Build.VERSION.RELEASE
            val report = buildString {
                appendLine("### AOD Benchmark Report")
                appendLine()
                appendLine("| Metric | Value |")
                appendLine("|--------|-------|")
                appendLine("| **Device** | $device |")
                appendLine("| **Android** | $android |")
                appendLine("| **Duration** | ${elapsed} ms |")
                appendLine("| **Avg Battery Current** | ${avgCurrentMa} mA |")
                appendLine()
                appendLine("*Measured on-device; zero synthetic data.*")
            }
            benchmarkReportTv.text = report
            benchmarkReportTv.visibility = View.VISIBLE
            benchmarkCopyBtn.visibility = View.VISIBLE
            benchmarkStartBtn.isEnabled = true
        }, benchmarkDurationMs)
    }

    private fun copyReportToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Benchmark Report", benchmarkReportTv.text)
        clipboard.setPrimaryClip(clip)
    }

    // -------------------- Interactive Mode --------------------
    private fun initInteractiveMode() {
        val interactiveLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        // Live Clock
        clockTv = TextView(this).apply {
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        interactiveLayout.addView(clockTv)

        // Frequency SeekBar
        freqValueTv = TextView(this).apply {
            text = "Frequency: 1 Hz"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        interactiveLayout.addView(freqValueTv)

        freqSeekBar = SeekBar(this).apply {
            max = 20
            progress = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val hz = if (progress < 1) 1 else progress
                    freqValueTv.text = "Frequency: $hz Hz"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        interactiveLayout.addView(freqSeekBar)

        // Preset Buttons
        presetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        val presets = arrayOf(1, 10, 30, 50, 60, 120)
        for (hz in presets) {
            val btn = Button(this).apply {
                text = "${hz}Hz"
                textSize = 11f
                setPadding(8, 4, 8, 4)
                setOnClickListener {
                    freqSeekBar.max = maxOf(freqSeekBar.max, hz)
                    freqSeekBar.progress = hz
                    freqValueTv.text = "Frequency: ${hz} Hz"
                }
            }
            presetContainer.addView(btn)
        }
        interactiveLayout.addView(presetContainer)

        // Battery Current Graph
        batteryGraph = BatteryGraphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            )
        }
        interactiveLayout.addView(batteryGraph)

        container.addView(interactiveLayout)
    }

    private fun showBenchmarkMode() {
        // Show benchmark children, hide interactive
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.visibility = if (child is LinearLayout && child.getChildAt(0) is Button && (child.getChildAt(0) as Button).text == "Start OLED Black Test") {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        // Ensure black overlay is on top if needed
        blackOverlay.bringToFront()
    }

    private fun showInteractiveMode() {
        // Show interactive children, hide benchmark
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.visibility = if (child is LinearLayout && child.getChildAt(0) is TextView && (child.getChildAt(0) as TextView).textSize == 24f) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        // Start clock and battery monitoring
        handler.post(clockRunnable)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    override fun onResume() {
        super.onResume()
        if (modeSwitcher.checkedRadioButtonId != -1) {
            val checked = findViewById<RadioButton>(modeSwitcher.checkedRadioButtonId)
            if (checked.text == "Interactive") {
                handler.post(clockRunnable)
                registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        }
    }

    // -------------------- Battery Graph View --------------------
    private class BatteryGraphView(context: Context) : View(context) {

        private val paint = Paint().apply {
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
        private val maxSamples = 100
        private val samples = mutableListOf<Float>()
        private var maxValue = 500f // mA, adjust as needed

        fun addSample(value: Float) {
            if (samples.size >= maxSamples) {
                samples.removeAt(0)
            }
            samples.add(value.coerceIn(0f, maxValue))
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // Draw grid
            val rows = 5
            for (i in 0..rows) {
                val y = i * h / rows
                canvas.drawLine(0f, y, w, y, gridPaint)
            }

            // Draw line
            if (samples.isNotEmpty()) {
                val step = w / (maxSamples - 1)
                var prevX = 0f
                var prevY = h - (samples[0] / maxValue) * h
                for (i in 1 until samples.size) {
                    val x = i * step
                    val y = h - (samples[i] / maxValue) * h
                    canvas.drawLine(prevX, prevY, x, y, paint)
                    prevX = x
                    prevY = y
                }
            }
        }
    }
}
