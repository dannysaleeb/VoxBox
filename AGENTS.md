# VoxBox repository guidance

VoxBox is a SuperCollider toolkit for metre-aware, patchable musical score
objects. Read `README.md` before changing public behavior; it is the current
architecture and roadmap document.

## Design contracts

- Treat `Box` and `BoxMulti` as mutable editing containers.
- Treat `Vox` and `VoxMulti` as defensive output snapshots.
- Preserve the distinction between live processing (`VoxNode` chains, routers,
  forks, arrangements) and explicit snapshot boundaries (gathering, clipping,
  bank deposits, and arrangement placement).
- Voice labels are identities used by routing and multi-voice operations.
- Keep `Vox.source` runtime-only. Durable lineage belongs in copied descriptive
  recipes under `metadata[\provenance]`; repeated `.out` calls must not grow or
  mutate provenance.
- Avoid expanding the operator DSL without documenting and testing its contract.
- Keep VoxExport v1 limitations explicit; do not imply it provides full
  engraving, quantization, tie, beam, clef, or overlapping-voice support.

## Repository layout

- `Classes/`: SuperCollider implementation; processing modules are under
  `Classes/Modules/`.
- `Extensions/Utils/`: extension methods and shared utilities.
- `HelpSource/Classes/`: SuperCollider help pages for public classes.
- `Tests/`: executable `.scd` smoke checks and musical integration sketches.
- `Scripts/voxexport_musicxml.py`: the MusicXML bridge used by VoxExport.

## Verification

After changing `.sc` class files, recompile the SuperCollider class library
before testing. Run the smoke script closest to the changed behavior from
`Tests/`; the principal regression scripts are:

- `Tests/mvp_smoke.scd`
- `Tests/playback_smoke.scd`
- `Tests/bank_arrangement_smoke.scd`
- `Tests/provenance_archive_smoke.scd`
- `Tests/livecoding_smoke.scd`
- `Tests/granulator_harmony_smoke.scd`
- `Tests/vox_export_smoke.scd`

The Python side of MusicXML export can be checked with:

```sh
python3 Tests/voxexport_musicxml_smoke.py
```

Some tests require SuperCollider quarks documented in `README.md`: `wslib` for
`SimpleMIDIFile`, `JSONlib` for bank persistence, and `RTProbMap` plus
`MathLib` for Granulator-related checks. State clearly when a relevant test was
not run or an optional dependency was unavailable.

When public behavior changes, update the corresponding help page and README
contract along with the implementation and smoke coverage.
