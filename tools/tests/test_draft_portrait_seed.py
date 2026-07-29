import unittest

import numpy as np

from tools.generate_draft_portrait_seed import (
    SOURCE_ICON_OVERRIDES,
    VARIANTS,
    fingerprint_v3,
    transformed_sample,
)


class DraftPortraitSeedTest(unittest.TestCase):
    def test_v3_fingerprint_contains_spatial_luminance(self):
        rgb = np.zeros((12, 12, 3), dtype=np.uint8)
        rgb[:, :6] = (20, 40, 80)
        rgb[:, 6:] = (220, 180, 120)

        encoded = fingerprint_v3(rgb)

        parts = encoded.split(":")
        self.assertEqual("v3", parts[0])
        self.assertEqual(6, len(parts))
        self.assertEqual(64, len(parts[5].split(",")))

    def test_variants_cover_observed_scale_shift_and_tone_without_explosion(self):
        geometry = {
            (variant.scale, variant.offset_x, variant.offset_y)
            for variant in VARIANTS
        }
        tone = {(variant.contrast, variant.brightness) for variant in VARIANTS}

        self.assertIn((1.0, 0.10, -0.10), geometry)
        self.assertIn((0.84, 0.05, -0.05), geometry)
        self.assertIn((0.92, 0.0, -0.05), geometry)
        self.assertTrue(any(contrast < 1.0 for contrast, _ in tone))
        self.assertTrue(any(contrast > 1.0 for contrast, _ in tone))
        self.assertLessEqual(len(VARIANTS), 16)

    def test_shifted_crop_remains_a_valid_twelve_pixel_sample(self):
        source = np.arange(128 * 128 * 3, dtype=np.uint8).reshape(128, 128, 3)
        variant = next(
            item
            for item in VARIANTS
            if (item.scale, item.offset_x, item.offset_y) == (1.0, 0.10, -0.10)
        )

        sample = transformed_sample(source, variant)

        self.assertEqual((12, 12, 3), sample.shape)

    def test_current_names_can_use_verified_historical_icon_files(self):
        self.assertIn("Ao'yin", SOURCE_ICON_OVERRIDES)
        self.assertIn("Gao Changgong", SOURCE_ICON_OVERRIDES)


if __name__ == "__main__":
    unittest.main()
