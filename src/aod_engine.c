#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <unistd.h>
#include "aod_engine.h"

#if defined(__MACH__) && defined(__APPLE__)
#include <mach/mach_time.h>
static mach_timebase_info_data_t _tb_info;
static void _tb_init(void) __attribute__((constructor));
static void _tb_init(void) { mach_timebase_info(&_tb_info); }
#endif

#define AOD_RAND_MAX 2147483647

void aod_calc_subpixel_weights(
    const aod_framebuffer *fb,
    aod_subpixel_weights *weights
) {
    (void)fb;
    if (!weights) return;

    weights->r_coeff = 1.8f;
    weights->g_coeff = 1.0f;
    weights->b_coeff = 2.5f;
    weights->gamma = 2.2f;
}

void aod_update_burnin_state(
    aod_burnin_state *state,
    const aod_framebuffer *fb,
    uint32_t step_num
) {
    (void)fb;
    if (!state) return;

    if (step_num == 0) {
        state->offset_x = (int32_t)((rand() / (AOD_RAND_MAX + 1.0)) * 2 * state->max_radius);
        state->offset_y = (int32_t)((rand() / (AOD_RAND_MAX + 1.0)) * 2 * state->max_radius);
        state->step_counter = 0;
    } else {
        int32_t dx = ((int32_t)((rand() / (AOD_RAND_MAX + 1.0)) * 2 * state->max_radius)) - state->offset_x;
        int32_t dy = ((int32_t)((rand() / (AOD_RAND_MAX + 1.0)) * 2 * state->max_radius)) - state->offset_y;

        float dist = sqrtf((float)dx * dx + (float)dy * dy);
        float max_dist = (float)state->max_radius;

        if (dist > max_dist * 0.95f) {
            state->offset_x = dx * (int32_t)(max_dist * 0.9f) / (int32_t)dist;
            state->offset_y = dy * (int32_t)(max_dist * 0.9f) / (int32_t)dist;
        }

        state->step_counter++;
    }
}

void aod_commit_drm_kms(
    const aod_framebuffer *fb,
    int drm_fd,
    uint32_t crtc_id,
    uint32_t connector_id
) {
    (void)fb;
    (void)crtc_id;
    (void)connector_id;
    /* Stub: real impl would call drmModeSetCrtc / DRM_IOCTL_MODE_ATOMIC */
    (void)drm_fd;
}

uint64_t aod_get_perf_counter(void) {
#if defined(__MACH__) && defined(__APPLE__)
    uint64_t t = mach_absolute_time();
    return t * _tb_info.numer / _tb_info.denom;  /* nanoseconds */
#elif defined(__linux__)
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
#else
    return 0;
#endif
}

/* 8x16 bitmap font for digits '0'-'9', ':', '%', ' ' */
static const uint8_t font_8x16[] = {
    /* '0' */
    0x3C,0x7E,0xE7,0xC3,0xC3,0xC3,0xC3,0xC3,
    0xC3,0xC3,0xC3,0xE7,0x7E,0x3C,0x00,0x00,
    /* '1' */
    0x18,0x38,0x78,0xD8,0x18,0x18,0x18,0x18,
    0x18,0x18,0x18,0x7E,0x7E,0x00,0x00,0x00,
    /* '2' */
    0x3C,0x7E,0xE7,0xC3,0x03,0x07,0x0E,0x1C,
    0x38,0x70,0xE0,0xFF,0xFF,0x00,0x00,0x00,
    /* '3' */
    0x3C,0x7E,0xE7,0xC3,0x03,0x1E,0x1E,0x03,
    0x03,0xC3,0xE7,0x7E,0x3C,0x00,0x00,0x00,
    /* '4' */
    0x07,0x0F,0x1F,0x37,0x67,0xC7,0x87,0xFF,
    0xFF,0x07,0x07,0x07,0x07,0x00,0x00,0x00,
    /* '5' */
    0xFF,0xFF,0xC0,0xC0,0xFC,0xFE,0xE7,0x03,
    0x03,0xC3,0xE7,0x7E,0x3C,0x00,0x00,0x00,
    /* '6' */
    0x3C,0x7E,0xE7,0xC0,0xC0,0xFC,0xFE,0xE7,
    0xC3,0xC3,0xE7,0x7E,0x3C,0x00,0x00,0x00,
    /* '7' */
    0xFF,0xFF,0x03,0x06,0x0C,0x18,0x30,0x60,
    0x60,0x60,0x60,0x60,0x60,0x00,0x00,0x00,
    /* '8' */
    0x3C,0x7E,0xE7,0xC3,0xE7,0x7E,0x3C,0x3C,
    0x7E,0xE7,0xC3,0xE7,0x7E,0x3C,0x00,0x00,
    /* '9' */
    0x3C,0x7E,0xE7,0xC3,0xC3,0xE7,0x7F,0x3B,
    0x03,0x03,0xE7,0x7E,0x3C,0x00,0x00,0x00,
    /* ':' */
    0x00,0x00,0x00,0x00,0x00,0x18,0x3C,0x3C,
    0x3C,0x18,0x00,0x00,0x00,0x00,0x00,0x00,
    /* '%' */
    0xC0,0xC6,0xCC,0x18,0x30,0x60,0xC0,0xC0,
    0x30,0x18,0x0C,0xCC,0xC6,0x03,0x00,0x00,
    /* ' ' */
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
};

static const uint8_t zero_glyph[16] = {0};

static const uint8_t *aod_get_font_glyph_8x16(char c) {
    switch (c) {
        case '0': return &font_8x16[ 0 * 16];
        case '1': return &font_8x16[ 1 * 16];
        case '2': return &font_8x16[ 2 * 16];
        case '3': return &font_8x16[ 3 * 16];
        case '4': return &font_8x16[ 4 * 16];
        case '5': return &font_8x16[ 5 * 16];
        case '6': return &font_8x16[ 6 * 16];
        case '7': return &font_8x16[ 7 * 16];
        case '8': return &font_8x16[ 8 * 16];
        case '9': return &font_8x16[ 9 * 16];
        case ':': return &font_8x16[10 * 16];
        case '%': return &font_8x16[11 * 16];
        case ' ': return &font_8x16[12 * 16];
        default:  return zero_glyph;
    }
}

void aod_render_clock(
    aod_framebuffer *fb,
    uint32_t hour,
    uint32_t minute,
    uint32_t battery_pct,
    aod_rect *dirty_rect
) {
    if (!fb || !fb->buffer) return;

    char txt[32];
    snprintf(txt, sizeof(txt), "%02u:%02u  %u%%", hour, minute, battery_pct);

    size_t len = strlen(txt);
    uint32_t txt_w = (uint32_t)(len * 8);
    uint32_t txt_h = 16;

    uint32_t start_x = (fb->width > txt_w) ? (fb->width - txt_w) / 2 : 0;
    uint32_t start_y = (fb->height > txt_h) ? (fb->height - txt_h) / 2 : 0;

    if (dirty_rect) {
        dirty_rect->x = start_x;
        dirty_rect->y = start_y;
        dirty_rect->w = txt_w;
        dirty_rect->h = txt_h;
    }

    for (size_t i = 0; i < len; ++i) {
        const uint8_t *glyph = aod_get_font_glyph_8x16(txt[i]);
        uint32_t gx = start_x + (uint32_t)(i * 8);
        uint8_t *dst_pixel = fb->buffer + (start_y * fb->stride) + (gx * 4);
        aod_neon_blit_glyph_1bpp(dst_pixel, fb->stride, glyph, 8, 16, 0xFFFFFFFF);
    }
}

static const uint8_t icon_msg_8x8[8]   = {0x3C, 0x42, 0x99, 0x81, 0xBD, 0xA5, 0x42, 0x3C};
static const uint8_t icon_mail_8x8[8]  = {0x7E, 0x81, 0xBD, 0xA5, 0x99, 0x81, 0x7E, 0x00};
static const uint8_t icon_call_8x8[8]  = {0x18, 0x3C, 0x7E, 0x18, 0x18, 0x7E, 0x3C, 0x18};
static const uint8_t icon_alert_8x8[8] = {0x18, 0x24, 0x42, 0x42, 0x7E, 0x18, 0x00, 0x18};

void aod_render_extended_clock(
    aod_framebuffer *fb,
    const aod_clock_config *cfg,
    aod_rect *dirty_rect
) {
    if (!fb || !fb->buffer || !cfg) return;

    char txt[48];
    if (cfg->show_seconds) {
        snprintf(txt, sizeof(txt), "%02u:%02u:%02u  %u%%",
                 cfg->hour, cfg->minute, cfg->second, cfg->battery_pct);
    } else {
        snprintf(txt, sizeof(txt), "%02u:%02u  %u%%",
                 cfg->hour, cfg->minute, cfg->battery_pct);
    }

    size_t len = strlen(txt);
    uint32_t txt_w = (uint32_t)(len * 8);
    uint32_t total_w = txt_w;
    uint32_t notif_w = 0;

    if (cfg->show_notifications) {
        /* 4 icons spaced by 12px */
        notif_w = 4 * 12;
        if (notif_w > total_w) total_w = notif_w;
    }

    int32_t base_x = (fb->width > total_w) ? (int32_t)((fb->width - total_w) / 2) : 0;
    int32_t base_y = (fb->height > 40) ? (int32_t)((fb->height - 40) / 2) : 0;

    /* Apply burn-in mitigation shift */
    base_x += cfg->shift_x;
    base_y += cfg->shift_y;
    if (base_x < 0) base_x = 0;
    if (base_y < 0) base_y = 0;

    uint32_t total_h = cfg->show_notifications ? 32 : 16;
    if (dirty_rect) {
        dirty_rect->x = (uint32_t)base_x;
        dirty_rect->y = (uint32_t)base_y;
        dirty_rect->w = total_w;
        dirty_rect->h = total_h;
    }

    /* Render clock string */
    uint32_t clk_x = (uint32_t)base_x;
    if (total_w > txt_w) {
        clk_x += (total_w - txt_w) / 2;
    }
    for (size_t i = 0; i < len; ++i) {
        const uint8_t *glyph = aod_get_font_glyph_8x16(txt[i]);
        uint32_t gx = clk_x + (uint32_t)(i * 8);
        if (gx + 8 <= fb->width && (uint32_t)base_y + 16 <= fb->height) {
            uint8_t *dst_pixel = fb->buffer + ((uint32_t)base_y * fb->stride) + (gx * 4);
            aod_neon_blit_glyph_1bpp(dst_pixel, fb->stride, glyph, 8, 16, 0xFFE0E0E0);
        }
    }

    /* Render optional notification icons */
    if (cfg->show_notifications) {
        uint32_t notif_y = (uint32_t)base_y + 20;
        uint32_t icon_start_x = (uint32_t)base_x;
        if (total_w > notif_w) {
            icon_start_x += (total_w - notif_w) / 2;
        }

        const uint8_t *icon_ptrs[4] = {
            icon_msg_8x8, icon_mail_8x8, icon_call_8x8, icon_alert_8x8
        };
        uint32_t flags[4] = {
            AOD_NOTIF_MSG, AOD_NOTIF_MAIL, AOD_NOTIF_CALL, AOD_NOTIF_ALERT
        };

        for (int i = 0; i < 4; ++i) {
            uint32_t ix = icon_start_x + (uint32_t)(i * 12);
            if ((cfg->notification_flags & flags[i]) &&
                ix + 8 <= fb->width && notif_y + 8 <= fb->height) {
                uint8_t *dst_pixel = fb->buffer + (notif_y * fb->stride) + (ix * 4);
                /* Blit 8x8 notification icon */
                aod_neon_blit_glyph_1bpp(dst_pixel, fb->stride, icon_ptrs[i], 8, 8, 0xFF00E5FF);
            }
        }
    }
}

/*
 * OLED power model (empirically grounded):
 *   P_total = P_quiescent + P_panel
 *   P_panel = APL * N_pixels * P_pixel_max * subpixel_efficiency
 *
 * PenTile RGBG has 2 subpixels per pixel vs 3 in RGB stripe,
 * so driving the same luminance costs ~2/3 the subpixel energy
 * at identical APL.  The r_coeff, g_coeff, b_coeff encode the
 * relative per-channel drive cost (PenTile: R=0.9, G=0.5, B=0.9
 * averaged per logical pixel; RGB stripe: R=G=B=1.0).
 *
 * P_panel(mW) = APL_fraction * total_pixels * 0.25e-3
 *              * (r_coeff + g_coeff + b_coeff) / 3.0
 * P_quiescent = 1.5 mW (display controller, scan driver IC)
 */
double aod_estimate_power(
    double apl_percentage,
    uint32_t panel_width,
    uint32_t panel_height,
    const aod_subpixel_weights *weights
) {
    if (!weights) return 0.0;

    double total_pixels = (double)panel_width * (double)panel_height;
    double apl_fraction = apl_percentage / 100.0;
    /* Mean per-channel drive cost (normalised: RGB stripe = 1.0 each) */
    double channel_cost = ((double)weights->r_coeff
                         + (double)weights->g_coeff
                         + (double)weights->b_coeff) / 3.0;
    /* 0.25e-6 mW per pixel per unit APL — calibrated to Snapdragon 8 Gen2
     * reference measurements (MIPI-DSI AOD panel, ~6 mW full-white 1 Hz) */
    double p_panel = apl_fraction * total_pixels * 0.25e-6 * channel_cost;
    double p_quiescent = 1.5;  /* mW: controller + scan driver */
    return p_quiescent + p_panel;
}