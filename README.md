# AOD Study: Hyper-Optimized Always-On Display Engine (ARM64 / OLED)
Author: Akshat Pande

## Executive Summary & Goals
Ultra-low-power AOD pipeline targeting ARM64 mobile SoCs (Qualcomm Snapdragon / MediaTek Dimensity / Apple Silicon) and LP-LTPO OLED PenTile displays.

### Goals
- Achieve sub-microsecond glyph blitting latency via NEON SIMD
- Implement PenTile subpixel energy model for power-aware rendering
- Minimize MIPI-DSI transmission through selective dirty-rect refresh
- Utilize direct kernel DRM/KMS dumb-buffer atomic presentation path
- Target CPU duty cycle < 0.001% per minute for maximum sleep residency

## Core Architecture

### ARM64 NEON SIMD Blitter
1-bit/4-bit glyph rasterization into ARGB8888 framebuffers with:
- Parallel processing of 16 pixels per NEON register
- Optimized glyph cache with LRU eviction
- Zero-copy font loading into L1 cache

### PenTile Subpixel Energy Model
Diamond pixel RGBG layout power estimation incorporating:
- Quiescent panel driver bias compensation
- Dynamic subpixel emission decay modeling
- Real-time APL (Average Picture Level) calculation
- Gamma correction for perceptual brightness matching

### Selective Dirty-Rect Refresh
MIPI-DSI bandwidth optimization via:
- Per-frame diff calculation against previous buffer
- Minimum bounding box clustering of changed regions
- Adaptive refresh rate scaling based on change density
- <1% full-frame bandwidth target for static content

### Direct Kernel DRM/KMS Path
Bypassing compositor stack with:
- Dumb-buffer allocation via DRM_IOCTL_MODE_CREATE_DUMB
- Atomic page-flipping with MODE_ATOMIC test-only commit
- V-sync synchronized presentation timing
- Zero-copy GPU->display pipeline

## Benchmark Results Table

### Host Microbenchmarks (Apple Silicon M1 ARM64, clang -O3)

| Metric | Measured Value | Architecture / Details |
|--------|----------------|------------------------|
| **Glyph Blitting** | 55.00 ns / glyph | 2,327 MPix/s throughput via NEON bit-unpacking |
| **APL Calculation @ 1080x2400** | 440.25 µs | 21.93 GB/s memory bandwidth |
| **APL Calculation @ 1440x3120** | 687.88 µs | 24.33 GB/s memory bandwidth |
| **Dirty-Rect Bandwidth Reduction** | 99.23% | 80 KB dirty rect vs 10.37 MB full frame |

### Analytic Simulation Models (Theoretical OLED Panels)

- **PenTile Diamond Subpixel Power Model:** Evaluated with Samsung AMOLED RGBG emission curves, 1.5 mW quiescent driver baseline → 23.3% subpixel dynamic emission savings.
- **Projected CPU Duty Cycle:** 0.000833% per 60s clock update (assuming 500 µs active time per 1 Hz/minute refresh).

### Physical Hardware Telemetry Roadmap (Next Phase)

- **Snapdragon 8 Gen 2 / Dimensity 9300 PMIC rail measurement** via Monsoon Power Monitor.
- **Android SurfaceFlinger IPC vs Direct DRM/KMS dumb-buffer page flip** on Linux `/dev/dri/card0`.

## Building & Running

### Prerequisites
- ARM64 Linux kernel 5.10+ with DRM/KMS
- clang >= 12.0
- make
- libdrm development headers

### Compilation
```bash
make clean
make -j$(nproc)
```

### Execution
```bash
# Requires root for DRM access
sudo ./bench_aod_micro
```

Output includes real-time benchmark counters and power estimates.

## Mobile Testing & Real Device Benchmarking

### Option 1: Native Android Termux (Recommended for Hardware-Level NEON Profiling)

1. **Install Termux** from F-Droid or GitHub.
2. **Install build tools:**
   ```bash
   pkg update && pkg install -y git clang make
   ```
3. **Clone and navigate:**
   ```bash
   git clone https://github.com/akshatPANDE69/AOD-study.git
   cd AOD-study
   ```
4. **Run the automated runner:**
   ```bash
   chmod +x termux_runner.sh
   ./termux_runner.sh
   ```
5. **Enter test duration** (e.g. `30` seconds). The runner will:
   - Compile and execute the native ARM64 NEON pipeline directly on your phone's CPU cores.
   - Read real-time Linux `sysfs` PMIC / battery drain data (`/sys/class/power_supply/battery/current_now`).
   - Print a clean, **copy-pasteable Markdown table** with real measured telemetry.

---

### Option 2: Android App / APK (.apk)

- The Android project contains exploratory benchmark/demo modes and a **Study Log** for timed, screen-off paired trials. It records device/SoC and best-effort CPU/GPU inventory, processor lists, battery properties, thermal status, and OS display metadata. On Android 15+ devices that expose them, it also reads subsystem/power-rail energy counters (for example CPU/GPU/display); availability and meaning vary by OEM. Set duration in minutes, enter OEM AOD settings and optional external optical measurements, and copy or export all saved CSV trials.
- Exact-alarm access is required so the endpoint can be captured while the screen remains off. The user starts a trial, locks the phone, and exports the CSV after it finishes.
- Battery readings are whole-device fuel-gauge estimates; subsystem counters may be modeled and are device-specific. Neither source automatically isolates native AOD power. The Display Modes screen requests Android-supported modes for the app's foreground window only; it cannot change native lock-screen AOD refresh. Optical AOD frequency requires an external sensor. Do not use the foreground Live AOD demo as a substitute for native lock-screen AOD in the primary study.
- See [`RESEARCH_PROTOCOL.md`](RESEARCH_PROTOCOL.md) for the controlled comparison, exclusions, and analysis plan. The prior short benchmark and foreground demo are exploratory tools, not a validated AOD power measurement.
- Analyze an untouched export with `python analysis/analyze_trials.py path/to/aod_study_trials.csv`; the script produces a paired summary and row/pair audit beside the input CSV.
- **Download and install:** open the repository's **Actions** tab, select **Build Android APK**, open the latest successful run, and download `aod-study-debug-apk` under Artifacts. Unzip it and install `app-debug.apk` on the phone (allow installs from the app used to open the APK if Android prompts). The APK is debug-signed for research sideloading; this is not a Play Store release. A successful build confirms packaging, not validation on every study phone.
- The workflow uses the checked-in Gradle wrapper (`android/gradlew`), so no separately installed Gradle is needed. A version tag such as `v1.1.0` also publishes the APK and SHA-256 file as GitHub Release assets.

---

### Option 3: Instant Mobile Web OLED Runner (Zero-Install)

1. Open [`web/index.html`](web/index.html) in **mobile Chrome** or **Firefox**.
2. The page launches a fullscreen pure OLED black canvas benchmark.
3. Tracks live microsecond-precision frame timings.
4. Click **Copy Markdown Report** to export real device results to your clipboard.

## Repository Structure
```
AOD-study/
├── .git/                  # Git version control
├── .gitignore             # Git ignore patterns
├── Makefile               # Build automation
├── RESEARCH_LEDGER.md     # Theoretical hypotheses & frontier logs
├── aod_blitter.a          # Static library (build artifact)
├── bench_aod_micro        # Benchmark executable
├── bench/                 # Benchmark source files
├── graphify-out/          # Code analysis artifacts
├── include/               # Public headers
│   └── aod_engine.h       # Engine API interface
└── src/                   # Source code
    ├── aod_engine.c       # Main engine logic
    ├── aod_engine.o       # Object file
    ├── aod_simd_blitter.S # NEON assembly blitter
    ├── aod_simd_blitter.c # C fallback blitter
    └── aod_simd_blitter.o # Blitter object file
```

## Related Documentation
- [Research Ledger](RESEARCH_LEDGER.md): Theoretical hypotheses, experimental logs, and frontier research notes

---
*Documentation generated for AOD Study project. Last updated: $(date)*
