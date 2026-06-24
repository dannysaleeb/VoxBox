// VoxBox MVP smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// It uses small in-memory event lists rather than MIDI files.

(
var assert, map, makeEvent, sourceBox, clipped, liveTransposer, snapshotBox;
var canoniser, routeTransposer, router, routed, liveClip, selector;
var copied, historyBox, randomBox, randomModule, firstRandom, duplicateMulti;
var multiBox, replacementMulti, emptyBox, gatheredClip;
var channelBox, channelSplit, liveChannelSplit;
var rangeBox, rangeTransposer, rangeOut, rangeMultiBox, rangeMultiTransposer, rangeMultiOut;
var gridBox, gridSplitter, gridOut, offGridBox, offGridOut, rangedGridOut, maskedGridOut;

assert = { |condition, message|
	if (condition.not) {
		Error("VoxBox MVP smoke failed: " ++ message).throw;
	};
};

map = MetreMap.new;
map.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4], 960)));

makeEvent = { |start, dur, note, velocity = 90, channel = 0|
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: velocity,
		channel: channel,
		position: TimeConverter.ticksToPos(start, map)
	)
};

// TimeRange normalises tick and Pos ranges.
assert.(TimeRange.fromTicks(960, 0).asArray == [0, 960], "TimeRange tick normalisation");
assert.(TimeRange.fromPositions(Pos(1), Pos(0), map).asArray == [0, 3840], "TimeRange Pos normalisation");
assert.(TimeRange.fromTicks(0, 960).overlaps(TimeRange.fromTicks(480, 1440)), "TimeRange overlap");
assert.(TimeRange.fromTicks(0, 960).intersection(TimeRange.fromTicks(480, 1440)).asArray == [480, 960], "TimeRange intersection");

// Clipping trims notes that cross range boundaries.
sourceBox = Box.new([
	makeEvent.(0, 960, 60),
	makeEvent.(960, 960, 62)
], map, \dux);

clipped = sourceBox.clipRange(TimeRange.fromTicks(480, 1440)).out;
assert.(clipped.events.size == 2, "clip keeps overlapping events");
assert.(clipped.events.first[\absTime] == 480, "clip adjusts crossing start");
assert.(clipped.events.first[\dur] == 480, "clip trims crossing duration");

// The >>/ operator is a live view; gathering into a Box is the snapshot boundary.
liveClip = sourceBox >>/ [480, 1440];
assert.(liveClip.isKindOf(VoxClipper), ">>/ returns live clipper");
assert.(liveClip.out.events.first[\midinote] == 60, "live clip initial pitch");

// Modules stay live when played or queried directly.
liveTransposer = CTransposer(0);
sourceBox >>> liveTransposer;
assert.(liveTransposer.out.events.first[\midinote] == 60, "live chain initial pitch");
liveTransposer.semitones = 7;
assert.(liveTransposer.out.events.first[\midinote] == 67, "live chain parameter update");

liveClip = liveTransposer >>/ [0, 960];
assert.(liveClip.out.events.first[\midinote] == 67, "live clip resolves transformed output");
liveTransposer.semitones = 9;
assert.(liveClip.out.events.first[\midinote] == 69, "live clip follows upstream revision");
liveClip >>> Box.new >>= \gatheredClip;
gatheredClip = \gatheredClip.envirGet;
liveTransposer.semitones = 10;
assert.(gatheredClip.out.events.first[\midinote] == 69, "gathered clip is an editable snapshot");

// Containers gather snapshots and do not retain hidden upstream input.
snapshotBox = Box.new(nil, map, \dux);
liveTransposer >>> snapshotBox;
liveTransposer.semitones = 12;
assert.(snapshotBox.out.events.first[\midinote] == 70, "Box gather is a snapshot");

// Canon labels and offsets.
canoniser = VoxCanoniser(2, [\bass, \tenor], [Pos(0), Pos(beat: 1)], \canon);
sourceBox >>> canoniser;
assert.(canoniser.out.at(\bass).events.first[\absTime] == 0, "canon bass offset");
assert.(canoniser.out.at(\tenor).events.first[\absTime] == 960, "canon tenor offset");

// Routing transforms selected labels and preserves fallback labels.
routeTransposer = CTransposer(12);
router = canoniser >>@ [\tenor <<< routeTransposer, \bass];
routed = router.out;
assert.(routed.at(\tenor).events.first[\midinote] == 72, "routed tenor transposition");
assert.(routed.at(\bass).events.first[\midinote] == 60, "routed bass fallback");

// Selection is also live and does not follow Vox.source as a hidden wire.
selector = canoniser >>* \tenor;
assert.(selector.isKindOf(VoxSelector), ">>* returns live selector");
assert.(selector.out.events.first[\absTime] == 960, "selector reads labelled voice");
sourceBox.events.first[\midinote] = 65;
sourceBox.touch;
assert.(selector.out.events.first[\midinote] == 65, "selector follows source revision");

// Vox values copy payload data while retaining shallow provenance.
copied = sourceBox.out;
copied.events.first[\midinote] = 99;
assert.(sourceBox.out.events.first[\midinote] == 65, "Vox payload is isolated from Box");

// Undo restores a committed snapshot by replacement.
historyBox = Box.new([makeEvent.(0, 960, 60)], map, \history);
historyBox.events.first[\midinote] = 64;
historyBox.touch;
historyBox.commit(\edited);
historyBox.events.first[\midinote] = 67;
historyBox.touch;
historyBox.undo;
assert.(historyBox.out.events.first[\midinote] == 60, "undo restores previous exact snapshot");
historyBox.redo;
assert.(historyBox.out.events.first[\midinote] == 64, "redo restores committed exact snapshot");

// Seeded random modules are replayable and cached between reads.
randomBox = Box.new([makeEvent.(0, 960, 60), makeEvent.(960, 960, 62)], map, \random);
randomModule = RandElong(0.5, 2.0, 1234);
randomBox >>> randomModule;
firstRandom = randomModule.out.events.collect(_[\dur]);
assert.(randomModule.out.events.collect(_[\dur]) == firstRandom, "random module output is cached");
randomModule.reroll;
assert.(randomModule.out.events.collect(_[\dur]) == firstRandom, "seeded reroll is reproducible");

// Duplicate labels are rejected rather than silently overwriting voices.
duplicateMulti = VoxMulti.new([sourceBox.out, sourceBox.out]);
assert.(duplicateMulti.size == 1, "VoxMulti rejects duplicate labels");

// Multi loading is label-based even when input dictionary ordering differs.
multiBox = BoxMulti.new([
	Box.new([makeEvent.(0, 960, 60)], map, \a),
	Box.new([makeEvent.(0, 960, 62)], map, \b)
], map, \multi);
replacementMulti = VoxMulti.new([
	Vox.new([makeEvent.(0, 960, 74)], map, \b),
	Vox.new([makeEvent.(0, 960, 72)], map, \a)
], map, \replacement);
multiBox.forceload(replacementMulti);
assert.(multiBox.out.at(\a).events.first[\midinote] == 72, "BoxMulti non-strict load matches label a");
assert.(multiBox.out.at(\b).events.first[\midinote] == 74, "BoxMulti non-strict load matches label b");

// Channel splitting separates packed MIDI voices and normalises output channels.
channelBox = Box.new([
	makeEvent.(0, 960, 60, 90, 3),
	makeEvent.(960, 960, 62, 90, 9),
	makeEvent.(1920, 960, 64, 90, 3)
], map, \sib);
channelSplit = channelBox.splitByChannel;
assert.(channelSplit.out.keys.asArray.collect(_.asString).sort == ["sib_1", "sib_2"], "Box splitByChannel creates numbered labels");
assert.(channelSplit.out.at(\sib_1).events.collect(_[\midinote]) == [60, 64], "first split voice follows lowest source channel");
assert.(channelSplit.out.at(\sib_2).events.first[\midinote] == 62, "second split voice follows next source channel");
assert.(channelSplit.out.asArray.every { |vox| vox.events.every { |event| event[\channel] == 0 } }, "splitByChannel normalises MIDI channels to zero");
liveChannelSplit = VoxChannelSplitter.new(channelBox);
channelBox.events.first[\midinote] = 67;
channelBox.touch;
assert.(liveChannelSplit.out.at(\sib_1).events.first[\midinote] == 67, "live splitByChannel follows source edits");

// Module.atRange transforms only the selected span while preserving full context.
rangeBox = Box.new([
	makeEvent.(0, 1920, 60),
	makeEvent.(1920, 960, 62)
], map, \range);
rangeTransposer = CTransposer(7).atRange([480, 1440]);
rangeBox >>> rangeTransposer;
rangeOut = rangeTransposer.out;
assert.(rangeOut.events.collect(_[\absTime]) == [0, 480, 1440, 1920], "atRange splits events at range boundaries");
assert.(rangeOut.events.collect(_[\dur]) == [480, 960, 480, 960], "atRange preserves dry and wet durations");
assert.(rangeOut.events.collect(_[\midinote]) == [60, 67, 60, 62], "atRange only transposes inside the range");
rangeTransposer.module.semitones = 12;
assert.(rangeTransposer.out.events.collect(_[\midinote]) == [60, 72, 60, 62], "atRange follows wrapped module edits live");

rangeMultiBox = BoxMulti.new([
	Box.new([makeEvent.(0, 960, 60), makeEvent.(960, 960, 62)], map, \a),
	Box.new([makeEvent.(0, 960, 65), makeEvent.(960, 960, 67)], map, \b)
], map, \rangeMulti);
rangeMultiTransposer = CTransposer(5).atRange([0, 960]);
rangeMultiBox >>> rangeMultiTransposer;
rangeMultiOut = rangeMultiTransposer.out;
assert.(rangeMultiOut.at(\a).events.collect(_[\midinote]) == [65, 62], "atRange applies to each VoxMulti voice");
assert.(rangeMultiOut.at(\b).events.collect(_[\midinote]) == [70, 67], "atRange preserves dry material in VoxMulti voices");

// VoxGridSplitter guarantees continuation onsets at grid boundaries.
gridBox = Box.new([makeEvent.(0, 1920, 60)], map, \grid);
gridSplitter = VoxGridSplitter(\eighth);
gridBox >>> gridSplitter;
gridOut = gridSplitter.out;
assert.(gridOut.events.collect(_[\absTime]) == [0, 480, 960, 1440], "VoxGridSplitter creates eighth-note onsets");
assert.(gridOut.events.collect(_[\dur]) == [480, 480, 480, 480], "VoxGridSplitter preserves total duration across segments");
assert.(gridOut.events.collect { |event| event[\metadata][\grid_is_onset] } == [true, false, false, false], "VoxGridSplitter marks original and continuation onsets");

offGridBox = Box.new([makeEvent.(240, 720, 62)], map, \offGrid);
offGridOut = (offGridBox >>> VoxGridSplitter(\eighth)).out;
assert.(offGridOut.events.collect(_[\absTime]) == [240, 480], "VoxGridSplitter preserves off-grid starts then cuts at next boundary");
assert.(offGridOut.events.collect(_[\dur]) == [240, 480], "VoxGridSplitter preserves off-grid total duration");

rangedGridOut = (gridBox >>> VoxGridSplitter(\eighth).atRange([480, 1440])).out;
assert.(rangedGridOut.events.collect(_[\absTime]) == [0, 480, 960, 1440], "VoxGridSplitter atRange only regularises the scoped span");
assert.(rangedGridOut.events.collect(_[\dur]) == [480, 480, 480, 480], "VoxGridSplitter atRange keeps dry material around scoped span");

maskedGridOut = (gridBox >>> VoxGridSplitter(\eighth) >>> VoxMask(Eighths[0, 2])).out;
assert.(maskedGridOut.events.collect(_[\absTime]) == [0, 960], "VoxGridSplitter gives VoxMask reliable grid onsets");

emptyBox = Box.new;
assert.(emptyBox.out.events.isEmpty, "empty Box out is safe");
assert.(BoxMulti.new.out.size == 0, "empty BoxMulti out is safe");

"VoxBox MVP smoke passed.".postln;
)
