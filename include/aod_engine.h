#ifndef AOD_ENGINE_H
#define AOD_ENGINE_H

#include <stdint.h>
#include <stddef.h>

#define AOD_PIXEL_ARGB8888 0
#define AOD_PIXEL_PENTILE_RGBG 1
#define AOD_PIXEL_RGB565 2

typedef struct {
    uint32_t x;
    uint32_t y;
    uint32_t w;
    uint32_t h;
} aod_rect;

typedef struct {
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    uint32_t format;
    uint8_t *buffer;
    uint32_t dumb_handle;
} aod_framebuffer;

typedef struct {
    float r_coeff;
    float g_coeff;
    float b_coeff;
    float gamma;
} aod_subpixel_weights;

typedef struct {
    int32_t offset_x;
    int32_t offset_y;
    uint32_t step_counter;
    uint32_t max_radius;
} aod_burnin_state;

typedef struct {
    double apl_percentage;
    double estimated_power_mw;
    uint64_t dirty_bytes;
    uint64_t total_bytes;
    uint64_t render_cycles;
} aod_stats;

void aod_neon_blit_glyph_1bpp(
    uint8_t *dst,
    uint32_t dst_stride,
    const uint8_t *glyph_bits,
    uint32_t glyph_w,
    uint32_t glyph_h,
    uint32_t fg_color
);

uint64_t aod_neon_calc_apl(
    const uint8_t *framebuffer,
    uint64_t total_pixels
);

void aod_calc_subpixel_weights(
    const aod_framebuffer *fb,
    aod_subpixel_weights *weights
);

void aod_update_burnin_state(
    aod_burnin_state *state,
    const aod_framebuffer *fb,
    uint32_t step_num
);

void aod_commit_drm_kms(
    const aod_framebuffer *fb,
    int drm_fd,
    uint32_t crtc_id,
    uint32_t connector_id
);

#define AOD_NOTIF_MSG   (1u << 0)
#define AOD_NOTIF_MAIL  (1u << 1)
#define AOD_NOTIF_CALL  (1u << 2)
#define AOD_NOTIF_ALERT (1u << 3)

typedef struct {
    uint32_t hour;
    uint32_t minute;
    uint32_t second;
    uint32_t show_seconds;
    uint32_t battery_pct;
    uint32_t show_notifications;
    uint32_t notification_flags;
    int32_t shift_x;
    int32_t shift_y;
} aod_clock_config;

void aod_render_clock(
    aod_framebuffer *fb,
    uint32_t hour,
    uint32_t minute,
    uint32_t battery_pct,
    aod_rect *dirty_rect
);

void aod_render_extended_clock(
    aod_framebuffer *fb,
    const aod_clock_config *cfg,
    aod_rect *dirty_rect
);

double aod_estimate_power(
    double apl_percentage,
    uint32_t panel_width,
    uint32_t panel_height,
    const aod_subpixel_weights *weights
);

uint64_t aod_get_perf_counter(void);

#endif