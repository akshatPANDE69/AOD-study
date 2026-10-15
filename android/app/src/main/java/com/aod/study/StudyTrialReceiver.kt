package com.aod.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.OutcomeReceiver
import android.os.PowerMonitorReadings
import android.os.SystemClock
import android.os.health.SystemHealthManager
import android.view.Display
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Captures two low-frequency system snapshots without keeping the screen awake. */
data class SystemSnapshot(
    val wallTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val batteryLevelPct: Int,
    val batteryPlugType: Int,
    val batteryTempC: Double,
    val batteryVoltageMv: Int,
    val currentNowUa: Int,
    val currentAverageUa: Int,
    val chargeCounterUah: Int,
    val energyCounterNwh: Long,
    val displayState: Int,
    val refreshRateHz: Float,
    val modeId: Int,
    val supportedModes: String,
    val thermalStatus: Int,
    val powerMonitorsJson: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("wallTimeMs", wallTimeMs)
        put("elapsedRealtimeMs", elapsedRealtimeMs)
        put("batteryLevelPct", batteryLevelPct)
        put("batteryPlugType", batteryPlugType)
        put("batteryTempC", if (batteryTempC.isFinite()) batteryTempC else JSONObject.NULL)
        put("batteryVoltageMv", batteryVoltageMv)
        put("currentNowUa", currentNowUa)
        put("currentAverageUa", currentAverageUa)
        put("chargeCounterUah", chargeCounterUah)
        put("energyCounterNwh", energyCounterNwh)
        put("displayState", displayState)
        put("refreshRateHz", if (refreshRateHz.isFinite()) refreshRateHz.toDouble() else JSONObject.NULL)
        put("modeId", modeId)
        put("supportedModes", supportedModes)
        put("thermalStatus", thermalStatus)
        put("powerMonitorsJson", powerMonitorsJson)
    }

    companion object {
        fun fromJson(json: JSONObject) = SystemSnapshot(
            wallTimeMs = json.getLong("wallTimeMs"),
            elapsedRealtimeMs = json.getLong("elapsedRealtimeMs"),
            batteryLevelPct = json.getInt("batteryLevelPct"),
            batteryPlugType = json.getInt("batteryPlugType"),
            batteryTempC = if (json.isNull("batteryTempC")) Double.NaN else json.getDouble("batteryTempC"),
            batteryVoltageMv = json.getInt("batteryVoltageMv"),
            currentNowUa = json.getInt("currentNowUa"),
            currentAverageUa = json.getInt("currentAverageUa"),
            chargeCounterUah = json.getInt("chargeCounterUah"),
            energyCounterNwh = json.getLong("energyCounterNwh"),
            displayState = json.getInt("displayState"),
            refreshRateHz = if (json.isNull("refreshRateHz")) Float.NaN else json.getDouble("refreshRateHz").toFloat(),
            modeId = json.getInt("modeId"),
            supportedModes = json.getString("supportedModes"),
            thermalStatus = json.optInt("thermalStatus", -1),
            powerMonitorsJson = json.optString("powerMonitorsJson", "{}"),
        )

        fun capture(context: Context): SystemSnapshot {
            val batteryManager = context.getSystemService(BatteryManager::class.java)
            val batteryStatus = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val displayManager = context.getSystemService(DisplayManager::class.java)
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            val mode = display?.mode
            val supported = display?.supportedModes
                ?.map { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" }
                ?.distinct()
                ?.joinToString("|")
                .orEmpty()

            val energyCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                    ?: Long.MIN_VALUE
            } else {
                Long.MIN_VALUE
            }

            return SystemSnapshot(
                wallTimeMs = System.currentTimeMillis(),
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                batteryLevelPct = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
                batteryPlugType = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1,
                batteryTempC = (batteryStatus?.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    Int.MIN_VALUE
                ) ?: Int.MIN_VALUE).let { if (it == Int.MIN_VALUE) Double.NaN else it / 10.0 },
                batteryVoltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1,
                currentNowUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    ?: Int.MIN_VALUE,
                currentAverageUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                    ?: Int.MIN_VALUE,
                chargeCounterUah = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    ?: Int.MIN_VALUE,
                energyCounterNwh = energyCounter,
                displayState = display?.state ?: -1,
                refreshRateHz = display?.refreshRate ?: Float.NaN,
                modeId = mode?.modeId ?: -1,
                supportedModes = supported,
                thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getSystemService(android.os.PowerManager::class.java)?.currentThermalStatus ?: -1
                } else -1,
                powerMonitorsJson = "{}",
            )
        }

        /** Reads device-provided subsystem and power-rail counters on Android 15+ when exposed. */
        fun captureWithPowerMonitors(context: Context, onCaptured: (SystemSnapshot) -> Unit) {
            val base = capture(context)
            if (Build.VERSION.SDK_INT < 35) {
                onCaptured(base)
                return
            }
            try {
                val health = context.getSystemService(SystemHealthManager::class.java)
                if (health == null) {
                    onCaptured(base)
                    return
                }
                health.getSupportedPowerMonitors(null) { monitors ->
                    if (monitors.isEmpty()) {
                        onCaptured(base)
                    } else {
                        health.getPowerMonitorReadings(monitors, null,
                            object : OutcomeReceiver<PowerMonitorReadings, RuntimeException> {
                                override fun onResult(readings: PowerMonitorReadings) {
                                    val rows = org.json.JSONArray()
                                    monitors.forEach { monitor ->
                                        rows.put(JSONObject().apply {
                                            put("name", monitor.name)
                                            put("type", monitor.type)
                                            put("energy_uws_since_boot", readings.getConsumedEnergy(monitor))
                                            put("reading_elapsed_ms", readings.getTimestampMillis(monitor))
                                        })
                                    }
                                    onCaptured(base.copy(powerMonitorsJson = JSONObject()
                                        .put("available", true)
                                        .put("monitors", rows)
                                        .toString()))
                                }

                                override fun onError(error: RuntimeException) {
                                    onCaptured(base)
                                }
                            })
                    }
                }
            } catch (_: Exception) {
                onCaptured(base)
            }
        }
    }
}

object StudyTrialStore {
    const val PREFS = "aod_study_trial"
    const val ACTIVE = "active"
    const val START_SNAPSHOT = "start_snapshot"
    const val TARGET_ELAPSED_MS = "target_elapsed_ms"
    const val PENDING_START_TARGET_MS = "pending_start_target_ms"
    const val LAST_RESULT = "last_result"
    const val REQUEST_CODE = 7319

    private const val CSV_NAME = "aod_study_trials_v2.csv"

    fun csvFile(context: Context): File = File(context.filesDir, CSV_NAME)

    fun appendCompletedTrial(context: Context, end: SystemSnapshot) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ACTIVE, false)) return

        val rawStart = prefs.getString(START_SNAPSHOT, null) ?: return
        val start = SystemSnapshot.fromJson(JSONObject(rawStart))
        val targetElapsed = prefs.getLong(TARGET_ELAPSED_MS, end.elapsedRealtimeMs)
        val elapsedMs = end.elapsedRealtimeMs - start.elapsedRealtimeMs
        val elapsedHours = elapsedMs / 3_600_000.0
        val deltaEnergyNwh = if (
            start.energyCounterNwh != Long.MIN_VALUE && end.energyCounterNwh != Long.MIN_VALUE
        ) start.energyCounterNwh - end.energyCounterNwh else Long.MIN_VALUE
        val deltaChargeUah = if (
            start.chargeCounterUah != Int.MIN_VALUE && end.chargeCounterUah != Int.MIN_VALUE
        ) start.chargeCounterUah - end.chargeCounterUah else Int.MIN_VALUE
        val powerMonitorDeltas = calculatePowerMonitorDeltas(
            start.powerMonitorsJson,
            end.powerMonitorsJson
        )

        val energyMwh: Double
        val energySource: String
        when {
            deltaEnergyNwh != Long.MIN_VALUE -> {
                energyMwh = deltaEnergyNwh / 1_000_000.0
                energySource = "BATTERY_PROPERTY_ENERGY_COUNTER"
            }
            deltaChargeUah != Int.MIN_VALUE && start.batteryVoltageMv > 0 && end.batteryVoltageMv > 0 -> {
                val meanVoltageMv = (start.batteryVoltageMv + end.batteryVoltageMv) / 2.0
                energyMwh = deltaChargeUah * meanVoltageMv / 1_000_000.0
                energySource = "CHARGE_COUNTER x mean battery voltage"
            }
            else -> {
                energyMwh = Double.NaN
                energySource = "unavailable"
            }
        }
        val averagePowerMw = if (elapsedHours > 0 && !energyMwh.isNaN()) {
            energyMwh / elapsedHours
        } else Double.NaN
        val alarmLatenessMs = end.elapsedRealtimeMs - targetElapsed

        val metadata = prefs.getString("metadata", "") ?: ""
        val data = JSONObject(metadata)
        val trialId = data.optString("trialId", "")
        val row = listOf(
            trialId,
            data.optString("pairId", ""),
            data.optString("deviceLabel", ""),
            data.optString("condition", ""),
            data.optString("durationMinutes", ""),
            data.optString("aodSetup", ""),
            data.optString("aodBrightnessPct", ""),
            data.optString("aodRefreshSettingHz", ""),
            data.optString("opticalAodFrequencyHz", ""),
            data.optString("opticalInstrument", ""),
            data.optString("pixelCoveragePct", ""),
            data.optString("patternRgb", ""),
            data.optString("notes", ""),
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.DISPLAY,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT.toString(),
            start.wallTimeMs.toString(),
            end.wallTimeMs.toString(),
            elapsedMs.toString(),
            alarmLatenessMs.toString(),
            start.batteryLevelPct.toString(),
            end.batteryLevelPct.toString(),
            start.batteryPlugType.toString(),
            end.batteryPlugType.toString(),
            start.batteryTempC.csvNumber(),
            end.batteryTempC.csvNumber(),
            start.batteryVoltageMv.toString(),
            end.batteryVoltageMv.toString(),
            start.currentNowUa.toString(),
            end.currentNowUa.toString(),
            start.currentAverageUa.toString(),
            end.currentAverageUa.toString(),
            start.chargeCounterUah.toString(),
            end.chargeCounterUah.toString(),
            deltaChargeUah.toString(),
            start.energyCounterNwh.toString(),
            end.energyCounterNwh.toString(),
            deltaEnergyNwh.toString(),
            energyMwh.csvNumber(),
            averagePowerMw.csvNumber(),
            energySource,
            start.displayState.toString(),
            end.displayState.toString(),
            start.refreshRateHz.toDouble().csvNumber(),
            end.refreshRateHz.toDouble().csvNumber(),
            start.modeId.toString(),
            end.modeId.toString(),
            start.supportedModes,
            end.supportedModes,
            start.thermalStatus.toString(),
            end.thermalStatus.toString(),
            start.powerMonitorsJson,
            end.powerMonitorsJson,
            powerMonitorDeltas,
            data.optString("deviceInventoryJson", "{}"),
        )

        val file = csvFile(context)
        val writeHeader = !file.exists() || file.length() == 0L
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { writer ->
            if (writeHeader) writer.appendLine(CSV_HEADER)
            writer.appendLine(row.joinToString(",") { it.csvEscape() })
        }

        val result = buildString {
            append("Trial $trialId saved. ")
            append("Whole-device battery energy source: $energySource. ")
            append("Energy change: ${energyMwh.csvNumber()} mWh; ")
            append("average battery power: ${averagePowerMw.csvNumber()} mW. ")
            append("OS-reported refresh: ${start.refreshRateHz.toDouble().csvNumber()} Hz at start, ")
            append("${end.refreshRateHz.toDouble().csvNumber()} Hz at end. ")
            append("These are whole-device/fuel-gauge estimates, not isolated OLED power or proof of panel scan rate.")
            append(" Component/rail deltas available: ${powerMonitorDeltas != "[]"}.")
        }
        prefs.edit()
            .putBoolean(ACTIVE, false)
            .putString(LAST_RESULT, result)
            .remove(START_SNAPSHOT)
            .remove(TARGET_ELAPSED_MS)
            .remove(PENDING_START_TARGET_MS)
            .remove("metadata")
            .apply()
    }

    private val CSV_HEADER = listOf(
        "trial_id", "pair_id", "device_label", "condition", "target_duration_min", "aod_setup",
        "aod_brightness_setting_pct", "oem_aod_refresh_setting_hz", "optical_aod_frequency_hz",
        "optical_instrument_sampling_rate", "pixel_coverage_estimate_pct", "pattern_rgb",
        "notes", "manufacturer", "brand", "model", "device_code", "product", "build_id",
        "android_release", "api_level", "start_epoch_ms", "end_epoch_ms", "elapsed_ms",
        "end_alarm_lateness_ms", "battery_level_start_pct", "battery_level_end_pct",
        "battery_plug_type_start", "battery_plug_type_end",
        "battery_temp_start_c", "battery_temp_end_c", "battery_voltage_start_mv",
        "battery_voltage_end_mv", "current_now_start_ua", "current_now_end_ua",
        "current_average_start_ua", "current_average_end_ua", "charge_counter_start_uah",
        "charge_counter_end_uah", "charge_counter_delta_uah", "energy_counter_start_nwh",
        "energy_counter_end_nwh", "energy_counter_delta_nwh", "battery_energy_delta_mwh",
        "average_whole_device_battery_power_mw", "energy_source", "display_state_start",
        "display_state_end", "os_reported_refresh_start_hz", "os_reported_refresh_end_hz",
        "display_mode_start_id", "display_mode_end_id", "supported_display_modes_start",
        "supported_display_modes_end", "thermal_status_start", "thermal_status_end",
        "power_monitors_start_json", "power_monitors_end_json", "power_monitor_energy_deltas_uws_json",
        "device_inventory_json"
    ).joinToString(",")

    private fun calculatePowerMonitorDeltas(startJson: String, endJson: String): String {
        return try {
            val startRows = JSONObject(startJson).optJSONArray("monitors") ?: org.json.JSONArray()
            val endRows = JSONObject(endJson).optJSONArray("monitors") ?: org.json.JSONArray()
            val starts = (0 until startRows.length()).mapNotNull { index ->
                val row = startRows.optJSONObject(index) ?: return@mapNotNull null
                val key = "${row.optString("name")}#${row.optInt("type")}"
                key to row.optLong("energy_uws_since_boot", Long.MIN_VALUE)
            }.toMap()
            val deltas = org.json.JSONArray()
            (0 until endRows.length()).forEach { index ->
                val row = endRows.optJSONObject(index) ?: return@forEach
                val key = "${row.optString("name")}#${row.optInt("type")}"
                val before = starts[key] ?: return@forEach
                val after = row.optLong("energy_uws_since_boot", Long.MIN_VALUE)
                if (before >= 0L && after >= before) {
                    deltas.put(JSONObject().apply {
                        put("name", row.optString("name"))
                        put("type", row.optInt("type"))
                        put("delta_energy_uws", after - before)
                    })
                }
            }
            deltas.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    private fun Double.csvNumber(): String = if (isFinite()) String.format(Locale.US, "%.4f", this) else ""

    private fun String.csvEscape(): String = "\"${replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
}

class StudyTrialReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_BEGIN_TRIAL -> beginTrial(context)
            ACTION_FINISH_TRIAL -> {
                val pending = goAsync()
                SystemSnapshot.captureWithPowerMonitors(context) { end ->
                    try {
                        StudyTrialStore.appendCompletedTrial(context, end)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun beginTrial(context: Context) {
        val prefs = context.getSharedPreferences(StudyTrialStore.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(StudyTrialStore.ACTIVE, false)) return

        val metadata = JSONObject(prefs.getString("metadata", "") ?: "")
        val durationMinutes = metadata.optLong("durationMinutes", 60L).coerceAtLeast(1L)
        val pending = goAsync()
        SystemSnapshot.captureWithPowerMonitors(context) { start ->
            try {
                val endElapsed = start.elapsedRealtimeMs + durationMinutes * 60_000L
                prefs.edit()
                    .putString(StudyTrialStore.START_SNAPSHOT, start.toJson().toString())
                    .putLong(StudyTrialStore.TARGET_ELAPSED_MS, endElapsed)
                    .remove(StudyTrialStore.PENDING_START_TARGET_MS)
                    .apply()

                val alarmManager = context.getSystemService(AlarmManager::class.java)
                val finishIntent = Intent(context, StudyTrialReceiver::class.java).setAction(ACTION_FINISH_TRIAL)
                val finishPendingIntent = PendingIntent.getBroadcast(
                    context,
                    StudyTrialStore.REQUEST_CODE + 1,
                    finishIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    endElapsed,
                    finishPendingIntent
                )
            } catch (_: Exception) {
                prefs.edit()
                    .putBoolean(StudyTrialStore.ACTIVE, false)
                    .putString(StudyTrialStore.LAST_RESULT, "Trial could not be scheduled or system telemetry could not be read. Do not use the run.")
                    .remove(StudyTrialStore.START_SNAPSHOT)
                    .remove(StudyTrialStore.TARGET_ELAPSED_MS)
                    .remove("metadata")
                    .apply()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BEGIN_TRIAL = "com.aod.study.action.BEGIN_TRIAL"
        const val ACTION_FINISH_TRIAL = "com.aod.study.action.FINISH_TRIAL"
    }
}
