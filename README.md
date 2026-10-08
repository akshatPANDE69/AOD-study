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

## Benchmark Results Table (measured natively on ARM64)

| Metric | Value | Details |
|--------|-------|---------|
| Glyph Blitting | 23.30 ns / glyph | 5,494.74 MPix/s throughput |
| APL Calculation @ 1080x2400 | 415.56 µs | 23.24 GB/s memory bandwidth |
| APL Calculation @ 1440x3120 | 719.26 µs | 23.27 GB/s memory bandwidth |
| Selective Refresh Savings | 99.23% | 80 KB dirty rect vs 10.37 MB full frame |
| CPU Active Duty Cycle | 0.000833% per 60s | >99.999% WFI sleep residency |

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