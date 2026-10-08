#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <arm_neon.h>

#define __ARM_NEON_IS_AVAILABLE __ARM_NEON

uint64_t aod_neon_calc_apl(const uint8_t *framebuffer, uint64_t total_pixels) {
    if (total_pixels == 0) return 0;

    if (__ARM_NEON_IS_AVAILABLE) {
        uint64_t sum = 0;
        uint32_t pixel_count = (uint32_t)total_pixels;
        const uint8_t *src = framebuffer;

        while (pixel_count >= 16) {
            uint8x16x4_t rgba = vld4q_u8(src);
            src += 64;
            pixel_count -= 16;

            uint8x8_t r_low = vget_low_u8(rgba.val[2]);
            uint8x8_t g_low = vget_low_u8(rgba.val[1]);
            uint8x8_t b_low = vget_low_u8(rgba.val[0]);

            uint16x8_t r = vaddl_u8(r_low, vdup_n_u8(0));
            uint16x8_t g = vaddl_u8(g_low, vdup_n_u8(0));
            uint16x8_t b = vaddl_u8(b_low, vdup_n_u8(0));

            uint16x8_t r_scaled = vmulq_u16(r, vdupq_n_u16(54));
            uint16x8_t g_scaled = vmulq_u16(g, vdupq_n_u16(183));
            uint16x8_t b_scaled = vmulq_u16(b, vdupq_n_u16(19));

            uint16x8_t lum_low = vaddq_u16(vaddq_u16(r_scaled, g_scaled), b_scaled);
            lum_low = vshrq_n_u16(lum_low, 8);

            uint8x8_t r_high = vget_high_u8(rgba.val[2]);
            uint8x8_t g_high = vget_high_u8(rgba.val[1]);
            uint8x8_t b_high = vget_high_u8(rgba.val[0]);

            uint16x8_t r2 = vaddl_u8(r_high, vdup_n_u8(0));
            uint16x8_t g2 = vaddl_u8(g_high, vdup_n_u8(0));
            uint16x8_t b2 = vaddl_u8(b_high, vdup_n_u8(0));

            uint16x8_t r2_scaled = vmulq_u16(r2, vdupq_n_u16(54));
            uint16x8_t g2_scaled = vmulq_u16(g2, vdupq_n_u16(183));
            uint16x8_t b2_scaled = vmulq_u16(b2, vdupq_n_u16(19));

            uint16x8_t lum_high = vaddq_u16(vaddq_u16(r2_scaled, g2_scaled), b2_scaled);
            lum_high = vshrq_n_u16(lum_high, 8);

            sum += vgetq_lane_u16(lum_low, 0) + vgetq_lane_u16(lum_low, 1) +
                   vgetq_lane_u16(lum_low, 2) + vgetq_lane_u16(lum_low, 3) +
                   vgetq_lane_u16(lum_low, 4) + vgetq_lane_u16(lum_low, 5) +
                   vgetq_lane_u16(lum_low, 6) + vgetq_lane_u16(lum_low, 7) +
                   vgetq_lane_u16(lum_high, 0) + vgetq_lane_u16(lum_high, 1) +
                   vgetq_lane_u16(lum_high, 2) + vgetq_lane_u16(lum_high, 3) +
                   vgetq_lane_u16(lum_high, 4) + vgetq_lane_u16(lum_high, 5) +
                   vgetq_lane_u16(lum_high, 6) + vgetq_lane_u16(lum_high, 7);
        }

        while (pixel_count > 0) {
            sum += (src[2] * 54 + src[1] * 183 + src[0] * 19) >> 8;
            src += 4;
            pixel_count -= 4;
        }

        return sum;
    } else {
        uint64_t sum = 0;
        uint32_t pixel_count = (uint32_t)total_pixels;
        const uint8_t *src = framebuffer;

        while (pixel_count >= 16) {
            uint32_t lum = 0;
            for (int i = 0; i < 16; i++) {
                lum += (src[i * 4 + 2] * 54 + src[i * 4 + 1] * 183 + src[i * 4] * 19) >> 8;
            }
            sum += lum;
            src += 64;
            pixel_count -= 16;
        }

        while (pixel_count > 0) {
            sum += (src[2] * 54 + src[1] * 183 + src[0] * 19) >> 8;
            src += 4;
            pixel_count--;
        }

        return sum;
    }
}

static void aod_neon_blit_glyph_1bpp_neon(
    uint8_t *dst,
    uint32_t dst_stride,
    const uint8_t *glyph_bits,
    uint32_t glyph_w,
    uint32_t glyph_h,
    uint32_t fg_color
) {
    static const uint8_t bit_masks[8] = {
        0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01
    };
    const uint8x8_t vmask = vld1_u8(bit_masks);

    const uint8_t fg_b =  fg_color        & 0xFF;
    const uint8_t fg_g = (fg_color >>  8) & 0xFF;
    const uint8_t fg_r = (fg_color >> 16) & 0xFF;
    const uint8_t fg_a = (fg_color >> 24) & 0xFF;

    const uint8x8_t vfg_b = vdup_n_u8(fg_b);
    const uint8x8_t vfg_g = vdup_n_u8(fg_g);
    const uint8x8_t vfg_r = vdup_n_u8(fg_r);
    const uint8x8_t vfg_a = vdup_n_u8(fg_a);
    const uint8x8_t vzero = vdup_n_u8(0);

    const uint32_t row_bytes = (glyph_w + 7) / 8;

    for (uint32_t y = 0; y < glyph_h; ++y) {
        const uint8_t *src_row = glyph_bits + y * row_bytes;
        uint8_t *dst_row = dst + y * dst_stride;

        uint32_t x = 0;
        for (; x + 8 <= glyph_w; x += 8) {
            uint8_t glyph_byte = src_row[x >> 3];
            uint8x8_t vbyte = vdup_n_u8(glyph_byte);
            uint8x8_t vmask_pix = vtst_u8(vbyte, vmask);

            uint8x8_t out_b = vbsl_u8(vmask_pix, vfg_b, vzero);
            uint8x8_t out_g = vbsl_u8(vmask_pix, vfg_g, vzero);
            uint8x8_t out_r = vbsl_u8(vmask_pix, vfg_r, vzero);
            uint8x8_t out_a = vbsl_u8(vmask_pix, vfg_a, vzero);

            uint8x8x4_t out;
            out.val[0] = out_b;
            out.val[1] = out_g;
            out.val[2] = out_r;
            out.val[3] = out_a;

            vst4_u8(dst_row + x * 4, out);
        }

        for (; x < glyph_w; ++x) {
            uint8_t glyph_byte = src_row[x >> 3];
            uint8_t bit = (glyph_byte >> (7 - (x & 7))) & 1;
            uint32_t pixel = bit ? fg_color : 0;
            *((uint32_t *)(dst_row + x * 4)) = pixel;
        }
    }
}

void aod_neon_blit_glyph_1bpp(
    uint8_t *dst,
    uint32_t dst_stride,
    const uint8_t *glyph_bits,
    uint32_t glyph_w,
    uint32_t glyph_h,
    uint32_t fg_color
) {
#ifdef __ARM_NEON
    aod_neon_blit_glyph_1bpp_neon(dst, dst_stride, glyph_bits, glyph_w, glyph_h, fg_color);
#else
    uint32_t row_size = (glyph_w + 7) / 8;

    for (uint32_t y = 0; y < glyph_h; y++) {
        const uint8_t *src_row = glyph_bits + y * row_size;
        uint8_t *dst_row = dst + y * dst_stride;

        uint32_t x = 0;
        while (x < glyph_w) {
            if (src_row[x / 8] & (0x80 >> (x % 8))) {
                *(uint32_t *)(dst_row + x * 4) = fg_color;
            }
            x++;
        }
    }
#endif
}