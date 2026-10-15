package com.aod.study

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Best-effort, read-only device inventory. Unreadable OEM/kernel paths are left blank. */
object DeviceTelemetry {
    fun inventory(context: Context): JSONObject {
        val present = readText("/sys/devices/system/cpu/present")
        val online = readText("/sys/devices/system/cpu/online")
        val cpuInfo = readText("/proc/cpuinfo")
        val cpuDescriptions = cpuInfo.lineSequence()
            .filter { line ->
                val key = line.substringBefore(':').trim().lowercase()
                key in setOf("processor", "model name", "hardware", "cpu implementer", "cpu part", "features")
            }
            .map { it.trim() }
            .toList()

        val coreFrequencies = JSONArray()
        val onlineIds = expandCpuList(online)
        onlineIds.forEach { id ->
            val khz = readText("/sys/devices/system/cpu/cpu$id/cpufreq/scaling_cur_freq")
            if (khz.isNotBlank()) {
                coreFrequencies.put(JSONObject().put("cpu", id).put("current_khz", khz.toLongOrNull() ?: JSONObject.NULL))
            }
        }

        val gpuPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/misc/mali0/device/name",
            "/sys/class/devfreq/gpu/name",
            "/sys/devices/platform/gpusysfs/gpu_model"
        )
        val gpuDetails = JSONArray()
        gpuPaths.forEach { path ->
            val value = readText(path)
            if (value.isNotBlank()) gpuDetails.put(JSONObject().put("path", path).put("value", value))
        }

        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)

        return JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("hardware", Build.HARDWARE)
            put("soc_manufacturer", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else "")
            put("soc_model", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "")
            put("android_release", Build.VERSION.RELEASE)
            put("api_level", Build.VERSION.SDK_INT)
            put("build_fingerprint", Build.FINGERPRINT)
            put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            put("runtime_processors", Runtime.getRuntime().availableProcessors())
            put("cpu_present_list", present)
            put("cpu_online_list", online)
            put("cpu_descriptions", JSONArray(cpuDescriptions))
            put("cpu_frequency_samples_khz", coreFrequencies)
            put("cpu_frequency_measurement_note", "Point-in-time inventory reading only; not a trial average or power measurement")
            put("gpu_kernel_details_best_effort", gpuDetails)
            put("gpu_kernel_details_note", if (gpuDetails.length() == 0) "Not exposed to this app by the device kernel" else "OEM/kernel paths; validate before interpretation")
            put("display_width_px", display?.mode?.physicalWidth ?: JSONObject.NULL)
            put("display_height_px", display?.mode?.physicalHeight ?: JSONObject.NULL)
            put("display_active_mode_id", display?.mode?.modeId ?: JSONObject.NULL)
            put("display_os_reported_refresh_hz", display?.refreshRate?.toDouble() ?: JSONObject.NULL)
            put("inventory_capture_wall_time_ms", System.currentTimeMillis())
            put("inventory_capture_elapsed_ms", SystemClock.elapsedRealtime())
        }
    }

    private fun readText(path: String): String = try {
        File(path).takeIf { it.canRead() }?.readText()?.trim().orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun expandCpuList(value: String): List<Int> {
        if (value.isBlank()) return emptyList()
        return value.split(',').flatMap { part ->
            val bounds = part.trim().split('-')
            if (bounds.size == 2) {
                val start = bounds[0].toIntOrNull()
                val end = bounds[1].toIntOrNull()
                if (start != null && end != null && end >= start && end - start < 512) (start..end).toList() else emptyList()
            } else {
                listOfNotNull(part.trim().toIntOrNull())
            }
        }
    }
}
