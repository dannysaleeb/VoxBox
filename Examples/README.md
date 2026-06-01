# VoxBox Starter Sketches

Recompile the SuperCollider class library, then evaluate these sketches one block
at a time in order:

1. `01_live_boxes.scd`: live chains, live clipping, snapshot gathering and Box edits.
2. `02_canon_routing.scd`: multi-voice canon routing and live labelled selection.
3. `03_seeded_processing.scd`: replayable random transformations and deliberate rerolls.
4. `04_rolling_playback.scd`: rolling playback through a recording MIDI sink.
5. `05_bank_arrangement.scd`: named snapshots and metre-aware timeline placement.

The examples use small in-memory event arrays, so no MIDI file or external device
is required. `Tests/hc.scd` remains the MIDI-import sketch for adapting to your
own source file and MIDI output.
