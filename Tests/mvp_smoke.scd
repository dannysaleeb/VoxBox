// VoxBox MVP smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// It uses small in-memory event lists rather than MIDI files.

(
var assert, map, makeEvent, sourceBox, clipped, liveTransposer, snapshotBox;
var canoniser, routeTransposer, router, routed;

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

// Modules stay live when played or queried directly.
liveTransposer = CTransposer(0);
sourceBox >>> liveTransposer;
assert.(liveTransposer.out.events.first[\midinote] == 60, "live chain initial pitch");
liveTransposer.semitones = 7;
assert.(liveTransposer.out.events.first[\midinote] == 67, "live chain parameter update");

// Containers gather snapshots and do not retain hidden upstream input.
snapshotBox = Box.new(nil, map, \dux);
liveTransposer >>> snapshotBox;
liveTransposer.semitones = 12;
assert.(snapshotBox.out.events.first[\midinote] == 67, "Box gather is a snapshot");

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

"VoxBox MVP smoke passed.".postln;
)
