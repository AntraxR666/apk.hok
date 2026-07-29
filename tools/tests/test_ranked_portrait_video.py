import unittest

import numpy as np

from tools.validate_ranked_portrait_video import (
    Fingerprint,
    compare_expected_slots,
    consensus,
    fingerprint_distance,
    portrait_rect,
    select_match,
)


class RankedPortraitVideoValidatorTest(unittest.TestCase):
    def test_native_geometry_scales_to_capture_and_recording(self):
        native = portrait_rect(2340, 1080, physical_left=True, slot_index=1)
        capture = portrait_rect(1170, 540, physical_left=True, slot_index=1)
        recording = portrait_rect(848, 392, physical_left=True, slot_index=1)

        self.assertLessEqual(abs(native[0] / 2 - capture[0]), 1)
        self.assertAlmostEqual(native[0] / 2340, recording[0] / 848, places=3)
        self.assertGreater(recording[2], recording[0])
        self.assertGreater(recording[3], recording[1])

    def test_edge_signature_contributes_to_distance(self):
        base = Fingerprint(
            average_hash=0,
            gradient_hash=0,
            edges=np.zeros(32, dtype=np.int32),
            colors=np.full(12, 100, dtype=np.int32),
        )
        changed = Fingerprint(
            average_hash=0,
            gradient_hash=0,
            edges=np.full(32, 255, dtype=np.int32),
            colors=np.full(12, 100, dtype=np.int32),
        )

        self.assertAlmostEqual(0.20, fingerprint_distance(base, changed), places=4)

    def test_close_top_two_remain_ambiguous_with_candidates(self):
        accepted, candidates = select_match(
            [("Lam", 0.10), ("Luna", 0.115), ("Angela", 0.30)],
            visual_confidence=0.90,
        )

        self.assertIsNone(accepted)
        self.assertEqual(["Lam", "Luna", "Angela"], [item["hero"] for item in candidates])

    def test_v3_spatial_signature_is_brightness_tolerant(self):
        pattern = np.arange(64, dtype=np.int32)
        original = Fingerprint(
            average_hash=0,
            gradient_hash=0,
            edges=np.zeros(32, dtype=np.int32),
            colors=np.full(12, 100, dtype=np.int32),
            version=3,
            spatial_luminance=pattern,
        )
        brighter = Fingerprint(
            average_hash=0,
            gradient_hash=0,
            edges=np.zeros(32, dtype=np.int32),
            colors=np.full(12, 125, dtype=np.int32),
            version=3,
            spatial_luminance=pattern + 25,
        )

        self.assertLess(fingerprint_distance(original, brighter), 0.02)

    def test_calibrated_clear_match_is_accepted(self):
        accepted, _ = select_match(
            [("Xiao Qiao", 0.17), ("Flowborn (Tank)", 0.25)],
            visual_confidence=0.65,
        )

        self.assertEqual("Xiao Qiao", accepted)

    def test_consensus_requires_three_of_five_for_same_slot(self):
        observations = ["Angela", None, "Angela", "Lam", "Angela"]

        result = consensus(observations, required_hits=3, history_size=5)

        self.assertEqual("Angela", result["hero"])
        self.assertEqual("DETECTED", result["status"])

    def test_expected_slot_comparison_reports_only_real_mismatches(self):
        report = {
            "slots": {
                "ALLY:1": {
                    "consensus": {"status": "DETECTED", "hero": "yao"}
                },
                "ENEMY:1": {
                    "consensus": {"status": "UNCERTAIN", "hero": None}
                },
            }
        }

        mismatches = compare_expected_slots(
            report,
            {"ALLY:1": "Yao", "ENEMY:1": "Arthur"},
        )

        self.assertEqual(1, len(mismatches))
        self.assertIn("ENEMY:1", mismatches[0])


if __name__ == "__main__":
    unittest.main()
