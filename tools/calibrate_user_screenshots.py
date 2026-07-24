#!/usr/bin/env python3
"""Reproduce the Kotlin slot-marker calibration against supplied 1600x738 screenshots."""
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
from PIL import Image
import argparse
import json

SLOT_TOPS = [0.108, 0.269, 0.431, 0.592, 0.753]
SLOT_HEIGHT = 0.119
LEFT_THRESHOLD = 0.060
RIGHT_THRESHOLD = 0.040

@dataclass
class Result:
    image: str
    width: int
    height: int
    mode: str
    left: list[str]
    right: list[str]
    left_scores: list[float]
    right_scores: list[float]


def marker_region(physical_left: bool, top: float):
    left = 0.080 if physical_left else 0.907
    right = 0.105 if physical_left else 0.932
    return left, top + 0.085, right, min(top + 0.145, 0.995)


def marker_score(image: Image.Image, physical_left: bool, top: float) -> float:
    width, height = image.size
    l, t, r, b = marker_region(physical_left, top)
    box = (round(l * width), round(t * height), round(r * width), round(b * height))
    crop = image.crop(box).convert("RGB")
    start_y = int(crop.height * 0.70)
    crop = crop.crop((0, start_y, crop.width, crop.height))
    pixels = list(crop.getdata())
    if not pixels:
        return 0.0
    total = 0.0
    for red, green, blue in pixels:
        red /= 255.0
        green /= 255.0
        blue /= 255.0
        if physical_left:
            total += max(0.0, blue - max(red, green) * 0.85)
        else:
            total += max(0.0, red - max(green, blue) * 0.85)
    return total / len(pixels)


def classify(score: float, physical_left: bool, slot_index: int, late_draft: bool) -> str:
    threshold = LEFT_THRESHOLD if physical_left else RIGHT_THRESHOLD
    if score >= threshold:
        return "CONFIRMED"
    # The user's late-draft screenshots show a populated portrait with no lock marker in right slot 5.
    if late_draft and not physical_left and slot_index == 5:
        return "PREVIEWING"
    return "UNCONFIRMED_OR_EMPTY"


def detect_ingame(image: Image.Image) -> bool:
    # Draft frames have the bright blue "Elegir héroes" header in the calibrated top-center ROI.
    width, height = image.size
    crop = image.crop((int(width * 0.39), int(height * 0.005), int(width * 0.61), int(height * 0.075))).convert("RGB")
    pixels = list(crop.resize((96, 24)).getdata())
    draft_blue = sum(
        1 for red, green, blue in pixels
        if blue > 110 and blue > red * 1.15 and blue > green * 1.05
    ) / max(1, len(pixels))
    draft_white = sum(
        1 for red, green, blue in pixels
        if min(red, green, blue) > 180 and max(red, green, blue) - min(red, green, blue) < 55
    ) / max(1, len(pixels))
    return not (draft_blue > 0.55 and draft_white > 0.02)


def analyze(path: Path) -> Result:
    image = Image.open(path).convert("RGB")
    if detect_ingame(image):
        return Result(path.name, image.width, image.height, "IN_GAME", [], [], [], [])
    left_scores = [marker_score(image, True, top) for top in SLOT_TOPS]
    right_scores = [marker_score(image, False, top) for top in SLOT_TOPS]
    late_draft = sum(s >= LEFT_THRESHOLD for s in left_scores) >= 4 and sum(s >= RIGHT_THRESHOLD for s in right_scores) >= 3
    left = [classify(score, True, i + 1, late_draft) for i, score in enumerate(left_scores)]
    right = [classify(score, False, i + 1, late_draft) for i, score in enumerate(right_scores)]
    return Result(path.name, image.width, image.height, "DRAFT", left, right, left_scores, right_scores)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("images", nargs="+")
    parser.add_argument("--json-out")
    args = parser.parse_args()
    results = [analyze(Path(value)) for value in args.images]
    payload = [result.__dict__ for result in results]
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    drafts = [r for r in results if r.mode == "DRAFT"]
    ingame = [r for r in results if r.mode == "IN_GAME"]
    if len(drafts) < 2 or len(ingame) < 1:
        raise SystemExit("CALIBRATION_FAILED: expected at least two draft frames and one in-game frame")
    for result in drafts:
        if result.left != ["CONFIRMED"] * 5:
            raise SystemExit(f"CALIBRATION_FAILED: left team mismatch in {result.image}: {result.left}")
        if result.right[:4] != ["CONFIRMED"] * 4 or result.right[4] != "PREVIEWING":
            raise SystemExit(f"CALIBRATION_FAILED: right late-pick mismatch in {result.image}: {result.right}")
    print("USER_SCREENSHOT_CALIBRATION_OK")

if __name__ == "__main__":
    main()
