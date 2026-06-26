#!/usr/bin/env python3
"""Smoke test for the VoxExport MusicXML bridge."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "Scripts" / "voxexport_musicxml.py"


def main() -> int:
    payload = {
        "format": "voxbox-export",
        "version": 1,
        "type": "VoxMulti",
        "label": "smoke",
        "tpqn": 960,
        "metreMap": {
            "regions": [
                {
                    "start": 0,
                    "beats": [1, 1, 1, 1],
                    "divisions": [4, 4, 4, 4],
                    "timeSignature": "4/4",
                }
            ]
        },
        "parts": [
            {
                "label": "lead",
                "events": [
                    {"start": 0, "dur": 960, "midinote": 60, "velocity": 90, "channel": 0},
                    {"start": 0, "dur": 960, "midinote": 67, "velocity": 90, "channel": 0},
                    {"start": 960, "dur": 960, "midinote": 64, "velocity": 90, "channel": 0},
                ],
            },
            {
                "label": "bass",
                "events": [
                    {"start": 0, "dur": 1920, "midinote": 48, "velocity": 80, "channel": 1}
                ],
            },
        ],
    }

    with tempfile.TemporaryDirectory() as tmp:
        json_path = Path(tmp) / "score.voxexport.json"
        xml_path = Path(tmp) / "score.musicxml"
        json_path.write_text(json.dumps(payload))

        subprocess.run([sys.executable, str(BRIDGE), str(json_path), str(xml_path)], check=True)
        text = xml_path.read_text()

    assert "lead" in text
    assert "bass" in text
    assert "<step>C</step>" in text
    assert "<octave>4</octave>" in text
    assert "<chord />" in text
    assert "<beats>4</beats>" in text
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
