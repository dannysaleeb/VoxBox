# VoxBox

VoxBox is a SuperCollider toolkit for working with MIDI-derived musical
material as metre-aware, patchable score objects. It is meant to sit somewhere
between a live-coding library, a compositional sketchbook, and a future notation
pipeline: import MIDI, select and transform clips, route voices through modular
processes, gather the result into multi-voice structures, and play or export the
result.

The project is currently an expressive prototype rather than a stable public
library. The core ideas are strong, but some object contracts are still settling,
several modules are incomplete, and range/time handling in particular needs a
more reliable design.

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
5. Gather the result into a `BoxMulti`.
6. Play the material through MIDI or synth playback.
7. Eventually export stable output toward notation, for example MusicXML.

The musical centre of gravity is generative and contrapuntal. The existing
`Tests/hc.scd` sketch shows the current direction clearly: take a source line,
canonise it into multiple voices, transpose selected routes, gather them, and
listen.

## Core Model

### `Box`

`Box` is the mutable working container for a single voice or clip. It stores
events, metre information, a current highlighted range, history, metadata, and a
source label. It can be created directly from events or from a `SimpleMIDIFile`
via `Box.fromMIDI`.

Important responsibilities currently include:

- MIDI note/sustain event conversion.
- Sorting events by absolute time.
- Calculating `Pos` values from ticks.
- Tracking a highlighted range.
- Clipping material to the current range.
- Loading transformed output back into the box.
- Keeping a `VoxHistory` of committed states.
- Producing a `Vox` through `.out`.

The design intent is that `Box` is live and editable, while `.out` gives a
snapshot suitable for transformation.

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
snapshot. Voice labels are currently the most important identity mechanism, and
most routing examples assume labels such as `\bass`, `\tenor`, and `\treble`.

### `VoxNode`

`VoxNode` is the base class for patchable objects. It defines the operator DSL
that gives the project its live-coding feel:

- `>>>` connects one node into another.
- `<<<` connects a source into the head of an existing chain.
- `>>=` assigns a node to an environment symbol or loads it into a `Box` /
  `BoxMulti`.
- `>>==>` force-loads into a target.
- `>>@` routes labelled voices through a `VoxRouter`.
- `>>*` selects a voice from a `VoxMulti`.
- `>>/` clips a range from the current output.

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

Some module files are placeholders or early sketches rather than finished tools.

### Timing Layer

The timing model is built around:

- `Pos`: score position as bar, beat, division, tick.
- `Metre`: beat and division structure for a bar.
- `MetreRegion`: a metre starting at a tick.
- `MetreMap`: a sequence of metre regions over time.
- `TimeConverter`: conversion between ticks and score positions.

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
// Canonise the source, route selected voices, then gather them.
~dux >>> ~canoniser
>>@ [
    \tenor <<< ~transposeTenor,
    \treble <<< ~transposeTreble,
    \bass
]
>>> BoxMulti.new >>= \canon_one;
)

(
// Play the gathered result.
t = TempoClock.new(1, queueSize: 100000);
z = VoxPlayer.new(~canon_one, t);
z.loopMIDI(m, 4);
)

z.stop;
```

The important idea is that source material remains editable, modules remain
parameterised, and gathered output can be regenerated after changing upstream
settings.

## Current Status

VoxBox is useful as a personal composition experiment. It already contains
enough machinery to import MIDI, make canon-like structures, route voices,
transpose material, clip ranges, and play the result.

It is not yet stable as a public library. Some names are inconsistent, some
operations have surprising side effects, some modules are unfinished, and the
tests are really working sketches. The next phase should be consolidation:
decide the core contracts, reduce duplication, and make the current workflow
boringly reliable before adding many more modules.

## Current Flaws

### Time and Range Handling Is Fragile

Time ranges are currently represented in several ways:

- raw two-item arrays of ticks, such as `[startTick, endTick]`;
- pairs of `Pos` values, such as `[Pos(1), Pos(2)]`;
- implicit highlight state stored inside `Box` and `BoxMulti`;
- local arguments to methods such as `clipRange`, `highlight`, and `>>/`.

`Box` and `BoxMulti` each implement their own `normaliseRange` logic. Range
semantics are spread across `highlight`, `tickHighlight`, `clipRange`,
`duration`, `mirrorRangeFromMulti`, `mirrorVoxHighlight`, range propagation, and
the `>>/` operator.

This makes the system fragile. A caller has to know whether a method expects
ticks, `Pos` values, a normalised pair, or a range that has already been
converted through a particular `MetreMap`. There is also no single place for
range validation, containment checks, overlap checks, intersections, clipping
semantics, or display conversion.

This probably deserves a dedicated `TimeRange` class. That class should own
normalisation and make the internal representation explicit.

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

Several core paths still contain `postln` debugging. That makes interactive
sessions noisier and makes it harder to distinguish useful warnings from
development traces.

Some warning logic also needs tightening. For example, `Box.commit`, `undo`, and
`redo` appear to warn even after taking the valid non-`BoxMulti` path, because
the warning is not guarded as an `else`.

### Architecture Still Settling

`VoxRouter` and `VoxProxy` look like the right direction for labelled voice
routing, but the contract is not fully settled. The project should decide
whether routing is label-first, id-first, or ordered-position-first. The current
examples strongly suggest label-first routing is the natural default.

Copy and source semantics also need careful attention. `Vox` and `VoxMulti`
copy most internal data, but source references are retained. `Box.fromVox`,
`BoxMulti.fromVoxMulti`, and module outputs all need a consistent story about
what counts as provenance, what should be deep-copied, and what should stay live.

`MetreMap.copy` is also missing despite deep-copy usage elsewhere. Because
timing data sits under almost every operation, this should be resolved early.

### Testing and Documentation Gaps

The `Tests/` directory currently contains useful working sketches, not automated
regression tests. That is fine for exploration, but the core behavior now needs
small repeatable checks.

Dependencies are also not documented in a user-facing way. At minimum, the
project should eventually explain its assumptions around SuperCollider,
`SimpleMIDIFile`, MIDI setup, JSON export, and the Python `music21` bridge.

There is also no module-author guide yet. A short guide explaining how to extend
`VoxModule` would make the architecture easier to evolve.

## Recommended Plan

### 1. Stabilise Core Contracts

Define what every `.out` method returns and when it returns a copy. The core
contract should be boring and memorable:

- `Box.out` returns a `Vox`.
- `BoxMulti.out` returns a `VoxMulti`.
- `VoxModule.out` returns a `Vox` or `VoxMulti`.
- `VoxRouter.out` returns a `VoxMulti`.

Then fix copy/source metadata behavior across `Box`, `Vox`, `BoxMulti`, and
`VoxMulti`. Decide what provenance means and make it consistent.

Remove accidental debug output from core paths and fix warning branches that
fire after successful operations.

### 2. Introduce `TimeRange`

Add a small class responsible for normalised tick ranges.

Recommended responsibilities:

- construct from `[startTick, endTick]`;
- construct from `[startPos, endPos]` plus a `MetreMap`;
- guarantee `start <= end`;
- expose `start`, `end`, and `duration`;
- answer `contains(tick)`;
- answer `overlaps(otherRange)`;
- return `intersection(otherRange)`;
- convert start/end back to display `Pos` values for a given `MetreMap`.

Once it exists, use it internally in:

- `Box.highlight`;
- `Box.tickHighlight`;
- `Box.clipRange`;
- `Box.duration`;
- `BoxMulti.highlight`;
- `BoxMulti` range propagation;
- `VoxNode.>>/`.

External shorthand such as `[Pos(1), Pos(2)]` should keep working, but it should
be normalised immediately into a `TimeRange` rather than passed around as a raw
array.

This is probably the highest-leverage cleanup because range semantics touch
editing, clipping, playback preparation, routing, and history.

### 3. Lock the Patching DSL

Write down and then enforce the behavior of the patching operators:

- `>>>`: connect this node into a downstream node.
- `<<<`: connect this source into the head of an existing chain.
- `>>=`: assign to a symbol or load into a compatible box.
- `>>==>`: force-load into a compatible box.
- `>>@`: route labelled voices.
- `>>*`: select a labelled voice from a multi.
- `>>/`: clip a time range.

Make label-based routing the default unless a later design clearly needs stable
voice IDs. Then make `VoxRouter` the single implementation of routed
multi-voice processing.

### 4. Turn Sketches Into Tests

The `Tests/hc.scd` sketch is valuable because it captures the intended musical
workflow. Keep it as a manual integration example, but add smaller repeatable
tests around the primitives:

- MIDI import into `Box` and `BoxMulti`;
- tick/position conversion;
- `TimeRange` normalisation and overlap/intersection behavior;
- clipping a note that crosses range boundaries;
- chromatic and diatonic transposition;
- canon output labels and offsets;
- routed voice transformation;
- history commit, undo, and redo.

Prioritise range tests early. If range behavior is stable, many other operations
become easier to reason about.

### 5. Finish, Park, or Label Experimental Modules

For each unfinished module, choose one of three states:

- finished and documented;
- explicitly experimental;
- parked outside the normal load path.

Do this before adding many more modules. A smaller set of reliable modules will
make the project easier to compose with than a larger set of ambiguous sketches.

After that, add a minimal module-author guide: what `doProcess` receives, what
it must return, how to preserve metadata, and how to handle `VoxMulti`.

### 6. Repair Export Workflow

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

- the current canon workflow still runs;
- core `.out` return types are documented and consistent;
- `TimeRange` exists and replaces raw internal range handling;
- debug `postln`s are removed from normal paths;
- warning branches are fixed;
- at least a small smoke-test script exercises import, clipping, routing, and
  playback preparation;
- the README remains accurate after those changes.

VoxBox does not need to become polished all at once. The promising thing here is
the shape of the system: mutable boxes for working material, immutable-ish voxes
for transformation, metre-aware timing, and a concise patching DSL for musical
flow. The next phase is to make that shape dependable.
