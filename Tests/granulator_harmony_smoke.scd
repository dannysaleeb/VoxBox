// Granulator + HarmonyMask smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// It assumes RTProbMap and rtdivide are available in the local environment.

(
var assert, isScalePc, map, makeEvent, sourceBox, clippedBox, gran, maskA, maskB, manualVox;
var granOut, maskedA, maskedB, onset, continuation, continuationPc, manualMasked;

assert = { |condition, message|
	if (condition.not) {
		Error("VoxBox granulator/harmony smoke failed: " ++ message).throw;
	};
};

isScalePc = { |scale, pc|
	scale.semitones.detect { |semitone|
		semitone.asInteger == pc.asInteger
	}.notNil;
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
		position: TimeConverter.ticksToPos(start, map),
		metadata: Dictionary.newFrom([\fixture, \kept])
	)
};

sourceBox = Box.new([
	makeEvent.(0, 960, 61),
	makeEvent.(960, 960, 64)
], map, \dux);

clippedBox = sourceBox >>/ [0, 1920];
gran = Granulator([2], RTProbMap.new, 1);
clippedBox >>> gran;
granOut = gran.out;

assert.(granOut.events.size >= 2, "granulator creates or preserves events");
assert.(granOut.events.first[\metadata][\fixture] == \kept, "granulator preserves metadata");
assert.(granOut.events.first[\metadata][\grain_origin_midinote] == 61, "granulator stores origin pitch");
assert.(granOut.events.first[\metadata][\grain_origin_absTime] == 0, "granulator stores origin start");
assert.(granOut.events.first[\metadata][\grain_origin_dur] == 960, "granulator stores origin duration");
assert.(granOut.events.first[\metadata][\grain_index] == 0, "granulator stores grain index");
assert.(granOut.events.first[\metadata][\grain_count] >= 1, "granulator stores grain count");
assert.(granOut.events.first[\metadata][\grain_is_onset] == true, "granulator marks first grain onset");
assert.(granOut.events.select { |ev| ev[\metadata][\grain_is_onset] == true }.size == 2, "granulator marks one onset per source event");
assert.(granOut.events.select { |ev| ev[\position].notNil }.size == granOut.events.size, "granulator recalculates positions");

maskA = HarmonyMask(60, Scale.at(\ionian), 12, 1234);
maskB = HarmonyMask(60, Scale.at(\ionian), 12, 1234);
maskedA = maskA.process(granOut);
maskedB = maskB.process(granOut);

assert.(maskedA.events.collect(_[\midinote]) == maskedB.events.collect(_[\midinote]), "seeded HarmonyMask is reproducible");
assert.(maskedA.events.size == granOut.events.size, "HarmonyMask preserves event count");

onset = maskedA.events.detect { |ev| ev[\metadata][\grain_is_onset] == true };
assert.(onset[\midinote] == onset[\metadata][\grain_origin_midinote], "HarmonyMask preserves onset pitch");

manualVox = Vox.new([
	(
		absTime: 0,
		dur: 480,
		midinote: 61,
		velocity: 90,
		channel: 0,
		position: TimeConverter.ticksToPos(0, map),
		metadata: Dictionary.newFrom([
			\grain_origin_midinote, 61,
			\grain_is_onset, true
		])
	),
	(
		absTime: 480,
		dur: 480,
		midinote: 61,
		velocity: 90,
		channel: 0,
		position: TimeConverter.ticksToPos(480, map),
		metadata: Dictionary.newFrom([
			\grain_origin_midinote, 61,
			\grain_is_onset, false
		])
	)
], map, \manual);

manualMasked = HarmonyMask(60, Scale.at(\ionian), 12, 1234).process(manualVox);
continuation = manualMasked.events.last;
continuationPc = (((continuation[\midinote] - 60) % 12) + 12) % 12;
assert.(manualMasked.events.first[\midinote] == 61, "HarmonyMask preserves explicit onset pitch");
assert.(isScalePc.(Scale.at(\ionian), continuationPc), "HarmonyMask assigns scale pitch");
assert.((continuation[\midinote] - continuation[\metadata][\grain_origin_midinote]).abs <= 12, "HarmonyMask respects window");
assert.(continuation[\metadata][\harmony_mask_origin_midinote].notNil, "HarmonyMask stores origin pitch metadata");
assert.(continuation[\metadata][\harmony_mask_assigned_midinote] == continuation[\midinote], "HarmonyMask stores assigned pitch metadata");

"VoxBox granulator/harmony smoke passed.".postln;
)
