# VoxBox

VoxBox is a SuperCollider toolkit for working with MIDI-derived musical
material as metre-aware, patchable score objects. It is meant to sit somewhere
between a live-coding library, a compositional sketchbook, and a future notation
pipeline: import MIDI, select and transform clips, route voices through modular
processes, gather the result into multi-voice structures, and play or export the
result.

The project is currently a working prototype rather than a finished public
library. Its core composition workflow is usable and smoke-tested: play chains,
routers, arrangements, forks, or taps directly when you want live parameter
updates, and gather into `Box` or `BoxMulti` when you want to deposit a snapshot
for further editing. The next stage is consolidation: keep the live-coding
surface expressive while making the contracts, tests, help files, and export
path dependable enough for repeated use outside the original sketches.

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
5. Add handles, taps, forks, masks, or arrangement regions when the patch needs
   to become a performance or live-coding surface.
6. Play a chain, router, fork, arrangement, or named output directly when
   parameter changes should be heard at the next unscheduled onset.
7. Gather the result into a `Box` or `BoxMulti` when you want an editable
   snapshot.
8. Eventually export stable output toward notation, for example MusicXML.

The musical centre of gravity is generative and contrapuntal. The existing
`Tests/hc.scd` sketch shows the current direction clearly: take a source line,
canonise it into multiple voices, transpose selected routes, gather them, and
listen.

## Core Model

### `Box`

`Box` is the mutable working container for a single voice or clip. It stores
events, metre information, a current highlighted `TimeRange`, history, metadata,
and a source label. It can be created directly from events or from a
`SimpleMIDIFile` via `Box.fromMIDI`. Prefer `Box.fromMIDIPath` when the source
file path should remain visible in later provenance records.

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

### `VoxBank` and `VoxArrangement`

`VoxBank` is a named library of frozen ideas. Depositing into a bank slot
resolves the current live output and stores a defensive copy:

```supercollider
~chain >>= ~bank.slot(\opening);
~bank.at(\opening); // copied frozen Vox or VoxMulti
```

`VoxArrangement` is a live `VoxNode` backed by copied region snapshots. It owns
one authoritative `MetreMap`, renders a `VoxMulti`, and can be played directly
through `VoxPlayer`. Regions can preserve elapsed tick placement or remain
attached to a score position when metre changes:

```supercollider
~bank.at(\opening) >>= ~arr.slot(\openingA, Pos(0), anchor: \ticks);
~chain >>= ~arr.slot(\reply, Pos(8), mode: \interweave, anchor: \pos);
VoxPlayer.new(~arr).loopMIDI(m);
```

For sketching contiguous blocks, use the sequence helpers instead of calculating
every start tick yourself:

```supercollider
~arr.append(\opening, ~bank.at(\opening));
~arr.append(\reply, ~bank.at(\reply));
~arr.insertBefore(\bridge, \reply, ~bank.at(\bridge));
~arr.moveAfter(\opening, \reply);
~arr.postReadout;
```

Arrangement overlap modes are `\overlay`, `\interweave`, and `\splice`.
Gather with `~arr >>> BoxMulti.new` when you want an editable local copy.

### Provenance, `.vox` snapshots, and saved banks

`Vox.source` remains a shallow runtime-only diagnostic reference. Durable
lineage lives in `metadata[\provenance]` as a copied descriptive recipe tree.
Rendered module output carries the applied recipe, so a frozen `Vox` or
`VoxMulti` remains understandable after the live graph has gone. Live `.out`
calls do not grow or mutate that tree. Snapshot boundaries add markers when
material is gathered, clipped eagerly, deposited in a `VoxBank`, or placed into
a `VoxArrangement`.

```supercollider
~source = Box.fromMIDIPath("/absolute/path/source.mid", \dux);
~clip = ~source >>> CTransposer(7) >>/ [Pos(1), Pos(3)];
~clip >>= ~bank.slot(\opening);

~clip.postChain;       // concise, source-first processing summary
~clip.postProvenance;  // complete recipe tree

~clip.writeVox("/absolute/path/opening.vox");
~loadedVox = Vox.read("/absolute/path/opening.vox"); // Vox or VoxMulti

~bank.postProvenance(\opening);
~bank.writeVoxBank("/absolute/path/ideas.voxbank");
~loaded = VoxBank.read("/absolute/path/ideas.voxbank");
```

The `.vox` and `.voxbank` formats are versioned JSON data. They reconstruct
frozen `Vox` and `VoxMulti` values only; they do not rebuild live patch cables.
The existing `VoxBank.writeJSON` and `VoxBank.readJSON` APIs and
`.voxbank.json` files remain supported. Begin a new editable stage with
`Box.fromVox(~loaded.at(\opening))` or `BoxMulti.fromVoxMulti(...)`.

Symbolic MIDI destinations, realized articulation names and channels, and the
destination/articulation processing policy survive in saved events and
provenance. `VoxOut` taps, clocks, destination-to-port maps, and actual
`MIDIOut` objects remain runtime-only and must be supplied again for playback.

For routed material, `postChain` factors out common ancestry and prints only the
divergent processing beneath each voice label. The stored descriptive recipe is
unchanged; this is a presentation improvement rather than an archive migration.

`SimpleMIDIFile` is provided by `wslib`. `.vox` and bank persistence require
the `JSONlib` quark.

### VoxExport

`VoxExport` is the supported v1 bridge from VoxBox material to notation
interchange. Export resolves the current source to a stable `Vox` or
`VoxMulti`, writes notation-oriented JSON, then optionally converts that JSON to
MusicXML with a Python `music21` bridge.

```supercollider
~box.exportMusicXML("/absolute/path/line.musicxml");
~multi.exportMusicXML("/absolute/path/score.musicxml");
~arr.exportMusicXML("/absolute/path/piece.musicxml", keepJSON: true);
```

The default Python command is `"python3"` and it must be able to import
`music21`; pass an absolute executable path as the second argument if needed.
V1 writes one MusicXML part per `Vox`, includes rests for gaps and time
signatures from the `MetreMap`, and groups equal-duration same-start notes as
chords. It does not yet attempt full engraving: quantization, ties across bars,
beams, clef inference, staff mapping and robust overlapping voices are later
notation-engine work.

### `VoxNode`

`VoxNode` is the base class for patchable objects. It defines the operator DSL
that gives the project its live-coding feel:

- `@` assigns a handle to a node so it can be found later with `node(\handle)`
  or bracket lookup such as `~chain[\grain]`.
- `>>>` connects live processing nodes. When the downstream target is a `Box` or
  `BoxMulti`, it gathers a snapshot into that container instead.
- `<<<` connects a source into the head of an existing chain.
- `>>=` binds a chain to an environment symbol through `VoxSession`, loads a
  snapshot into a `Box` / `BoxMulti`, or deposits a snapshot into a bank or
  arrangement slot.
- `>>==>` force-loads into a target.
- `>>@` routes labelled voices through a `VoxRouter`.
- `>>*` creates a live labelled-voice selector from a `VoxMulti`.
- `>>/` creates a live clipped view of the current output.
- `>><` creates an old-style `VoxPatcher` split for value selections, or a live
  `VoxFork` when the spec contains node-valued branches.

This layer is powerful, but it is also one of the places where the design is
still being discovered. The operators need a clearer written contract before
the library can feel predictable.

### Live-coding layer

The current live-coding layer is now more than a sketch. Handled nodes, session
binding, named output taps, forks, masks, and fragment selectors are implemented
and covered by `Tests/livecoding_smoke.scd`.

Handles let a rebound chain keep the same underlying node object when the handle
and class match:

```supercollider
~line
>>> (Granulator([2], depth: 1) @ \grain)
>>> (HarmonyMask(root: 60) @ \harm)
>>= \chain;

~chain[\grain].depth = 2;
~chain[\harm].root = 62;
```

`VoxSession` owns environment-bound chains and persistent named players. When a
chain is rebound with `>>= \name`, session reconciliation preserves compatible
handled nodes, copies compatible parameter values, and activates any `VoxOut`
taps found in the chain or fork branches.

`VoxOut` is a pass-through tap that registers a named player without changing
the musical material. `.on` and `.off` control audibility only; both forms keep
the tap's transport running so named outputs stay synchronized:

```supercollider
~line
>>> VoxOut.loopMIDI(\raw, ~sink, ~clock).on
>>> CTransposer(7)
>>> VoxOut.loopMIDI(\shifted, ~sink, ~clock).off
>>= \performance;

~controls = VoxOutControls.new;
~controls.pause(\raw);
~controls.resume(\raw);
~controls.map(\cmd1, \shifted, \on);
~controls.trigger(\cmd1);
```

One `VoxPlayer` can also schedule a shared score timeline across several named
MIDI destinations. Events carry durable symbols; actual `MIDIOut` objects remain
runtime-only:

```supercollider
MIDIClient.init;
~ports = (
    violin1: MIDIOut.newByName("IAC Driver", "Violin I"),
    violin2: MIDIOut.newByName("IAC Driver", "Violin II"),
    horn1: MIDIOut.newByName("IAC Driver", "Horn I")
);
~articulationMap = (
    violin1: (pizz: 0, staccato: 1, colLegno: 2),
    violin2: (pizz: 0, staccato: 1, tremolo: 2),
    horn1: (staccato: 0, legato: 1)
);
~articulationChoices = (
    violin1: (pizz: 3, staccato: 4, colLegno: 1),
    violin2: [\pizz, \staccato, \tremolo],
    horn1: (staccato: 2, legato: 5)
);

~orchestra
>>> VoxRandMIDIDestinationMask(
    [\violin1, \violin2, \horn1],
    [3, 2, 1],
    division: Pos(1)
)
>>> VoxRandArticulation(
    ~articulationMap,
    choices: ~articulationChoices,
    scope: \window,
    division: Pos(beat: 1)
)
>>> VoxOut.playMultiMIDI(
    \orchestra,
    ~ports,
    ~clock,
    defaultDestination: \violin1
)
>>= \orchestraChain;
```

`playMIDI` and `loopMIDI` retain their single-port behavior. The explicit
`playMultiMIDI` and `loopMultiMIDI` variants resolve each event's
`midiDestination`, while its realized `channel` selects the articulation inside
that destination. A valid explicit default receives unassigned material;
otherwise unresolved events are warned about and skipped.

`VoxFork` duplicates one live source into independent branch chains and gathers
their outputs back into a `VoxMulti`. Branches can be addressed by key, can use
their own handles, and can be masked independently:

```supercollider
~fork = ~line >>< [
    \a -> ((VoxCanoniser(1, [\aVoice], [Pos(0)], \canonA) @ \canonA)
        >>> VoxOut.loopMIDI(\a, ~sink, ~clock).off),
    \b -> ((VoxCanoniser(1, [\bVoice], [Pos(0)], \canonB) @ \canonB)
        >>> VoxOut.loopMIDI(\b, ~sink, ~clock).off)
];

~fork.hocket(\a, \b, by: Fragments(\eighth));
```

`VoxMask`, `Fragments`, and `Eighths` are contribution filters for playback or
branch output. They filter events by onset cell while preserving original
absolute timing and source span for synchronised playback. Masks can be used as
a convenience method or written directly into a chain:

```supercollider
~line.mask(Eighths[0, 2]);

~line
>>> VoxMask(Fragments(\quarter)[0, 2]).atRange([Pos(1), Pos(2)])
>>> VoxOut.loopMIDI(\masked, ~sink, ~clock).on;

~line
>>> VoxGridSplitter(\eighth)
>>> VoxMask(Eighths[0, 2, 4])
>>> VoxOut.loopMIDI(\hocketReady, ~sink, ~clock).on;
```

With `.atRange`, fragment indices are resolved from the start of the scoped
range and material outside the range passes through unchanged. Masks are
intentionally separate from `Box` highlighting and editing ranges, and they do
not trim sustained notes at mask boundaries.

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
- `VoxGridSplitter`: deterministically splits events at rhythmic grid borders.
- `VoxRandChannelBlock`: randomly selects a weighted destination MIDI-channel block while preserving every voice's relative channel position.
- `VoxRandChannelBlockMask`: makes that weighted block choice independently for repeating or explicitly bounded score-time windows, shared across all voices by event onset.
- `VoxMIDIDestination`: assigns a durable symbolic MIDI destination.
- `VoxRandMIDIDestination`: chooses one weighted destination for a complete render.
- `VoxRandMIDIDestinationMask`: chooses a shared weighted destination per score-time window.
- `VoxRandArticulation`: chooses from live destination-specific articulation weights, then realizes the selection through a stable articulation-to-channel map.
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
forking one routine for every event in a loop. It builds a sorted onset index
when the source revision changes, then uses binary-search window lookup during
stable playback. Peak queued work and per-poll event visits are therefore
proportional to nearby events and active notes, not the full clip. Rendering and
indexing a very large edited graph may still cause a brief refresh pause.

## Current Status

VoxBox is useful today as a personal composition and live-coding toolkit. It can
import MIDI, construct canon-like and routed multi-voice material, transform and
clip score objects, capture named ideas in a bank, arrange frozen ideas on a
metre-aware timeline, and play live graphs through a rolling scheduler. The
newer live-coding layer adds handles, persistent named output taps, session
rebinding, forked branch chains, masks, fragments, and hocket-style branch
distribution.

For small hardware-free sketches that can be evaluated block by block after
recompiling the class library, start with `Examples/README.md`. The examples
now cover:

- live boxes, live clipping, and explicit snapshot gathering;
- canon routing and labelled voice selection;
- seeded random processing;
- rolling playback;
- banks and arrangements;
- provenance and bank JSON persistence;
- real MIDI exploration with `Examples/hc_orig.mid`;
- handles, taps, forks, masks, and fragments.

The repository also contains repeatable smoke scripts for the MVP core, rolling
playback, probabilistic processing, bank/arrangement behavior,
provenance/archive round trips, and the live-coding layer. They are still
SuperCollider scripts rather than an automated CI suite, but they describe the
current working contract more accurately than the older exploratory notebooks.

It is not yet stable as a public library. Some names are inconsistent, some
operators still need review, and some modules are unfinished. The notation
export bridge now has a supported v1 path, but engraving-quality output remains
future work. The next phase should continue consolidation: broaden tests,
document the DSL, and make the existing workflow boringly reliable.

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

Most old sketch code has been pulled back toward the current API. New MIDI
material should enter through `Box.fromMIDI`, `Box.fromMIDIPath`,
`BoxMulti.fromMIDI`, or `BoxMulti.fromMIDIPath`.

Some files are placeholders or partial experiments:

- `Circulator.sc` is empty.
- `Glom.sc` is empty.
- `WordProcessor.sc` is an experimental text/binary/morse event selector rather
  than a settled text-to-music processor.
- `>>+=`, `>>&`, and parts of old value-splitting are still speculative.

These are not bad signs. They are normal prototype traces. But they should be
made explicit so future work can separate finished behavior from sketches.

### Runtime and Debug Noise

Routine debug output has been removed from the main patching, loading, routing,
and module paths touched by the MVP pass. `Box.commit`, `undo`, and `redo` now
return after successful standalone operations instead of warning afterwards.

Some intentional diagnostic methods still print, such as `VoxHistory.log` and
`MetreMap.listEntries`.

Named `VoxOut` taps are deliberately stateful through `VoxSession`. `.on` and
`.off` now control audibility while transport control lives in
`VoxOutControls`. This keeps compatible taps synchronized across rebindings, but
it also means session lifecycle and cleanup need clearer documentation before
this should be treated as a polished public API.

### Architecture Still Settling

`VoxRouter` and `VoxProxy` are now the main path for labelled voice routing.
Routing is label-first by default. A route can transform selected labels and, by
default, preserve unmentioned labels as fallback voices.

`Vox` and `VoxMulti` copy payload data while retaining `source` references
shallowly. `source` is diagnostic only: operators do not follow it to infer
connectivity. Serializable descriptive lineage is stored separately in
`metadata[\provenance]`.

`MetreMap.copy` now exists and copies its regions.

### Testing and Documentation Gaps

The `Tests/` directory contains useful working sketches plus repeatable smoke
scripts for the MVP core, rolling playback, probabilistic modules,
bank/arrangement behavior, provenance/archive round trips, and live-coding
behavior. They are intended to be run from `sclang` after recompiling the class
library.

The core currently assumes SuperCollider, `SimpleMIDIFile` from `wslib`, and
`JSONlib` for bank persistence. `Granulator` smoke tests assume `RTProbMap` and
`rtdivide` are available in the local environment. MIDI output setup and the
experimental Python `music21` bridge still need a fuller user-facing guide.

The HelpSource tree has started catching up, including pages for selectors,
clippers, routers, provenance, banks, arrangements, and player diagnostics.
Those pages should continue to grow from the smoke-test contracts rather than
from aspirational APIs.

## Remaining Plan

### 1. Promote Smoke Scripts Into a Repeatable Test Harness

The current smoke scripts are valuable, but they are still manually run from
SuperCollider. The next practical milestone is a repeatable test harness that
can run the smoke suite from `sclang`, fail loudly, and make it easy to protect
the existing behavior before refactoring.

The core `.out` contract to preserve is:

- `Box.out` returns a `Vox`.
- `BoxMulti.out` returns a `VoxMulti`.
- `VoxModule.out` returns a `Vox` or `VoxMulti`.
- `VoxRouter.out` returns a `VoxMulti`.
- `VoxFork.out` returns a `VoxMulti`.
- `VoxArrangement.out` returns a `VoxMulti`.

The next step is to add more repeatable tests for empty material, history,
metadata/source propagation, and multi-voice label behavior.

### 2. Lock the Patching DSL

Current operator behavior:

- `>>>`: connect this node into a downstream processing node, or gather a
  snapshot when the target is a `Box` or `BoxMulti`.
- `<<<`: connect this source into the head of an existing chain.
- `>>=`: assign to a symbol, load a compatible box, or deposit into a
  `VoxBank.slot` / `VoxArrangement.slot` snapshot target.
- `>>==>`: force-load into a compatible box.
- `>>@`: route labelled voices.
- `>>*`: create a live labelled-voice selector from a multi.
- `>>/`: create a live clipped view of a time range.
- `>><`: split value selections or create live forks, depending on the spec.
- `@`: name nodes inside a chain for later lookup and session reconciliation.

This should be tested and documented more completely before the DSL grows. In
particular, `>><` now carries two meanings and should either be clarified,
renamed, or carefully documented.

### 3. Turn More Sketches Into Tests

Keep `Tests/hc.scd` as the musical integration example, and add smaller
repeatable checks around:

- MIDI import into `Box` and `BoxMulti`;
- chromatic and diatonic transposition;
- history commit, undo, and redo;
- session reset, named output cleanup, and fork/tap lifecycle edge cases.

### 4. Finish, Park, or Label Experimental Modules

For each unfinished module, choose one of three states:

- finished and documented;
- explicitly experimental;
- parked outside the normal load path.

Do this before adding many more modules. A smaller set of reliable modules will
make the project easier to compose with than a larger set of ambiguous sketches.

### 5. Grow VoxExport Carefully

The v1 MusicXML bridge is now aligned with the actual public API: export from
`Box`, `BoxMulti`, `VoxArrangement`, `Vox`, or `VoxMulti`. The next export
phase should improve notation fidelity without obscuring the current honest
path:

1. Keep resolving to stable `Vox` or `VoxMulti` snapshots.
2. Keep the VoxExport JSON schema simple and versioned.
3. Add engraving features deliberately, starting with ties, quantization and
   clearer overlap handling.

### 6. Decide the Public Shape

The long-term project direction is a dependable composition environment, not
just a bag of transformations. The likely public shape is:

- `Box` / `BoxMulti` for editable working material;
- `Vox` / `VoxMulti` for copied score values;
- `VoxNode` graphs for live phrase flow;
- `VoxBank` for frozen ideas;
- `VoxArrangement` for timeline assembly;
- `VoxSession` / `VoxOut` / `VoxOutControls` for live performance state;
- provenance and archive data as the bridge between experimental live work and
  repeatable notation/export.

Future features should strengthen that shape: clearer score editing, better
metre-change behavior, reliable export, and a smaller set of well-documented
musical modules.

## Near-Term Definition of Done

A good next milestone would be:

- the current canon workflow runs from `Tests/hc.scd` and
  `Examples/07_real_midi_exploration.scd`;
- `Tests/mvp_smoke.scd` passes in SuperCollider;
- `Tests/playback_smoke.scd`, `Tests/bank_arrangement_smoke.scd`,
  `Tests/provenance_archive_smoke.scd`, and `Tests/livecoding_smoke.scd` pass
  in SuperCollider;
- more `.schelp` examples use the live-chain vs snapshot-gather distinction;
- history behavior and session lifecycle are tested after the snapshot and
  live-coding changes;
- VoxExport v1 smoke tests pass and its documented limits remain accurate;
- the README and HelpSource remain accurate after those changes.

VoxBox does not need to become polished all at once. The promising thing here is
the shape of the system: mutable boxes for working material, immutable-ish voxes
for transformation, metre-aware timing, and a concise patching DSL for musical
flow. The next phase is to make that shape dependable.
