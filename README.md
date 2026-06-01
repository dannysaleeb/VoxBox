# VoxBox

VoxBox is a SuperCollider toolkit for working with MIDI-derived musical
material as metre-aware, patchable score objects. It is meant to sit somewhere
between a live-coding library, a compositional sketchbook, and a future notation
pipeline: import MIDI, select and transform clips, route voices through modular
processes, gather the result into multi-voice structures, and play or export the
result.

The project is currently an expressive prototype moving toward a stable minimum
viable workflow. The core live-patching contract is now explicit: play chains or
routers directly when you want live parameter updates, and gather into `Box` or
`BoxMulti` when you want to deposit a snapshot for further editing.

This README is written primarily as a working map for the project: what it is
trying to be, how the current pieces fit together, what is fragile, and what
should probably be done next.

## Intent

The central idea is to treat musical material as labelled voices that can move
through a chain of transformations. A `Box` is the mutable place where material
lives and gets edited. A `Vox` is the output snapshot of that material as it
passes through modules. A chain of `VoxNode` objects acts like a score-level
patching graph: not audio-rate signal flow, but musical phrase flow.

In practice, the intended workflow looks like this:

1. Import a MIDI file into one or more `Box` objects.
2. Select or clip a time range using score positions such as `Pos(1)` or raw
   ticks.
3. Send the resulting `Vox` through modules such as transposers, canonisers,
   elongators, granulators, or mode mappers.
4. Route multi-voice output by label.
5. Play a chain or router directly when parameter changes should be heard at the
   next unscheduled onset.
6. Gather the result into a `Box` or `BoxMulti` when you want an editable
   snapshot.
7. Eventually export stable output toward notation, for example MusicXML.

The musical centre of gravity is generative and contrapuntal. The existing
`Tests/hc.scd` sketch shows the current direction clearly: take a source line,
canonise it into multiple voices, transpose selected routes, gather them, and
listen.

## Core Model

### `Box`

`Box` is the mutable working container for a single voice or clip. It stores
events, metre information, a current highlighted `TimeRange`, history, metadata,
and a source label. It can be created directly from events or from a
`SimpleMIDIFile` via `Box.fromMIDI`.

Important responsibilities currently include:

- MIDI note/sustain event conversion.
- Sorting events by absolute time.
- Calculating `Pos` values from ticks.
- Tracking a highlighted `TimeRange`.
- Clipping material to the current range.
- Loading transformed output back into the box.
- Keeping a `VoxHistory` of committed states.
- Producing a `Vox` through `.out`.

The design intent is that `Box` is editable local material. `.out` returns a
fresh clipped `Vox` snapshot from the box's own state; it does not secretly pass
through an upstream chain.

### `Vox`

`Vox` is the single-voice value object passed between modules. It contains an
event list, a `MetreMap`, a label, metadata, and an optional source reference.
It deep-copies most data on construction and copy, which suggests it is intended
to behave more like a stable output value than a mutable editing surface.

In the current design, most modules should accept a `Vox` and return a new
`Vox`.

### `BoxMulti` and `VoxMulti`

`BoxMulti` and `VoxMulti` are labelled collections of boxes or voxes. They are
used for multi-track MIDI, canon output, routed voices, and gathered
arrangements.

`BoxMulti` is the mutable multi-voice container. `VoxMulti` is the output
snapshot. `BoxMulti.out` returns a `VoxMulti` from its local boxes. Voice labels
are currently the most important identity mechanism, and most routing examples
assume labels such as `\bass`, `\tenor`, and `\treble`.

### `VoxNode`

`VoxNode` is the base class for patchable objects. It defines the operator DSL
that gives the project its live-coding feel:

- `>>>` connects live processing nodes. When the downstream target is a `Box` or
  `BoxMulti`, it gathers a snapshot into that container instead.
- `<<<` connects a source into the head of an existing chain.
- `>>=` assigns a node to an environment symbol or loads a snapshot into a
  `Box` / `BoxMulti`.
- `>>==>` force-loads into a target.
- `>>@` routes labelled voices through a `VoxRouter`.
- `>>*` creates a live labelled-voice selector from a `VoxMulti`.
- `>>/` creates a live clipped view of the current output.

This layer is powerful, but it is also one of the places where the design is
still being discovered. The operators need a clearer written contract before
the library can feel predictable.

### `VoxModule`

`VoxModule` is the base class for transformations. A module receives input from
its upstream node, resolves it through `.out`, and then chooses one of three
paths:

- process one `Vox` into one `Vox`;
- process each voice in a `VoxMulti`;
- turn one `Vox` into a `VoxMulti`, as `VoxCanoniser` does.

Existing modules include:

- `VoxCanoniser`: creates delayed entries from one source voice.
- `CTransposer`: chromatic transposition.
- `DTransposer`: diatonic transposition by scale degree.
- `ModeMap`: maps material from one scale/root to another.
- `Elongator` and `RandElong`: stretch time deterministically or randomly.
- `Granulator`: subdivides notes rhythmically.
- `HarmonyMask`: assigns scale-based pitches to granulated continuation events
  while preserving original onset grains.

Some module files are placeholders or early sketches rather than finished tools.

### Timing Layer

The timing model is built around:

- `Pos`: score position as bar, beat, division, tick.
- `Metre`: beat and division structure for a bar.
- `MetreRegion`: a metre starting at a tick.
- `MetreMap`: a sequence of metre regions over time.
- `TimeConverter`: conversion between ticks and score positions.
- `TimeRange`: a normalized tick range used internally by boxes, multis,
  clipping, highlighting, and `>>/`.

This is one of the most distinctive parts of the project. It is trying to handle
musical time as something richer than fixed quarter-note grids, including
changing metres and non-uniform divisions.

## Example Workflow

This compact example is adapted from `Tests/hc.scd`. It shows the intended
style rather than a guaranteed stable public API.

```supercollider
(
MIDIClient.init;
m = MIDIOut.new(0);
m.latency = 0;

f = SimpleMIDIFile.read("/path/to/source.mid");
)

(
// Load the source material into a mutable box.
Box.fromMIDI(f, \dux) >>= \dux;

// Create modules.
~canoniser = VoxCanoniser(
    3,
    [\bass, \tenor, \treble],
    [Pos(0), Pos(division: 2), Pos(beat: 1)],
    \canon
);

~transposeTenor = CTransposer(2);
~transposeTreble = CTransposer(12);
)

(
// Canonise the source and route selected voices.
~canon_chain = ~dux >>> ~canoniser
>>@ [
    \tenor <<< ~transposeTenor,
    \treble <<< ~transposeTreble,
    \bass
];
)

(
// Play the live chain. Parameter changes apply at the next unscheduled onset.
t = TempoClock.new(1, queueSize: 100000);
z = VoxPlayer.new(~canon_chain, t);
z.loopMIDI(m, 4);
)

// Gather a snapshot when you want editable deposited material.
~canon_chain >>> BoxMulti.new >>= \canon_one;

z.stop;
```

The important idea is that source material remains editable and modules remain
parameterised. Live playback attaches to the chain or router itself. Selection,
routing and clipping remain live:

```supercollider
~liveExcerpt = ~canon_chain >>* \tenor >>/ [Pos(1), Pos(2)];
```

Gathering into a box or multi deposits the current result as an editable
snapshot, so it can start a new editing or routing stage without retaining a
hidden upstream wire:

```supercollider
~liveExcerpt >>> Box.new >>= \editableExcerpt;
```

`VoxPlayer` uses a rolling lookahead scheduler. A Box edit or module-parameter
change is rendered before the next unscheduled onset. Notes already sounding
finish naturally. Random modules cache their rendered output between revisions;
use seeds for replayable output and `.reroll` when a deliberate redraw is wanted.

The rolling scheduler keeps only a short horizon of onsets queued rather than
forking one routine for every event in a loop. Peak queued work is therefore
proportional to the nearby events and active notes, not the full clip. Polling
adds a small fixed cost, while revision-aware caching avoids rerendering an
unchanged processing graph.

## Current Status

VoxBox is useful as a personal composition experiment. It already contains
enough machinery to import MIDI, make canon-like structures, route voices,
transpose material, clip ranges with `TimeRange`, and play either live chains or
snapshotted containers.

For small hardware-free sketches that can be evaluated block by block after
recompiling the class library, start with `Examples/README.md`.

It is not yet stable as a public library. Some names are inconsistent, some
operations still need review, some modules are unfinished, and most tests are
manual SuperCollider smoke scripts. The next phase should continue
consolidation: broaden tests, document the DSL, and make the current workflow
boringly reliable before adding many more modules.

## Current Flaws

### Time and Range Handling

`TimeRange` is now the canonical internal representation for active ranges.
External shorthand still works:

- raw two-item arrays of ticks, such as `[startTick, endTick]`;
- pairs of `Pos` values, such as `[Pos(1), Pos(2)]`;

Those forms are normalized into `TimeRange` as soon as they enter range-aware
methods. `TimeRange` owns ordering, duration, containment, overlap,
intersection, and display conversion back to `Pos`.

Range behavior still needs broader testing, especially around changing metres
and empty material, but the raw-array duplication between `Box`, `BoxMulti`, and
`VoxNode.>>/` has been reduced.

### API Mismatches and Unfinished Surfaces

Some sketch code references APIs that do not currently exist. For example,
`Tests/vox_to_musicxml.scd` uses `Vox.fromMIDI`, while the implemented import
paths are `Box.fromMIDI` and `BoxMulti.fromMIDI`.

Some files are placeholders or partial experiments:

- `Circulator.sc` is empty.
- `Glom.sc` is empty.
- `WordProcessor.sc` appears to be copied from granulation logic and is not yet
  a working text-to-music processor.
- `>>+=`, `>>&`, and parts of splitting/routing are still speculative.

These are not bad signs. They are normal prototype traces. But they should be
made explicit so future work can separate finished behavior from sketches.

### Runtime and Debug Noise

Routine debug output has been removed from the main patching, loading, routing,
and module paths touched by the MVP pass. `Box.commit`, `undo`, and `redo` now
return after successful standalone operations instead of warning afterwards.

Some intentional diagnostic methods still print, such as `VoxHistory.log` and
`MetreMap.listEntries`.

### Architecture Still Settling

`VoxRouter` and `VoxProxy` are now the main path for labelled voice routing.
Routing is label-first by default. A route can transform selected labels and, by
default, preserve unmentioned labels as fallback voices.

Copy and source semantics also need careful attention. `Vox` and `VoxMulti`
copy most internal data, but source references are retained. `Box.fromVox`,
`BoxMulti.fromVoxMulti`, and module outputs all need a consistent story about
what counts as provenance, what should be deep-copied, and what should stay live.

`MetreMap.copy` now exists and copies its regions.

### Testing and Documentation Gaps

The `Tests/` directory contains useful working sketches plus
`Tests/mvp_smoke.scd`, a small manual smoke script for the MVP behavior. `sclang`
was not available in the shell used for this pass, so the smoke script is
intended to be run from SuperCollider after recompiling the class library.

Dependencies are also not documented in a user-facing way. At minimum, the
project should eventually explain its assumptions around SuperCollider,
`SimpleMIDIFile`, MIDI setup, JSON export, and the Python `music21` bridge.

`VoxModule.schelp` now includes a concise module-author note. That should grow
as module contracts settle.

## Remaining Plan

### 1. Broaden Core Contract Tests

The core `.out` contract is:

- `Box.out` returns a `Vox`.
- `BoxMulti.out` returns a `VoxMulti`.
- `VoxModule.out` returns a `Vox` or `VoxMulti`.
- `VoxRouter.out` returns a `VoxMulti`.

The next step is to add more repeatable tests for empty material, history,
metadata/source propagation, and multi-voice label behavior.

### 2. Lock the Patching DSL

Current operator behavior:

- `>>>`: connect this node into a downstream processing node, or gather a
  snapshot when the target is a `Box` or `BoxMulti`.
- `<<<`: connect this source into the head of an existing chain.
- `>>=`: assign to a symbol or load a snapshot into a compatible box.
- `>>==>`: force-load into a compatible box.
- `>>@`: route labelled voices.
- `>>*`: create a live labelled-voice selector from a multi.
- `>>/`: create a live clipped view of a time range.

This should be tested and documented more completely before the DSL grows.

### 3. Turn More Sketches Into Tests

Keep `Tests/hc.scd` as the musical integration example, and add smaller
repeatable checks around:

- MIDI import into `Box` and `BoxMulti`;
- chromatic and diatonic transposition;
- history commit, undo, and redo.

### 4. Finish, Park, or Label Experimental Modules

For each unfinished module, choose one of three states:

- finished and documented;
- explicitly experimental;
- parked outside the normal load path.

Do this before adding many more modules. A smaller set of reliable modules will
make the project easier to compose with than a larger set of ambiguous sketches.

### 5. Repair Export Workflow

The MusicXML bridge should be brought back into alignment with the actual API.
If import goes through `Box.fromMIDI`, the export sketch should not imply
`Vox.fromMIDI` exists unless that constructor is added deliberately.

The eventual export path should be simple:

1. Get a stable `Vox` or `VoxMulti`.
2. Convert events and metre regions to JSON.
3. Convert JSON to MusicXML using `music21`.

That path does not need to be grand yet. It only needs to be honest, repeatable,
and documented.

## Near-Term Definition of Done

A good next milestone would be:

- the current canon workflow runs from `Tests/hc.scd`;
- `Tests/mvp_smoke.scd` passes in SuperCollider;
- more `.schelp` examples use the live-chain vs snapshot-gather distinction;
- history behavior is tested after the snapshot changes;
- the README remains accurate after those changes.

VoxBox does not need to become polished all at once. The promising thing here is
the shape of the system: mutable boxes for working material, immutable-ish voxes
for transformation, metre-aware timing, and a concise patching DSL for musical
flow. The next phase is to make that shape dependable.
