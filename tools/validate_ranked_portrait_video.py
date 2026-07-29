#!/usr/bin/env python3
"""Validate ranked draft portrait recognition against a supplied recording.

This host-side tool mirrors the Android JKM-LX3 geometry, V3 fingerprint distance,
strict ambiguity rejection, and 3-of-5 consensus. It reads sampled frames directly
from the source video and never copies the recording into the repository.
"""

from __future__ import annotations

import argparse
import json
import math
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

import cv2
import numpy as np

try:
    from tools.generate_draft_portrait_seed import fingerprint_v3
except ModuleNotFoundError:
    from generate_draft_portrait_seed import fingerprint_v3


MATCH_THRESHOLD = 0.21
MIN_MATCH_CONFIDENCE = 0.24
MIN_AMBIGUITY_MARGIN = 0.035
SUGGESTION_DISTANCE = 0.50
CONFIDENCE_DISTANCE = 0.35
STRONG_MARGIN = 0.12
NATIVE_WIDTH = 2340
NATIVE_HEIGHT = 1080
SLOT_TOPS_NATIVE = (117, 291, 465, 639, 813)
SLOT_HEIGHT_NATIVE = 129
LEFT_SLOT = (147, 283)
RIGHT_SLOT = (2078, 2212)
PORTRAIT_INSET = (12, 8, 12, 18)
CALIBRATED_SLOT_TOPS = (0.108, 0.269, 0.431, 0.592, 0.753)


@dataclass(frozen=True)
class Fingerprint:
    average_hash: int
    gradient_hash: int
    edges: np.ndarray
    colors: np.ndarray
    version: int = 2
    spatial_luminance: np.ndarray = field(
        default_factory=lambda: np.zeros(64, dtype=np.int32)
    )


def round_half_up(value: float) -> int:
    return int(math.floor(value + 0.5))


def portrait_rect(
    width: int,
    height: int,
    physical_left: bool,
    slot_index: int,
) -> tuple[int, int, int, int]:
    if width <= 0 or height <= 0 or slot_index not in range(1, 6):
        raise ValueError("invalid frame geometry or slot index")
    slot_left, slot_right = LEFT_SLOT if physical_left else RIGHT_SLOT
    top = SLOT_TOPS_NATIVE[slot_index - 1]
    left = slot_left + PORTRAIT_INSET[0]
    right = slot_right - PORTRAIT_INSET[2]
    inner_top = top + PORTRAIT_INSET[1]
    bottom = top + SLOT_HEIGHT_NATIVE - PORTRAIT_INSET[3]
    return (
        max(0, min(width - 1, round_half_up(left / NATIVE_WIDTH * width))),
        max(0, min(height - 1, round_half_up(inner_top / NATIVE_HEIGHT * height))),
        max(1, min(width, round_half_up(right / NATIVE_WIDTH * width))),
        max(1, min(height, round_half_up(bottom / NATIVE_HEIGHT * height))),
    )


def decode_fingerprint(value: str) -> Fingerprint:
    parts = value.split(":")
    if parts[0] not in {"v2", "v3"}:
        raise ValueError("video validator requires V2 or V3 fingerprints")
    expected_parts = 6 if parts[0] == "v3" else 5
    if len(parts) != expected_parts:
        raise ValueError("invalid fingerprint field count")
    edges = np.asarray([int(item) for item in parts[3].split(",")], dtype=np.int32)
    colors = np.asarray([int(item) for item in parts[4].split(",")], dtype=np.int32)
    if edges.size != 32 or colors.size != 12:
        raise ValueError("invalid fingerprint signature dimensions")
    spatial = (
        np.asarray([int(item) for item in parts[5].split(",")], dtype=np.int32)
        if parts[0] == "v3"
        else np.zeros(64, dtype=np.int32)
    )
    if spatial.size != 64:
        raise ValueError("invalid spatial luminance dimensions")
    return Fingerprint(
        average_hash=int(parts[1], 16),
        gradient_hash=int(parts[2], 16),
        edges=edges,
        colors=colors,
        version=3 if parts[0] == "v3" else 2,
        spatial_luminance=spatial,
    )


def spatial_distance(first: np.ndarray, second: np.ndarray) -> float:
    first_centered = first.astype(np.float64) - float(first.mean())
    second_centered = second.astype(np.float64) - float(second.mean())
    denominator = float(
        np.linalg.norm(first_centered) * np.linalg.norm(second_centered)
    )
    if denominator == 0.0:
        return 0.0 if np.array_equal(first, second) else 1.0
    correlation = float(first_centered @ second_centered) / denominator
    return max(0.0, min(1.0, (1.0 - max(-1.0, min(1.0, correlation))) / 2.0))


def fingerprint_distance(first: Fingerprint, second: Fingerprint) -> float:
    average = (first.average_hash ^ second.average_hash).bit_count() / 64.0
    gradient = (first.gradient_hash ^ second.gradient_hash).bit_count() / 64.0
    edges = float(np.abs(first.edges - second.edges).mean()) / 255.0
    colors = float(np.abs(first.colors - second.colors).mean()) / 255.0
    if first.version == 2 or second.version == 2:
        return min(
            1.0,
            max(
                0.0,
                average * 0.35
                + gradient * 0.30
                + edges * 0.20
                + colors * 0.15,
            ),
        )
    spatial = spatial_distance(first.spatial_luminance, second.spatial_luminance)
    return min(
        1.0,
        max(
            0.0,
            average * 0.18
            + gradient * 0.12
            + edges * 0.10
            + colors * 0.10
            + spatial * 0.50,
        ),
    )


def select_match(
    ranked_distances: Iterable[tuple[str, float]],
    visual_confidence: float,
) -> tuple[str | None, list[dict[str, float | str]]]:
    best_by_hero: dict[str, float] = {}
    for hero, distance in ranked_distances:
        if not hero or distance < 0:
            continue
        best_by_hero[hero] = min(distance, best_by_hero.get(hero, float("inf")))
    ranked = sorted(best_by_hero.items(), key=lambda item: (item[1], item[0]))
    candidates = [
        {
            "hero": hero,
            "distance": round(distance, 6),
            "confidence": round(
                max(0.0, min(1.0, 1.0 - distance / SUGGESTION_DISTANCE))
                * max(0.0, min(1.0, visual_confidence)),
                6,
            ),
        }
        for hero, distance in ranked[:3]
    ]
    if not ranked or ranked[0][1] > MATCH_THRESHOLD:
        return None, candidates
    if len(ranked) > 1 and ranked[1][1] - ranked[0][1] < MIN_AMBIGUITY_MARGIN:
        return None, candidates
    distance_evidence = max(
        0.0,
        min(1.0, 1.0 - ranked[0][1] / CONFIDENCE_DISTANCE),
    )
    margin_evidence = (
        1.0
        if len(ranked) == 1
        else max(
            0.0,
            min(1.0, (ranked[1][1] - ranked[0][1]) / STRONG_MARGIN),
        )
    )
    strict_confidence = (
        distance_evidence * 0.70 + margin_evidence * 0.30
    ) * max(0.0, min(1.0, visual_confidence))
    if strict_confidence < MIN_MATCH_CONFIDENCE:
        return None, candidates
    return ranked[0][0], candidates


def consensus(
    observations: Iterable[str | None],
    required_hits: int = 3,
    history_size: int = 5,
) -> dict[str, object]:
    recent = list(observations)[-history_size:]
    counts = Counter(value for value in recent if value)
    if not counts:
        return {"status": "NOT_DETECTED", "hero": None, "hits": 0}
    hero, hits = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[0]
    if hits >= required_hits:
        return {"status": "DETECTED", "hero": hero, "hits": hits}
    return {
        "status": "UNCERTAIN" if len(counts) > 1 else "SCANNING",
        "hero": None,
        "hits": hits,
    }


def normalized_rect(
    width: int,
    height: int,
    left: float,
    top: float,
    right: float,
    bottom: float,
) -> tuple[int, int, int, int]:
    return (
        max(0, min(width - 1, round_half_up(left * width))),
        max(0, min(height - 1, round_half_up(top * height))),
        max(1, min(width, round_half_up(right * width))),
        max(1, min(height, round_half_up(bottom * height))),
    )


def sampled_pixels(frame_bgr: np.ndarray, rect: tuple[int, int, int, int]) -> np.ndarray:
    left, top, right, bottom = rect
    if right <= left or bottom <= top:
        raise ValueError(f"geometry overflow: {rect}")
    return frame_bgr[top:bottom, left:right]


def marker_score(
    frame_bgr: np.ndarray,
    physical_left: bool,
    slot_index: int,
) -> float:
    height, width = frame_bgr.shape[:2]
    top = CALIBRATED_SLOT_TOPS[slot_index - 1]
    rect = normalized_rect(
        width,
        height,
        0.072 if physical_left else 0.902,
        top + 0.085,
        0.110 if physical_left else 0.940,
        min(top + 0.145, 0.995),
    )
    crop = sampled_pixels(frame_bgr, rect)
    blue, green, red = (crop[:, :, index].astype(np.float32) for index in range(3))
    maximum = np.maximum(np.maximum(red, green), blue)
    minimum = np.minimum(np.minimum(red, green), blue)
    saturation = (maximum - minimum) / np.maximum(maximum, 1.0)
    if physical_left:
        mask = (
            (blue >= 85)
            & (blue > red * 1.12)
            & (blue > green * 1.02)
            & (saturation >= 0.20)
        )
    else:
        mask = (
            (red >= 85)
            & (red > green * 1.10)
            & (red > blue * 1.02)
            & (saturation >= 0.20)
        )
    return float(mask.mean()) if mask.size else 0.0


def preview_score(
    frame_bgr: np.ndarray,
    physical_left: bool,
    slot_index: int,
) -> float:
    height, width = frame_bgr.shape[:2]
    top = CALIBRATED_SLOT_TOPS[slot_index - 1]
    rect = normalized_rect(
        width,
        height,
        0.043 if physical_left else 0.855,
        max(0.0, top - 0.018),
        0.151 if physical_left else 0.958,
        min(0.995, top + 0.150),
    )
    crop = sampled_pixels(frame_bgr, rect)
    blue, green, red = (crop[:, :, index].astype(np.float32) for index in range(3))
    gold = (
        (red >= 120)
        & (green >= 85)
        & (red > blue * 1.35)
        & (green > blue * 1.15)
        & (np.abs(red - green) < 110)
    )
    return float(gold.mean()) if gold.size else 0.0


def slot_occupied(
    frame_bgr: np.ndarray,
    physical_left: bool,
    slot_index: int,
) -> bool:
    marker_threshold = 0.075 if physical_left else 0.070
    return marker_score(frame_bgr, physical_left, slot_index) >= marker_threshold or (
        preview_score(frame_bgr, physical_left, slot_index) >= 0.120
    )


def visual_confidence(rgb: np.ndarray) -> float:
    pixels = rgb.astype(np.float64) / 255.0
    maximum = pixels.max(axis=2)
    minimum = pixels.min(axis=2)
    saturation = np.where(maximum == 0.0, 0.0, (maximum - minimum) / maximum)
    luminance = (
        pixels[:, :, 0] * 0.2126
        + pixels[:, :, 1] * 0.7152
        + pixels[:, :, 2] * 0.0722
    )
    return float(
        np.clip(
            0.40 + saturation.mean() * 0.45 + abs(luminance.mean() - 0.5) * 0.10,
            0.0,
            1.0,
        )
    )


def frame_is_draft(frame_bgr: np.ndarray) -> bool:
    height, width = frame_bgr.shape[:2]
    rect = normalized_rect(width, height, 0.39, 0.005, 0.61, 0.075)
    crop = sampled_pixels(frame_bgr, rect)
    blue, green, red = (crop[:, :, index].astype(np.float32) for index in range(3))
    blue_ratio = ((blue > 85) & (blue > red * 1.12) & (blue > green * 1.02)).mean()
    return bool(blue_ratio > 0.25)


def load_seed(path: Path) -> dict[str, list[Fingerprint]]:
    root = json.loads(path.read_text(encoding="utf-8"))
    if root.get("schema_version") not in {2, 3}:
        raise ValueError("expected portrait seed schema 2 or 3")
    return {
        hero: [decode_fingerprint(value) for value in values]
        for hero, values in root["templates"].items()
    }


def rank_frame(
    frame_bgr: np.ndarray,
    templates: dict[str, list[Fingerprint]],
    physical_left: bool,
    slot_index: int,
) -> tuple[str | None, list[dict[str, float | str]]]:
    rect = portrait_rect(
        frame_bgr.shape[1],
        frame_bgr.shape[0],
        physical_left,
        slot_index,
    )
    crop = sampled_pixels(frame_bgr, rect)
    normalized = cv2.resize(crop, (12, 12), interpolation=cv2.INTER_LINEAR)
    rgb = cv2.cvtColor(normalized, cv2.COLOR_BGR2RGB)
    query = decode_fingerprint(fingerprint_v3(rgb))
    ranked = [
        (hero, min(fingerprint_distance(query, template) for template in hero_templates))
        for hero, hero_templates in templates.items()
    ]
    return select_match(ranked, visual_confidence(rgb))


def validate_video(
    video_path: Path,
    seed_path: Path,
    start_seconds: float,
    end_seconds: float | None,
    step_seconds: float,
    enemy_on_right: bool,
) -> dict[str, object]:
    templates = load_seed(seed_path)
    capture = cv2.VideoCapture(str(video_path))
    if not capture.isOpened():
        raise ValueError(f"could not open video: {video_path}")
    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
    video_width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
    video_height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
    duration = frame_count / fps if frame_count > 0 else 0.0
    stop = min(duration, end_seconds if end_seconds is not None else duration)
    if step_seconds <= 0 or start_seconds < 0 or stop <= start_seconds:
        raise ValueError("invalid sampling interval")

    accepted_by_slot: dict[str, list[str | None]] = defaultdict(list)
    candidates_by_slot: dict[str, list[list[dict[str, float | str]]]] = defaultdict(list)
    occupied_counts: Counter[str] = Counter()
    sampled_frames = 0
    draft_frames = 0
    timestamp = start_seconds
    while timestamp <= stop + 1e-6:
        capture.set(cv2.CAP_PROP_POS_MSEC, timestamp * 1000.0)
        ok, frame = capture.read()
        sampled_frames += 1
        timestamp += step_seconds
        if not ok or frame is None or not frame_is_draft(frame):
            continue
        draft_frames += 1
        for physical_left in (True, False):
            side = (
                "ALLY"
                if (physical_left and enemy_on_right) or (not physical_left and not enemy_on_right)
                else "ENEMY"
            )
            for slot_index in range(1, 6):
                key = f"{side}:{slot_index}"
                if not slot_occupied(frame, physical_left, slot_index):
                    continue
                occupied_counts[key] += 1
                accepted, candidates = rank_frame(
                    frame,
                    templates,
                    physical_left,
                    slot_index,
                )
                accepted_by_slot[key].append(accepted)
                candidates_by_slot[key].append(candidates)
    capture.release()

    slot_reports: dict[str, dict[str, object]] = {}
    for side in ("ALLY", "ENEMY"):
        for slot_index in range(1, 6):
            key = f"{side}:{slot_index}"
            observations = accepted_by_slot.get(key, [])
            candidate_frames = candidates_by_slot.get(key, [])
            flattened = [
                (str(item["hero"]), float(item["distance"]), float(item["confidence"]))
                for frame_candidates in candidate_frames
                for item in frame_candidates
            ]
            candidate_summary = []
            for hero, group in _group_candidates(flattened).items():
                candidate_summary.append(
                    {
                        "hero": hero,
                        "best_distance": round(min(item[0] for item in group), 6),
                        "mean_confidence": round(
                            sum(item[1] for item in group) / len(group),
                            6,
                        ),
                        "frames": len(group),
                    }
                )
            candidate_summary.sort(
                key=lambda item: (
                    -int(item["frames"]),
                    float(item["best_distance"]),
                    str(item["hero"]),
                )
            )
            slot_reports[key] = {
                "occupied_frames": occupied_counts[key],
                "accepted_frames": sum(value is not None for value in observations),
                "consensus": consensus(observations),
                "top_candidates": candidate_summary[:3],
            }

    return {
        "video": str(video_path),
        "seed": str(seed_path),
        "video_resolution": {
            "width": video_width,
            "height": video_height,
        },
        "duration_seconds": round(duration, 3),
        "sampling": {
            "start": start_seconds,
            "end": stop,
            "step": step_seconds,
            "sampled_frames": sampled_frames,
            "draft_frames": draft_frames,
        },
        "geometry_authority": f"JKM-LX3 {NATIVE_WIDTH}x{NATIVE_HEIGHT}",
        "slots": slot_reports,
    }


def _group_candidates(
    values: list[tuple[str, float, float]]
) -> dict[str, list[tuple[float, float]]]:
    groups: dict[str, list[tuple[float, float]]] = defaultdict(list)
    for hero, distance, confidence in values:
        groups[hero].append((distance, confidence))
    return groups


def normalized_hero_name(value: object) -> str:
    without_marks = "".join(
        character
        for character in unicodedata.normalize("NFD", str(value).strip().casefold())
        if unicodedata.category(character) != "Mn"
    )
    return " ".join(without_marks.split())


def compare_expected_slots(
    report: dict[str, object],
    expected: dict[str, str],
) -> list[str]:
    slots = report.get("slots", {})
    if not isinstance(slots, dict):
        return ["report has no slot results"]
    mismatches: list[str] = []
    for slot, expected_hero in sorted(expected.items()):
        actual_slot = slots.get(slot)
        if not isinstance(actual_slot, dict):
            mismatches.append(f"{slot}: missing result, expected {expected_hero}")
            continue
        consensus_result = actual_slot.get("consensus", {})
        actual_hero = (
            consensus_result.get("hero")
            if isinstance(consensus_result, dict)
            else None
        )
        if normalized_hero_name(actual_hero) != normalized_hero_name(expected_hero):
            mismatches.append(
                f"{slot}: detected {actual_hero or 'NONE'}, expected {expected_hero}"
            )
    return mismatches


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--video", type=Path, required=True)
    parser.add_argument("--seed", type=Path, required=True)
    parser.add_argument("--start", type=float, default=45.0)
    parser.add_argument("--end", type=float, default=160.0)
    parser.add_argument("--step", type=float, default=1.0)
    parser.add_argument("--enemy-on-right", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--expected", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    report = validate_video(
        video_path=args.video,
        seed_path=args.seed,
        start_seconds=args.start,
        end_seconds=args.end,
        step_seconds=args.step,
        enemy_on_right=args.enemy_on_right,
    )
    mismatches: list[str] = []
    if args.expected:
        expected_root = json.loads(args.expected.read_text(encoding="utf-8"))
        expected_slots = expected_root.get("slots", {})
        if not isinstance(expected_slots, dict):
            raise ValueError("expected fixture must contain a slots object")
        mismatches = compare_expected_slots(report, expected_slots)
        report["verification"] = {
            "expected_fixture": str(args.expected),
            "passed": not mismatches,
            "mismatches": mismatches,
        }
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    if mismatches:
        raise SystemExit("RANKED_VIDEO_VALIDATION_FAILED: " + "; ".join(mismatches))


if __name__ == "__main__":
    main()
