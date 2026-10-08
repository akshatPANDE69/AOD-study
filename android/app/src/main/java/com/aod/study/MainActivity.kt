package com.aod.study

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var handler: Handler
    private var durationSec: Int = 30
    private var elapsedSec: Int = 0
    private val records = mutableListOf<Record>()
    private lateinit var aodView: AODView
    private lateinit var rootLayout: LinearLayout
    private lateinit var reportText: StringBuilder

    data class Record(val second: Int, val latencyNs: Long, val batteryNow: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial UI
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val durationInput = EditText(this).apply {
            hint = "Duration (seconds)"
            setText("30")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }

        val startButton = Button(this).apply {
            text = "Start AOD Test"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        rootLayout.addView(durationInput)
        rootLayout.addView(startButton)
        setContentView(rootLayout)

        handler = Handler(Looper.getMainLooper())

        startButton.setOnClickListener {
            val input = durationInput.text.toString()
            durationSec = input.toIntOrNull() ?: 30
            startTest()
        }
    }

    private fun startTest() {
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive full‑screen black background
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        rootLayout.setBackgroundColor(Color.BLACK)
        rootLayout.removeAllViews()

        // AOD view
        aodView = AODView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            renderListener = { latencyNs, batteryNow ->
                // Record data for the current second (elapsedSec is already incremented in the tick)
                records.add(Record(elapsedSec, latencyNs, batteryNow))
            }
        }
        rootLayout.addView(aodView)

        // Start ticking
        elapsedSec = 0
        records.clear()
        handler.post(tickRunnable)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (elapsedSec >= durationSec) {
                finishTest()
                return
            }
            elapsedSec++
            aodView.invalidate()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun finishTest() {
        handler.removeCallbacks(tickRunnable)
        // Build markdown report
        reportText = StringBuilder()
        reportText.append("| Second | Latency (ms) | Battery (µA) |\n")
        reportText.append("|--------|--------------|--------------|\n")
        for (rec in records) {
            val latencyMs = rec.latencyNs / 1_000_000.0
            reportText.append("| ${rec.second} | %.3f | ${rec.batteryNow} |\n".format(latencyMs))
        }

        // Show report UI
        rootLayout.removeAllViews()
        rootLayout.setBackgroundColor(Color.WHITE)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val reportView = TextView(this).apply {
            text = reportText.toString()
            setTextColor(Color.BLACK)
            setPadding(16, 16, 16, 16)
        }

        scrollView.addView(reportView)

        val copyButton = Button(this).apply {
            text = "Copy Results to Clipboard"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 24
                bottomMargin = 24
            }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AOD Test Report", reportText.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        rootLayout.addView(scrollView)
        rootLayout.addView(copyButton)
    }

    // Custom view that draws the AOD clock and battery info
    private class AODView(context: Context) : View(context) {
        var renderListener: ((latencyNs: Long, batteryNow: Int) -> Unit)? = null
        private val timePaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 120f
        }
        private val batteryPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 60f
        }
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        override fun onDraw(canvas: Canvas) {
            val startNs = System.nanoTime()

            val now = Date()
            val timeText = timeFormat.format(now)

            val width = width.toFloat()
            val height = height.toFloat()

            // Draw time centered
            canvas.drawText(timeText, width / 2, height / 2, timePaint)

            // Battery percentage
            val batteryPct = getBatteryPercentage()
            val batteryText = "Battery: $batteryPct%"
            canvas.drawText(batteryText, width / 2, height / 2 + 80f, batteryPaint)

            // Read current now (µA)
            val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

            val latencyNs = System.nanoTime() - startNs
            renderListener?.invoke(latencyNs, currentNow)
        }

        private fun getBatteryPercentage(): Int {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            return if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                0
            }
        }
    }
}
