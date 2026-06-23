# VoxBox Starter Sketches

Recompile the SuperCollider class library, then evaluate these sketches one block
at a time in order:

1. `01_live_boxes.scd`: live chains, live clipping, snapshot gathering and Box edits.
2. `02_canon_routing.scd`: multi-voice canon routing and live labelled selection.
3. `03_seeded_processing.scd`: replayable random transformations and deliberate rerolls.
4. `04_rolling_playback.scd`: rolling playback through a recording MIDI sink.
5. `05_bank_arrangement.scd`: named snapshots and metre-aware timeline placement.
6. `06_provenance_bank_save.scd`: MIDI-path provenance and frozen bank JSON persistence.
7. `07_real_midi_exploration.scd`: a larger exploratory MIDI workflow.
8. `08_live_coding_layer.scd`: handles, persistent taps, forks, masks and fragments.

The first five examples and `08_live_coding_layer.scd` use small in-memory event
arrays, so no MIDI file or external device is required. The sixth is a template
with explicit source and archive paths. `Tests/hc.scd` remains the MIDI-import
sketch for adapting to your own source file and MIDI output.
