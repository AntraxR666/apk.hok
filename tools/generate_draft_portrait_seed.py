#!/usr/bin/env python3
"""Generate deterministic V2 draft-portrait fingerprints from verified hero icons.

The Android application stores compact, non-reversible signatures rather than the
source artwork.  This generator mirrors PortraitFingerprint.fromArgb144() so the
asset can be reproduced and audited without doing image work at runtime.
"""

from __future__ import annotations

import argparse
import json
import math
import unicodedata
from pathlib import Path

import cv2
import numpy as np


EDGE_BINS = 8
SOURCE_URL = "https://honor-of-kings.fandom.com/wiki/Heroes"

# Variants model the small framing and display differences observed between the
# public icon and the in-game draft slot.  The canonical image remains first.
VARIANTS = (
    (0.000, 1.00, 0),
    (0.035, 1.00, 0),
    (0.070, 1.00, 0),
    (0.000, 0.90, -6),
    (0.000, 1.10, 6),
    (0.035, 0.92, 8),
    (0.035, 1.08, -8),
)


def normalized_hero_name(value: str) -> str:
    """Match CounterCatalog.normalize for the names in the verified icon set."""
    without_marks = "".join(
        char
        for char in unicodedata.normalize("NFD", value.strip())
        if unicodedata.category(char) != "Mn"
    )
    return " ".join(without_marks.lower().split())


def sample_coordinate(index: int, sample_count: int) -> int:
    return max(0, min(11, int((index * 11.0) / max(1, sample_count - 1))))


def transformed_sample(
    source_bgr: np.ndarray,
    crop_ratio: float,
    contrast: float,
    brightness: int,
) -> np.ndarray:
    height, width = source_bgr.shape[:2]
    inset_x = int(round(width * crop_ratio))
    inset_y = int(round(height * crop_ratio))
    right = max(inset_x + 1, width - inset_x)
    bottom = max(inset_y + 1, height - inset_y)
    cropped = source_bgr[inset_y:bottom, inset_x:right]
    adjusted = np.clip(cropped.astype(np.float32) * contrast + brightness, 0, 255)
    resized = cv2.resize(adjusted.astype(np.uint8), (12, 12), interpolation=cv2.INTER_LINEAR)
    return cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)


def bit_hash(values: list[int], threshold: float) -> int:
    result = 0
    for index, value in enumerate(values):
        if value >= threshold:
            result |= 1 << index
    return result


def fingerprint_v2(rgb: np.ndarray) -> str:
    if rgb.shape != (12, 12, 3):
        raise ValueError(f"expected a 12x12 RGB sample, got {rgb.shape}")

    red = rgb[:, :, 0].astype(np.int32)
    green = rgb[:, :, 1].astype(np.int32)
    blue = rgb[:, :, 2].astype(np.int32)
    luminance = (red * 299 + green * 587 + blue * 114) // 1000

    sampled = [
        int(luminance[sample_coordinate(y, 8), sample_coordinate(x, 8)])
        for y in range(8)
        for x in range(8)
    ]
    average_hash = bit_hash(sampled, sum(sampled) / len(sampled))

    gradient_bits: list[int] = []
    for y in range(8):
        source_y = sample_coordinate(y, 8)
        for x in range(8):
            left = luminance[source_y, sample_coordinate(x, 9)]
            right = luminance[source_y, sample_coordinate(x + 1, 9)]
            gradient_bits.append(1 if right >= left else 0)
    gradient_hash = sum(bit << index for index, bit in enumerate(gradient_bits))

    edge_totals = np.zeros((4, EDGE_BINS), dtype=np.float64)
    for y in range(1, 11):
        for x in range(1, 11):
            horizontal = float(luminance[y, x + 1] - luminance[y, x - 1])
            vertical = float(luminance[y + 1, x] - luminance[y - 1, x])
            magnitude = math.sqrt(horizontal * horizontal + vertical * vertical)
            if magnitude == 0.0:
                continue
            angle = (math.atan2(vertical, horizontal) + 2.0 * math.pi) % (
                2.0 * math.pi
            )
            edge_bin = max(
                0,
                min(EDGE_BINS - 1, int((angle / (2.0 * math.pi)) * EDGE_BINS)),
            )
            quadrant = (2 if y >= 6 else 0) + (1 if x >= 6 else 0)
            edge_totals[quadrant, edge_bin] += magnitude

    edge_signature: list[int] = []
    for bins in edge_totals:
        total = max(1.0, float(bins.sum()))
        edge_signature.extend(
            max(0, min(255, int((float(magnitude) / total) * 255.0)))
            for magnitude in bins
        )

    color_signature: list[int] = []
    for x0, y0, x1, y1 in (
        (0, 0, 6, 6),
        (6, 0, 12, 6),
        (0, 6, 6, 12),
        (6, 6, 12, 12),
    ):
        quadrant = rgb[y0:y1, x0:x1].astype(np.int32)
        color_signature.extend(
            int(quadrant[:, :, channel].sum() // quadrant[:, :, channel].size)
            for channel in range(3)
        )

    edges = ",".join(str(value) for value in edge_signature)
    colors = ",".join(str(value) for value in color_signature)
    return (
        f"v2:{average_hash:016x}:{gradient_hash:016x}:"
        f"{edges}:{colors}"
    )


def load_source(path: Path) -> np.ndarray:
    source = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
    if source is None:
        raise ValueError(f"could not read {path}")
    if source.ndim != 3:
        raise ValueError(f"expected a color image: {path}")
    if source.shape[2] == 4:
        alpha = source[:, :, 3:4].astype(np.float32) / 255.0
        foreground = source[:, :, :3].astype(np.float32)
        # The in-game slot is dark blue; compositing transparent edge pixels over
        # a dark neutral background avoids white halo fingerprints.
        background = np.full_like(foreground, 18.0)
        source = np.clip(foreground * alpha + background * (1.0 - alpha), 0, 255)
        source = source.astype(np.uint8)
    elif source.shape[2] != 3:
        raise ValueError(f"unsupported channel count in {path}")
    return source


def build_asset(icon_dir: Path, lookup_path: Path) -> dict[str, object]:
    lookup = json.loads(lookup_path.read_text(encoding="utf-8"))
    found = lookup.get("found", {})
    missing = lookup.get("missing", [])

    templates: dict[str, list[str]] = {}
    for icon_path in sorted(icon_dir.glob("*.png"), key=lambda item: item.name.casefold()):
        hero_key = normalized_hero_name(icon_path.stem)
        if icon_path.stem not in found:
            raise ValueError(f"{icon_path.name} is not present in the verified lookup")
        source = load_source(icon_path)
        variants = [
            fingerprint_v2(
                transformed_sample(source, crop_ratio, contrast, brightness)
            )
            for crop_ratio, contrast, brightness in VARIANTS
        ]
        # Deduplicate while preserving deterministic canonical-first order.
        templates[hero_key] = list(dict.fromkeys(variants))

    if len(templates) != len(found):
        raise ValueError(
            f"icon/lookup mismatch: {len(templates)} icons for {len(found)} URLs"
        )

    return {
        "schema_version": 2,
        "domain": "DRAFT_PORTRAIT",
        "source": (
            "Verified Honor of Kings hero icons transformed into non-reversible "
            "V2 fingerprints for the JKM-LX3 draft portrait interior"
        ),
        "source_url": SOURCE_URL,
        "generator": "tools/generate_draft_portrait_seed.py",
        "variant_count": len(VARIANTS),
        "missing_public_icons": sorted(missing),
        "templates": dict(sorted(templates.items())),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--icon-dir", type=Path, required=True)
    parser.add_argument("--lookup", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    asset = build_asset(args.icon_dir, args.lookup)
    args.output.write_text(
        json.dumps(asset, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        "DRAFT_PORTRAIT_SEED_V2_OK "
        f"heroes={len(asset['templates'])} variants={asset['variant_count']}"
    )


if __name__ == "__main__":
    main()
