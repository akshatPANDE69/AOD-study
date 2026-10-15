#!/usr/bin/env python3
"""Analyze native AOD trial CSV exports without modifying the raw files.

Only complete, quality-passing matched pairs are summarized. The outputs are
whole-device battery-power estimates, never display-panel-only power.
"""

from __future__ import annotations

import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path


OFF = "Screen off, native AOD off"
ON = "Screen off, native AOD on"
REQUIRED_CONDITIONS = {OFF, ON}
MAX_ALARM_LATENESS_MS = 120_000
MAX_DURATION_DEVIATION_MS = 120_000
MAX_START_SOC_DIFFERENCE_PCT = 5

# Two-sided 95% Student-t critical values for df=1..30. For larger samples,
# using df=30 is a slightly conservative approximation to the limiting value.
T95 = [
    0.0, 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262,
    2.228, 2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093,
    2.086, 2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045,
    2.042,
]


def number(row: dict[str, str], key: str) -> float | None:
    try:
        value = float(row.get(key, ""))
    except (TypeError, ValueError):
        return None
    return value if math.isfinite(value) else None


def integer(row: dict[str, str], key: str) -> int | None:
    try:
        value = int(row.get(key, ""))
    except (TypeError, ValueError):
        return None
    if value in {-2**31, -2**63}:
        return None
    return value


def derive_power_mw(row: dict[str, str]) -> float | None:
    """Recompute average whole-device power from the raw endpoint counters."""
    source = row.get("energy_source", "")
    if source == "BATTERY_PROPERTY_ENERGY_COUNTER":
        start = integer(row, "energy_counter_start_nwh")
        end = integer(row, "energy_counter_end_nwh")
        if start is None or end is None:
            return None
        energy_mwh = (start - end) / 1_000_000.0
    elif source == "CHARGE_COUNTER x mean battery voltage":
        start = integer(row, "charge_counter_start_uah")
        end = integer(row, "charge_counter_end_uah")
        voltage_start = number(row, "battery_voltage_start_mv")
        voltage_end = number(row, "battery_voltage_end_mv")
        if start is None or end is None or voltage_start is None or voltage_end is None:
            return None
        if voltage_start <= 0 or voltage_end <= 0:
            return None
        energy_mwh = (start - end) * ((voltage_start + voltage_end) / 2.0) / 1_000_000.0
    else:
        return None

    elapsed_ms = number(row, "elapsed_ms")
    reported_energy = number(row, "battery_energy_delta_mwh")
    reported_power = number(row, "average_whole_device_battery_power_mw")
    if elapsed_ms is None or elapsed_ms <= 0 or energy_mwh <= 0:
        return None
    power_mw = energy_mwh / (elapsed_ms / 3_600_000.0)
    if reported_energy is None or abs(reported_energy - energy_mwh) > 0.0002:
        return None
    if reported_power is None or abs(reported_power - power_mw) > 0.0002:
        return None
    return power_mw


def initial_row_issue(row: dict[str, str]) -> str:
    if not row.get("pair_id", "").strip():
        return "missing pair_id"
    if row.get("condition", "") not in REQUIRED_CONDITIONS:
        return "not a native AOD-on/off condition"
    if row.get("battery_plug_type_start") != "0" or row.get("battery_plug_type_end") != "0":
        return "plugged or plug state unknown"
    if row.get("energy_source") not in {
        "BATTERY_PROPERTY_ENERGY_COUNTER",
        "CHARGE_COUNTER x mean battery voltage",
    }:
        return "unsupported or missing energy source"
    elapsed = number(row, "elapsed_ms")
    target_minutes = number(row, "target_duration_min")
    if elapsed is None or target_minutes is None:
        return "missing elapsed or target duration"
    if abs(elapsed - target_minutes * 60_000) > MAX_DURATION_DEVIATION_MS:
        return "actual duration outside ±2-minute tolerance"
    if derive_power_mw(row) is None:
        return "raw battery counters missing, non-discharging, or inconsistent with app-derived result"
    lateness = number(row, "end_alarm_lateness_ms")
    if lateness is None or abs(lateness) > MAX_ALARM_LATENESS_MS:
        return "endpoint alarm outside ±2-minute tolerance"
    for key in ("battery_level_start_pct", "battery_level_end_pct"):
        level = number(row, key)
        if level is None or not 0 <= level <= 100:
            return f"missing or invalid {key}"
    return ""


def device_key(row: dict[str, str]) -> str:
    return row.get("device_code", "").strip() or row.get("model", "").strip() or row.get("device_label", "").strip() or "unknown-device"


def analyze(input_path: Path, summary_path: Path, audit_path: Path) -> None:
    with input_path.open("r", newline="", encoding="utf-8-sig") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise SystemExit("Input CSV has no trial rows.")
    if not {"pair_id", "condition", "average_whole_device_battery_power_mw"}.issubset(rows[0]):
        raise SystemExit("CSV is missing required study logger columns. Use an export with pair_id support.")

    grouped: dict[tuple[str, str], list[tuple[int, dict[str, str], str]]] = defaultdict(list)
    audit: list[dict[str, str]] = []
    for index, row in enumerate(rows, start=2):
        device = device_key(row)
        pair = row.get("pair_id", "").strip()
        issue = initial_row_issue(row)
        if issue:
            audit.append({"csv_line": str(index), "device": device, "pair_id": pair, "status": "excluded", "reason": issue})
            continue
        grouped[(device, pair)].append((index, row, ""))

    paired: dict[str, list[dict[str, float | str]]] = defaultdict(list)
    for (device, pair), members in grouped.items():
        conditions = [row.get("condition", "") for _, row, _ in members]
        if len(members) != 2 or set(conditions) != REQUIRED_CONDITIONS:
            reason = "pair must contain exactly one AOD-off and one AOD-on row"
            for line, _, _ in members:
                audit.append({"csv_line": str(line), "device": device, "pair_id": pair, "status": "excluded", "reason": reason})
            continue

        off = next(row for _, row, _ in members if row["condition"] == OFF)
        on = next(row for _, row, _ in members if row["condition"] == ON)
        off_soc = number(off, "battery_level_start_pct")
        on_soc = number(on, "battery_level_start_pct")
        if off_soc is None or on_soc is None or abs(off_soc - on_soc) > MAX_START_SOC_DIFFERENCE_PCT:
            reason = "matched runs start more than 5 battery-percentage points apart"
        elif off.get("energy_source") != on.get("energy_source"):
            reason = "energy-counter source differs within pair"
        else:
            reason = ""

        if reason:
            for line, _, _ in members:
                audit.append({"csv_line": str(line), "device": device, "pair_id": pair, "status": "excluded", "reason": reason})
            continue

        off_power = derive_power_mw(off)
        on_power = derive_power_mw(on)
        assert off_power is not None and on_power is not None
        paired[device].append({
            "pair_id": pair,
            "off_mw": off_power,
            "on_mw": on_power,
            "delta_mw": on_power - off_power,
        })
        for line, _, _ in members:
            audit.append({"csv_line": str(line), "device": device, "pair_id": pair, "status": "included", "reason": "complete quality-passing pair"})

    summaries: list[dict[str, str]] = []
    for device, pairs in sorted(paired.items()):
        deltas = [float(item["delta_mw"]) for item in pairs]
        n = len(deltas)
        mean_delta = statistics.mean(deltas)
        sd_delta = statistics.stdev(deltas) if n > 1 else math.nan
        if n > 1:
            critical = T95[min(n - 1, 30)]
            half_width = critical * sd_delta / math.sqrt(n)
            ci_low, ci_high = mean_delta - half_width, mean_delta + half_width
        else:
            ci_low = ci_high = math.nan
        summaries.append({
            "device": device,
            "n_valid_pairs": str(n),
            "mean_aod_off_whole_device_mw": f"{statistics.mean(float(x['off_mw']) for x in pairs):.4f}",
            "mean_aod_on_whole_device_mw": f"{statistics.mean(float(x['on_mw']) for x in pairs):.4f}",
            "mean_paired_delta_aod_minus_off_mw": f"{mean_delta:.4f}",
            "sd_paired_delta_mw": "" if math.isnan(sd_delta) else f"{sd_delta:.4f}",
            "ci95_low_mw": "" if math.isnan(ci_low) else f"{ci_low:.4f}",
            "ci95_high_mw": "" if math.isnan(ci_high) else f"{ci_high:.4f}",
            "ci_note": "Student-t paired interval; df=30 critical value used conservatively for n>31" if n > 31 else "Student-t paired interval",
        })

    summary_path.parent.mkdir(parents=True, exist_ok=True)
    audit_path.parent.mkdir(parents=True, exist_ok=True)
    summary_fields = [
        "device", "n_valid_pairs", "mean_aod_off_whole_device_mw",
        "mean_aod_on_whole_device_mw", "mean_paired_delta_aod_minus_off_mw",
        "sd_paired_delta_mw", "ci95_low_mw", "ci95_high_mw", "ci_note",
    ]
    with summary_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=summary_fields)
        writer.writeheader()
        writer.writerows(summaries)
    with audit_path.open("w", newline="", encoding="utf-8") as target:
        fields = ["csv_line", "device", "pair_id", "status", "reason"]
        writer = csv.DictWriter(target, fieldnames=fields)
        writer.writeheader()
        writer.writerows(sorted(audit, key=lambda item: int(item["csv_line"])))

    print(f"Read {len(rows)} raw trial rows; analyzed {sum(len(v) for v in paired.values())} matched pairs across {len(paired)} devices.")
    print(f"Summary: {summary_path}")
    print(f"Row/pair audit: {audit_path}")
    print("All power values are whole-device battery estimates, not panel-only measurements.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_csv", type=Path, help="Untouched CSV exported by the study logger")
    parser.add_argument("--summary", type=Path, help="Summary output CSV (default: beside input)")
    parser.add_argument("--audit", type=Path, help="Audit output CSV (default: beside input)")
    args = parser.parse_args()
    stem = args.input_csv.stem
    analyze(
        args.input_csv,
        args.summary or args.input_csv.with_name(f"{stem}_summary.csv"),
        args.audit or args.input_csv.with_name(f"{stem}_audit.csv"),
    )


if __name__ == "__main__":
    main()
