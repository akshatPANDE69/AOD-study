package com.aod.study

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.widget.*
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
    private var refreshStudyStatusOnResume: (() -> Unit)? = null
    private var studyStatusTicker: Runnable? = null

    // -------------------------------------------------------------------------
    // Shared services
    // -------------------------------------------------------------------------
    private val batteryManager by lazy { getSystemService(BatteryManager::class.java)!! }
    private val clipboardManager by lazy { getSystemService(ClipboardManager::class.java)!! }
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

        // Keep every mode reachable on narrow screens while letting wide screens
        // show the complete row without clipping.
        modeSwitcher = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setBackgroundColor(Color.DKGRAY)
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val modes = listOf("Benchmark", "Display Modes", "Live AOD", "Study Log")
        modes.forEachIndexed { index, title ->
            val rb = RadioButton(this).apply {
                text = title
                id = View.generateViewId()
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minHeight = dp(48)
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

        val modeScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setFillViewport(true)
            setBackgroundColor(Color.DKGRAY)
            addView(modeSwitcher)
        }
        root.addView(modeScroller, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(modeContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        setContentView(root)

        // Switch listener
        modeSwitcher.setOnCheckedChangeListener { _, checkedId ->
            stopStudyStatusTicker()
            refreshStudyStatusOnResume = null
            val selectedMode = findViewById<RadioButton>(checkedId).text.toString()
            if (selectedMode != "Display Modes") resetPreferredDisplayMode()
            when (selectedMode) {
                "Benchmark" -> showBenchmarkMode()
                "Display Modes" -> showExplorerMode()
                "Live AOD" -> showLiveAodMode()
                "Study Log" -> showStudyLoggerMode()
            }
        }

        // Show default mode
        showBenchmarkMode()
    }

    override fun onResume() {
        super.onResume()
        refreshStudyStatusOnResume?.invoke()
        startStudyStatusTicker()
    }

    override fun onPause() {
        stopStudyStatusTicker()
        super.onPause()
    }

    private fun startStudyStatusTicker() {
        stopStudyStatusTicker()
        val refresh = refreshStudyStatusOnResume ?: return
        val prefs = getSharedPreferences(StudyTrialStore.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(StudyTrialStore.ACTIVE, false)) return
        val ticker = object : Runnable {
            override fun run() {
                refresh()
                if (prefs.getBoolean(StudyTrialStore.ACTIVE, false)) {
                    mainHandler.postDelayed(this, 1_000L)
                } else {
                    studyStatusTicker = null
                }
            }
        }
        studyStatusTicker = ticker
        mainHandler.post(ticker)
    }

    private fun stopStudyStatusTicker() {
        studyStatusTicker?.let { mainHandler.removeCallbacks(it) }
        studyStatusTicker = null
    }

    private fun formatTimer(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
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

    private fun resetPreferredDisplayMode() {
        val params = window.attributes
        if (params.preferredDisplayModeId != 0) {
            params.preferredDisplayModeId = 0
            window.attributes = params
        }
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
            text = "Read battery current during black overlay (8 s)"
        }

        val resultView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, 0)
        }

        val copyBtn = Button(this).apply {
            text = "Copy Markdown Report"
            isEnabled = false
        }

        val measurementNote = TextView(this).apply {
            text = "Exploratory only: this reads whole-device battery current twice while the app is open. It does not isolate OLED power or measure AOD. Use Study Log for timed screen-off trials."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
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
                    # Exploratory Whole-Device Battery Current Snapshot
                    **Duration:** 8 seconds  
                    **Battery current before:** ${startCurrent / 1000} mA
                    **Battery current after:** ${endCurrent / 1000} mA
                    **Absolute difference between snapshots:** ${"%.2f".format(deltaMa)} mA

                    *Whole-device fuel-gauge readings; not isolated OLED power or an AOD measurement.*
                """.trimIndent()

                resultView.text = md
                copyBtn.isEnabled = true
                startBtn.isEnabled = true
            }, 8_000L)
        }

        copyBtn.setOnClickListener {
            copyToClipboard("Benchmark Report", resultView.text.toString())
        }

        container.addView(measurementNote)
        container.addView(startBtn)
        container.addView(resultView)
        container.addView(copyBtn)

        modeContainer.addView(container)
    }

    // -------------------------------------------------------------------------
    // Mode 2 – actual app-window display-mode request (not native AOD control)
    // -------------------------------------------------------------------------
    private fun showExplorerMode() {
        modeContainer.removeAllViews()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val display = getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val supportedModes = display?.supportedModes?.toList().orEmpty()
        val note = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 13f
            text = "Choose a refresh mode supported by Android for this app window. The OS may ignore the request. This cannot change native lock-screen AOD or measure optical flicker."
        }
        val modeLabels = supportedModes.map {
            "${it.refreshRate} Hz • ${it.physicalWidth}×${it.physicalHeight} • mode ${it.modeId}"
        }.ifEmpty { listOf("No display modes reported") }
        val modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        }
        val activeMode = TextView(this).apply { setTextColor(Color.WHITE); setPadding(0, dp(8), 0, dp(8)) }
        fun refreshActiveMode() {
            val current = getSystemService(android.hardware.display.DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
            activeMode.text = "OS-reported active app display mode: ${current?.refreshRate ?: "unknown"} Hz (mode ${current?.modeId ?: "?"})"
        }
        refreshActiveMode()
        val applyButton = Button(this).apply {
            text = "Request selected display mode"
            setOnClickListener {
                val selected = supportedModes.getOrNull(modeSpinner.selectedItemPosition)
                if (selected == null) {
                    Toast.makeText(this@MainActivity, "No selectable mode is exposed by Android.", Toast.LENGTH_SHORT).show()
                } else {
                    val params = window.attributes
                    params.preferredDisplayModeId = selected.modeId
                    window.attributes = params
                    activeMode.text = "Requested ${selected.refreshRate} Hz for this app window. Android may select another mode; native AOD is unaffected."
                    mainHandler.postDelayed({ refreshActiveMode() }, 1_000L)
                }
            }
        }
        val autoButton = Button(this).apply {
            text = "Return to system-selected mode"
            setOnClickListener {
                val params = window.attributes
                params.preferredDisplayModeId = 0
                window.attributes = params
                refreshActiveMode()
            }
        }
        container.addView(note)
        container.addView(modeSpinner)
        container.addView(applyButton)
        container.addView(autoButton)
        container.addView(activeMode)

        modeContainer.addView(container)
    }

    // -------------------------------------------------------------------------
    // Study logger: one system snapshot after lock and one at the timed endpoint
    // -------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    private fun showStudyLoggerMode() {
        modeContainer.removeAllViews()
        val wide = resources.configuration.screenWidthDp >= 600
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = if (wide) 24 else 16
            setPadding(dp(horizontalPadding), dp(12), dp(horizontalPadding), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(container) }

        container.addView(TextView(this).apply {
            text = "Study Log"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(0, 0, 0, dp(4))
        })
        container.addView(TextView(this).apply {
            text = "Run Test starts the session timer immediately. Lock the phone; the first reading begins after a 30-second settling period. The timer uses Android’s monotonic clock and resumes when you return."
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, 0, 0, dp(10))
        })

        val timerView = TextView(this).apply {
            text = "00:00:00"
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(4))
        }
        container.addView(TextView(this).apply {
            text = "Elapsed since Run Test · HH:MM:SS"
            setTextColor(Color.LTGRAY)
            textSize = 13f
        })
        container.addView(timerView)

        val statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minHeight = dp(56)
            setBackgroundColor(Color.rgb(32, 48, 56))
        }
        container.addView(statusView)

        fun field(label: String, initial: String = ""): EditText {
            container.addView(TextView(this).apply {
                text = label
                setTextColor(Color.LTGRAY)
                textSize = 14f
                setPadding(0, dp(6), 0, dp(2))
            })
            return EditText(this).apply {
                setText(initial)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minHeight = dp(48)
                container.addView(this)
            }
        }

        val deviceField = field("Device label", Build.MODEL)
        val pairField = field("Matched pair/block ID (same for AOD-off and AOD-on runs)")
        container.addView(TextView(this).apply {
            text = "Trial condition"
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, dp(6), 0, dp(2))
        })
        val conditionValues = listOf(
            "Screen off, native AOD off",
            "Screen off, native AOD on",
            "LCD persistent-display comparator"
        )
        val conditionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, conditionValues)
            container.addView(this)
        }
        val durationField = field("Trial duration in minutes", "60").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val setupField = field("AOD style or displayed pattern", "Clock only")
        val brightnessField = field("AOD brightness setting (%)", "")
        brightnessField.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val aodRefreshField = field("OEM AOD refresh setting (Hz, if shown in system settings)", "")
        aodRefreshField.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val opticalRefreshField = field("Optically measured AOD light frequency (Hz; external sensor)", "")
        opticalRefreshField.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val opticalInstrumentField = field("Optical sensor / sampling rate", "")
        val coverageField = field("Estimated illuminated pixel coverage (%)", "")
        coverageField.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val rgbField = field("Pattern RGB", "")
        val notesField = field("Notes", "")

        val instructions = TextView(this).apply {
            text = "Pair one screen-off run with native AOD off and one with it on, using the same pair ID. The first reading starts 30 seconds after you tap Start; the endpoint alarm may be delayed by Android. Keep the phone unplugged and untouched. This app cannot change or verify native AOD refresh; physical light frequency needs an external optical sensor."
            setTextColor(Color.GRAY)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        }
        container.addView(instructions)

        val inventoryView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setPadding(0, dp(4), 0, dp(4))
        }
        val inventoryButton = Button(this).apply {
            text = "Read device, CPU and GPU inventory"
            setOnClickListener {
                val inventory = DeviceTelemetry.inventory(this@MainActivity).toString(2)
                inventoryView.text = inventory
                copyToClipboard("Device telemetry inventory", inventory)
            }
        }
        container.addView(inventoryButton)
        container.addView(inventoryView)

        val prefs = getSharedPreferences(StudyTrialStore.PREFS, MODE_PRIVATE)
        fun refreshStatus() {
            val active = prefs.getBoolean(StudyTrialStore.ACTIVE, false)
            val hasStart = !prefs.getString(StudyTrialStore.START_SNAPSHOT, null).isNullOrBlank()
            val expectedElapsed = if (hasStart) {
                prefs.getLong(StudyTrialStore.TARGET_ELAPSED_MS, Long.MAX_VALUE)
            } else {
                prefs.getLong(StudyTrialStore.PENDING_START_TARGET_MS, Long.MAX_VALUE)
            }
            statusView.text = when {
                !active -> {
                    val savedDuration = prefs.getLong(StudyTrialStore.LAST_SESSION_DURATION_MS, 0L)
                    timerView.text = if (savedDuration > 0L) formatTimer(savedDuration) else "00:00:00"
                    prefs.getString(StudyTrialStore.LAST_RESULT, "No study trials saved yet.")
                }
                expectedElapsed != Long.MAX_VALUE && SystemClock.elapsedRealtime() > expectedElapsed + 120_000L ->
                    run {
                        val sessionStart = prefs.getLong(StudyTrialStore.SESSION_START_ELAPSED_MS, expectedElapsed)
                        timerView.text = formatTimer(SystemClock.elapsedRealtime() - sessionStart)
                        "A required alarm snapshot was not saved. The run may have been interrupted; cancel it and do not use it."
                    }
                !hasStart -> {
                    val now = SystemClock.elapsedRealtime()
                    val sessionStart = prefs.getLong(StudyTrialStore.SESSION_START_ELAPSED_MS, now)
                    timerView.text = formatTimer(now - sessionStart)
                    val untilStart = (expectedElapsed - now).coerceAtLeast(0L)
                    "SETTLING · First reading in ${formatTimer(untilStart)}. Lock the phone now; measurement starts after the 30-second settling period."
                }
                else -> {
                    val now = SystemClock.elapsedRealtime()
                    val sessionStart = prefs.getLong(StudyTrialStore.SESSION_START_ELAPSED_MS, now)
                    timerView.text = formatTimer(now - sessionStart)
                    val metadata = org.json.JSONObject(prefs.getString("metadata", "") ?: "")
                    val remaining = (expectedElapsed - now).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        "MEASURING · ${formatTimer(remaining)} remaining for trial ${metadata.optString("trialId")}. Keep the phone locked."
                    } else {
                        "MEASUREMENT TIME REACHED · Waiting for Android to deliver the endpoint alarm. Keep the phone locked; actual delay is recorded."
                    }
                }
            }
        }
        refreshStudyStatusOnResume = ::refreshStatus
        refreshStatus()

        val permissionButton = Button(this).apply {
            text = "Enable exact trial timer"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        startActivity(Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        ))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Open Settings and allow Alarms & reminders for AOD Study.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            container.addView(permissionButton)
        }

        val startButton = Button(this).apply {
            text = "Run Test"
            setOnClickListener {
                if (prefs.getBoolean(StudyTrialStore.ACTIVE, false)) {
                    Toast.makeText(this@MainActivity, "A trial is already scheduled or running.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val minutes = durationField.text.toString().toLongOrNull()
                if (minutes == null || minutes < 1L || minutes > 1440L) {
                    Toast.makeText(this@MainActivity, "Enter a duration from 1 to 1440 minutes.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val pairId = pairField.text.toString().trim()
                if (pairId.isBlank()) {
                    Toast.makeText(this@MainActivity, "Enter the matched pair/block ID for this run.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val alarmManager = getSystemService(AlarmManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    statusView.text = "Timed alarms are not enabled. Tap ‘Enable exact trial timer’, allow Alarms & reminders, return here, then tap Start Test again."
                    scroll.post { scroll.smoothScrollTo(0, 0) }
                    Toast.makeText(this@MainActivity, "Enable Alarms & reminders, then tap Start again.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val trialId = "AOD_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}"
                val metadata = org.json.JSONObject().apply {
                    put("trialId", trialId)
                    put("pairId", pairId)
                    put("deviceLabel", deviceField.text.toString().trim())
                    put("condition", conditionSpinner.selectedItem.toString())
                    put("durationMinutes", minutes)
                    put("aodSetup", setupField.text.toString().trim())
                    put("aodBrightnessPct", brightnessField.text.toString().trim())
                    put("aodRefreshSettingHz", aodRefreshField.text.toString().trim())
                    put("opticalAodFrequencyHz", opticalRefreshField.text.toString().trim())
                    put("opticalInstrument", opticalInstrumentField.text.toString().trim())
                    put("pixelCoveragePct", coverageField.text.toString().trim())
                    put("patternRgb", rgbField.text.toString().trim())
                    put("notes", notesField.text.toString().trim())
                    put("deviceInventoryJson", DeviceTelemetry.inventory(this@MainActivity).toString())
                }
                val beginIntent = Intent(this@MainActivity, StudyTrialReceiver::class.java)
                    .setAction(StudyTrialReceiver.ACTION_BEGIN_TRIAL)
                val beginPendingIntent = PendingIntent.getBroadcast(
                    this@MainActivity,
                    StudyTrialStore.REQUEST_CODE,
                    beginIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val beginAtElapsed = SystemClock.elapsedRealtime() + 30_000L
                prefs.edit()
                    .putBoolean(StudyTrialStore.ACTIVE, true)
                    .putString("metadata", metadata.toString())
                    .putString(StudyTrialStore.LAST_RESULT, "")
                    .putLong(StudyTrialStore.SESSION_START_ELAPSED_MS, SystemClock.elapsedRealtime())
                    .putLong(StudyTrialStore.LAST_SESSION_DURATION_MS, 0L)
                    .putLong(StudyTrialStore.PENDING_START_TARGET_MS, beginAtElapsed)
                    .remove(StudyTrialStore.START_SNAPSHOT)
                    .remove(StudyTrialStore.TARGET_ELAPSED_MS)
                    .apply()
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        beginAtElapsed,
                        beginPendingIntent
                    )
                    refreshStatus()
                    startStudyStatusTicker()
                    scroll.post { scroll.smoothScrollTo(0, 0) }
                } catch (_: SecurityException) {
                    prefs.edit().putBoolean(StudyTrialStore.ACTIVE, false)
                        .remove(StudyTrialStore.PENDING_START_TARGET_MS)
                        .remove(StudyTrialStore.SESSION_START_ELAPSED_MS)
                        .remove("metadata")
                        .putString(StudyTrialStore.LAST_RESULT, "Exact alarm access was unavailable. Trial was not started.")
                        .apply()
                    refreshStatus()
                    Toast.makeText(this@MainActivity, "Exact alarm access is required to start a trial.", Toast.LENGTH_LONG).show()
                }
            }
        }
        container.addView(startButton)

        val cancelButton = Button(this).apply {
            text = "Cancel / invalidate trial"
            setOnClickListener {
                val alarmManager = getSystemService(AlarmManager::class.java)
                listOf(
                    StudyTrialReceiver.ACTION_BEGIN_TRIAL to StudyTrialStore.REQUEST_CODE,
                    StudyTrialReceiver.ACTION_FINISH_TRIAL to StudyTrialStore.REQUEST_CODE + 1
                ).forEach { (action, requestCode) ->
                    val alarmIntent = Intent(this@MainActivity, StudyTrialReceiver::class.java).setAction(action)
                    val pending = PendingIntent.getBroadcast(
                        this@MainActivity,
                        requestCode,
                        alarmIntent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pending != null) alarmManager.cancel(pending)
                }
                prefs.edit().putBoolean(StudyTrialStore.ACTIVE, false)
                    .remove(StudyTrialStore.START_SNAPSHOT)
                    .remove(StudyTrialStore.TARGET_ELAPSED_MS)
                    .remove(StudyTrialStore.PENDING_START_TARGET_MS)
                    .remove(StudyTrialStore.SESSION_START_ELAPSED_MS)
                    .putLong(StudyTrialStore.LAST_SESSION_DURATION_MS, 0L)
                    .remove("metadata")
                    .putString(StudyTrialStore.LAST_RESULT, "Trial cancelled and not saved.")
                    .apply()
                refreshStatus()
            }
        }
        container.addView(cancelButton)

        val exportButton = Button(this).apply {
            text = "Export saved CSV"
            setOnClickListener {
                val file = StudyTrialStore.csvFile(this@MainActivity)
                if (!file.exists()) {
                    Toast.makeText(this@MainActivity, "No completed trials to export.", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/csv"
                        putExtra(Intent.EXTRA_TITLE, file.name)
                    }
                    startActivityForResult(intent, REQUEST_EXPORT_CSV)
                }
            }
        }
        container.addView(exportButton)

        val copyAllButton = Button(this).apply {
            text = "Copy all saved trial telemetry"
            setOnClickListener {
                val file = StudyTrialStore.csvFile(this@MainActivity)
                if (!file.exists() || file.length() == 0L) {
                    Toast.makeText(this@MainActivity, "No completed trials to copy.", Toast.LENGTH_SHORT).show()
                } else {
                    copyToClipboard("AOD study trial telemetry CSV", file.readText(Charsets.UTF_8))
                }
            }
        }
        container.addView(copyAllButton)

        container.addView(TextView(this).apply {
            text = "Records device/SoC and best-effort CPU/GPU inventory, CPU count/core lists, battery counters, voltage, temperature, thermal status, display metadata, OEM AOD setting notes, and (on supported Android 15+ devices) available subsystem/power-rail energy counters such as CPU/GPU/display. Vendor availability and definitions vary. Battery counters remain whole-device estimates; component counters may be modeled and cannot isolate AOD by themselves."
            setTextColor(Color.GRAY)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        })
        modeContainer.addView(scroll)
    }

    @Deprecated("Deprecated in Android API, retained for minSdk 26 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT_CSV || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val input = StudyTrialStore.csvFile(this).inputStream()
            contentResolver.openOutputStream(uri)?.use { output -> input.use { it.copyTo(output) } }
            Toast.makeText(this, "CSV exported.", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, "CSV export failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQUEST_EXPORT_CSV = 2047
    }

    // -------------------------------------------------------------------------
    // Mode 3 – Live AOD Display
    // -------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    private fun showLiveAodMode() {
        modeContainer.removeAllViews()
        val modeNote = TextView(this).apply {
            text = "Foreground rendering demo. The screen remains on; this is not the phone's native lock-screen AOD. The update toggle changes this app's clock redraw interval only."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
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
        aodRoot.addView(modeNote, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))
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

            // Whole-device fuel-gauge snapshot; this is not panel-specific telemetry.
            val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // µA
            telemetryTv.text = "Whole-device battery current: ${"%.2f".format(currentNow / 1000.0)} mA"
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
