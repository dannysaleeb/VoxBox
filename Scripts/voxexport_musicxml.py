#!/usr/bin/env python3
"""Convert VoxExport JSON into basic MusicXML."""

from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from music21 import chord, duration, meter, note, stream
except Exception as exc:  # pragma: no cover - exercised from SC smoke when missing
    print(f"VoxExport: music21 is required ({exc})", file=sys.stderr)
    sys.exit(2)


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def _ql(ticks: int, tpqn: int) -> float:
    return float(int(ticks)) / float(tpqn)


def _part_id(label: str, index: int) -> str:
    cleaned = "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in label)
    return cleaned or f"part_{index + 1}"


def _insert_time_signatures(part: stream.Part, regions: list[dict], tpqn: int) -> None:
    if not regions:
        part.insert(0, meter.TimeSignature("4/4"))
        return

    for region in regions:
        ts_string = region.get("timeSignature", "4/4")
        start = int(region.get("start", 0))
        part.insert(_ql(start, tpqn), meter.TimeSignature(ts_string))


def _make_note(event: dict, tpqn: int) -> note.Note:
    n = note.Note()
    n.pitch.midi = int(event["midinote"])
    n.duration = duration.Duration(_ql(int(event["dur"]), tpqn))
    if "velocity" in event:
        n.volume.velocity = int(event["velocity"])
    return n


def _make_chord(events: list[dict], tpqn: int) -> chord.Chord:
    c = chord.Chord([int(event["midinote"]) for event in events])
    c.duration = duration.Duration(_ql(int(events[0]["dur"]), tpqn))
    velocities = [event.get("velocity") for event in events if "velocity" in event]
    if velocities:
        c.volume.velocity = int(round(sum(int(v) for v in velocities) / len(velocities)))
    return c


def _append_events(part: stream.Part, events: list[dict], tpqn: int, label: str) -> None:
    events = sorted(events, key=lambda event: (int(event["start"]), int(event["midinote"])))
    cursor = 0
    index = 0

    while index < len(events):
        start = int(events[index]["start"])
        same_start = []
        while index < len(events) and int(events[index]["start"]) == start:
            same_start.append(events[index])
            index += 1

        if start > cursor:
            part.append(note.Rest(quarterLength=_ql(start - cursor, tpqn)))
            cursor = start
        elif start < cursor:
            print(
                f"VoxExport: overlap in part {label} at tick {start}; writing sequentially.",
                file=sys.stderr,
            )

        durations = {int(event["dur"]) for event in same_start}
        if len(same_start) > 1 and len(durations) == 1:
            part.append(_make_chord(same_start, tpqn))
            dur = int(same_start[0]["dur"])
            cursor = start + dur if start >= cursor else cursor + dur
            continue

        for event in same_start:
            event_start = int(event["start"])
            if event_start < cursor:
                print(
                    f"VoxExport: overlap in part {label} at tick {event_start}; writing sequentially.",
                    file=sys.stderr,
                )
            elif event_start > cursor:
                part.append(note.Rest(quarterLength=_ql(event_start - cursor, tpqn)))
                cursor = event_start

            part.append(_make_note(event, tpqn))
            cursor = cursor + int(event["dur"])


def convert(json_path: Path, xml_path: Path) -> None:
    data = json.loads(json_path.read_text())

    _require(data.get("format") == "voxbox-export", "unsupported VoxExport format")
    _require(data.get("version") == 1, "unsupported VoxExport version")
    tpqn = int(data["tpqn"])
    _require(tpqn > 0, "tpqn must be positive")

    regions = data.get("metreMap", {}).get("regions", [])
    parts = data.get("parts", [])
    _require(isinstance(parts, list), "parts must be a list")

    score = stream.Score(id=str(data.get("label", "voxbox_export")))

    if not parts:
        parts = [{"label": "empty", "events": []}]

    for index, part_data in enumerate(parts):
        label = str(part_data.get("label", f"part_{index + 1}"))
        p = stream.Part(id=_part_id(label, index))
        p.partName = label
        _insert_time_signatures(p, regions, tpqn)
        _append_events(p, part_data.get("events", []), tpqn, label)
        score.append(p)

    score.write("musicxml", fp=str(xml_path))


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: voxexport_musicxml.py input.voxexport.json output.musicxml", file=sys.stderr)
        return 2

    try:
        convert(Path(argv[1]), Path(argv[2]))
    except Exception as exc:
        print(f"VoxExport: {exc}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
