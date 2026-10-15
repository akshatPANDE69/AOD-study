# AOD battery-energy study: working protocol

**Status:** protocol proposal; no empirical results collected. This file deliberately does not reuse the numerical claims in `RESEARCH_LEDGER.md` because the repository does not include the underlying raw measurements or enough provenance to reproduce them.

## Research question

For each AOD-capable phone, how does enabling the phone's native Always-On Display (AOD) change whole-device battery energy use during a controlled, screen-off interval, compared with that same phone with AOD disabled? The user's interest in refresh rate is a secondary question: what refresh mode does Android report during the trial, and—only if external optical instrumentation is available—what physical panel behavior is observable during native AOD? A later exploratory question is whether the paired energy difference varies with native AOD content and settings.

The five devices identified so far are Samsung Galaxy S25, OnePlus Nord CE5, POCO X6, Nothing Phone (4a) Pro, and OPPO A79. Verify the exact model code, region, OS/build, and display technology on each handset before preregistration. Treat OPPO A79 as an LCD comparison if the exact unit is confirmed LCD. During preflight, verify whether the exact A79 software build offers a native lock-screen AOD mode. If it does not, it cannot contribute an AOD-on/AOD-off pair and must be reported only as an LCD screen-off baseline/control; do not substitute a custom foreground display and call it native AOD.

## Outcomes and claims

**Primary outcome:** per-trial average whole-device battery power, derived from the change in Android's battery energy counter over actual elapsed time when that counter is available. Report each phone's AOD-on minus AOD-off paired difference, in mW, with uncertainty across repeated pairs.

**Fallback outcome:** battery charge-counter change multiplied by the mean start/end battery voltage. Label this as a voltage-based whole-device battery-energy estimate and analyze it separately from the native energy-counter outcome. Do not combine the two sources without showing source-specific results.

Battery current at the two endpoints is a secondary descriptive snapshot, not an integrated power measurement. Battery percentage is too coarse for the primary endpoint. None of these Android readings isolates display-panel power. The paper must use “whole-device battery energy/power estimate,” not “OLED power,” unless an independent measurement actually isolates the display.

Android's energy counter is specified as remaining battery energy in nWh; charge counter is remaining capacity in µAh. Current-now is instantaneous and current-average's interval depends on the fuel gauge. Fuel-gauge hardware, resolution, update rate, and bias vary by device; Android's power-measurement guidance recommends repeated runs and longer windows when readings are noisy. Therefore the app's endpoint delta is an instrumented estimate, not a calibrated external power measurement. [BatteryManager API](https://developer.android.com/reference/android/os/BatteryManager) · [Android power measurement guidance](https://source.android.com/docs/core/power/device)

The app's `Display.getMode()`/supported-mode fields describe Android's reported display mode. They do not prove the panel's physical scan rate while AOD is active, especially while the display is off or in a vendor low-power state. Report them only as OS-reported metadata. A claim about actual AOD panel refresh requires a validated external measurement (for example, photodiode plus oscilloscope or suitable high-speed imaging) and a documented device-specific method.

`Display.Mode.getRefreshRate()` is documented as the mode refresh rate in frames per second; the API does not say that this is the panel's actual AOD scan rate. [Display.Mode API](https://developer.android.com/reference/android/view/Display.Mode.html)

## Trial conditions

For every handset that supports native AOD, use it as its own control:

1. **AOD off:** screen locked/off, native AOD disabled.
2. **Native AOD on:** screen locked/off, native AOD enabled with a specified setup.

Do not run the app's foreground Live AOD demonstration during study trials. It keeps the display awake and is not the phone's native AOD implementation.

For the first confirmatory comparison, use each AOD-capable phone's default native AOD content and record the exact clock/style, brightness mode, schedule, tap-to-show behavior, and other native settings. A later exploratory experiment may compare sparse and denser content, but content/brightness effects must not be confounded with the main AOD-on/off comparison. The LCD phone's screen-off baseline can describe standby draw, but it must not be pooled into the OLED AOD contrast.

## Controls and run procedure

- Use a fixed 60-minute interval per run for the initial study. If a device cannot keep native AOD continuously active for this interval, document its timeout and analyze it as a distinct exposure rather than silently changing the protocol.
- Start with a three-pair pilot on each AOD-capable phone to check endpoint reliability, fuel-gauge counter availability, and within-phone variability. Treat this as feasibility/pilot data, not definitive evidence. Then collect at least five valid matched pairs per phone; use pilot variance to decide whether more pairs are needed to meet a pre-stated precision target.
- Run unplugged, starting within a pre-set battery band (suggested 50–70%) and match AOD-on/off starts within 5 percentage points; do not charge during a run. The logger records plug type at endpoints; exclude any run with a plug type other than none or an unknown value pending investigation.
- Keep radios, SIM state, connectivity, notification state, location, and background activity the same between the paired runs on that phone. A practical low-variance setting is airplane mode, with Bluetooth/Wi-Fi/location disabled; if the target is ordinary use, choose and hold a fixed connectivity profile instead.
- Disable adaptive brightness and automatic display/AOD changes where the OS permits it. Keep the phone in the same location and orientation, away from direct sunlight, with a stable room temperature. Record ambient temperature and any unavoidable setting differences.
- Start/end at similar battery state of charge and battery temperature. Let the phone sit unused for about 30 minutes before a run and return to the chosen starting temperature range between runs. If restarting after setup, wait at least 10 minutes after boot before the first trial. Do not compare runs taken while charging or immediately after charging.
- Randomize the order within each pair (AOD on then off, or off then on). Spread pairs over multiple sessions. Record dates, order, exclusions, and reasons. Do not discard a run based on its measured energy result.
- The logger schedules a start after the user can lock the screen and captures an endpoint using an exact alarm. Record actual elapsed time and endpoint-alarm lateness; repeat runs if the alarm is materially late or an endpoint is missing. Confirm device-specific exact-alarm permission and battery-optimization settings before collection.
- Export the original CSV after each collection session and retain an untouched copy. Keep a separate run sheet for ambient temperature, plug state, AOD configuration, settings that cannot be read by the app, and protocol deviations.

The run sheet should have one row per run and include: pair/trial ID; phone model code and OS build; condition and randomized order; date and actual start/end time; room temperature; start/end battery percentage and temperature; start/end plug status; connectivity/SIM/radio profile; notification/background-app settings; native AOD style, schedule, brightness/tap-to-show settings; and protocol deviations or exclusion reason.

## Analysis plan

1. Preserve all raw CSVs. Validate unique trial IDs, timestamps, elapsed times, counter availability, alarm lateness, device/build identity, and missing values before calculating outcomes.
2. Mark runs invalid only by pre-specified operational rules: charging/plugged, wrong condition, missing/unsupported primary counter, incomplete interval, endpoint alarm outside the pre-specified tolerance, or documented protocol violation. Preserve invalid rows with an exclusion reason.
3. Within each device and matched pair, calculate `AOD-on average power − AOD-off average power`. Report individual pair values, mean paired difference, standard deviation, and a 95% confidence interval. Show per-device results before any pooled estimate; five distinct handset models are not a representative random sample of all phones.
4. Keep native energy-counter trials and charge-counter-derived trials separate. Report counter resolution/availability and all missingness. Do not treat current endpoint snapshots as average power.
5. Treat refresh-mode metadata as descriptive only. Do not claim that refresh rate caused an energy difference: this design does not manipulate or independently measure physical panel refresh. If refresh is a target outcome, run a separate optical-instrumentation experiment and document its sampling rate, sensor position, calibration, and how scan refresh is separated from OLED PWM dimming.
6. Do not report CPU-active percentage or wakeups/minute from the current app; it does not measure either. If Perfetto or another tracing method is used later, collect it in separate mechanistic runs because tracing itself can perturb the primary battery-energy trial.

The study logger requires a shared pair/block ID on the AOD-off and AOD-on runs. After copying the untouched app export, run `python analysis/analyze_trials.py path/to/aod_study_trials.csv`. The script independently recalculates trial energy and average power from the raw endpoint counters, then checks agreement with the app's derived columns. It writes a per-device paired summary and a row/pair inclusion audit beside the input. It checks the preregistered 2-minute timing/alarm tolerance, plug state, paired starting charge (≤5 percentage-point difference), complete condition pairs, and matching energy-counter source. Review the audit and device-specific counter quality before interpreting the summary; never edit the raw export to make a row pass.

## App and repository limitations to resolve

- The native study logger has been added locally, but the Android build currently fails because AAPT2 crashes in this Windows environment (exit code `0xC0000005`): both Gradle resource linking and a manual minimal-resource compile fail. The Kotlin sources compile when the resource-link task is bypassed, but the app has not yet been installed or exercised on a handset/emulator.
- The updated logger saves endpoint battery energy/charge, current, plug type, temperature, voltage, OS display mode, thermal status, device/SoC and best-effort CPU/GPU inventory, and user-entered AOD/optical instrument settings. On Android 15+ it attempts to read the device's supported power monitors and their cumulative energy counters (which may expose CPU, GPU, display, or shared rails); availability and definitions vary by OEM. These endpoint counters can support subsystem contrasts where available but do not identify native AOD's isolated contribution. No periodic polling is performed during screen-off trials, to limit measurement-induced wakeups.
- The app cannot access reliable panel-only power through the public Android battery APIs. A calibrated external power instrument is needed for a display-only power claim.
- The Display Modes screen requests an Android-supported refresh mode for this app's foreground window only; it cannot set native AOD refresh. The native AOD refresh/optical flicker must be measured with a photodiode or suitable high-speed instrument. The foreground AOD demo does not emulate native lock-screen AOD behavior. Existing synthetic/model outputs and unproven ledger claims are not measurements from the five phones.

## Preregistration checklist

Before collecting confirmatory data, record: exact device model codes and OS builds; primary counter selection rule; trial duration; starting state-of-charge and temperature bands; connectivity/background profile; exact-alarm tolerance; randomized run order; number of pairs; exclusion rules; primary effect and confidence interval method; and whether the target claim is whole-device battery energy or externally measured panel power.

## Selected prior work for the literature review

- Park et al., “Accurate power model for mobile AMOLED displays,” *Electronics Letters* (2015), DOI: [10.1049/el.2014.3875](https://doi.org/10.1049/el.2014.3875). Measures AMOLED module rails and converter power with sensing resistors and a DAQ, illustrating why panel-only claims need more than handset battery telemetry.
- “Fully automated OLED display power modeling for mobile devices,” *Pervasive and Mobile Computing* (2018), [ScienceDirect record](https://www.sciencedirect.com/science/article/pii/S157411921830258X). Useful for pixel-content dependence and display-power model context; its model results are not transferable to these five phones without device-specific calibration.
- “A novel LTPO AMOLED pixel circuit and driving scheme for variable refresh rate,” *Journal of Information Display* (2023), DOI: [10.1080/15980316.2023.2213848](https://doi.org/10.1080/15980316.2023.2213848). Establishes that LTPO panel designs can support very low refresh operation; it does not establish the actual AOD refresh rate of any of the study phones.

The literature search should specifically look for work on native lock-screen AOD exposure and low-power display modes. General AMOLED pixel models do not by themselves establish actual AOD power or refresh behavior on the five study devices.
