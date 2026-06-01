// Granulator + HarmonyMask smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// It assumes RTProbMap and rtdivide are available in the local environment.

(
var assert, isScalePc, map, makeEvent, sourceBox, clippedBox, gran, granSeedA, granSeedB, maskA, maskB, manualVox;
var granOut, maskedA, maskedB, onset, continuation, continuationPc, manualMasked;
var chordA, chordB, chords, chordVox, chordMask, chordMasked, chordContinuation, chordContinuationPc;
var originA, originB, globalA, globalB;

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

granSeedA = Granulator([2], RTProbMap.new, 1, 1234).process(sourceBox.out);
granSeedB = Granulator([2], RTProbMap.new, 1, 1234).process(sourceBox.out);
assert.(granSeedA.events.collect(_[\dur]) == granSeedB.events.collect(_[\dur]), "seeded Granulator is reproducible");

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

chordA = ChordSpec(60, Scale.at(\ionian), [0, 2, 4]);
chordB = ChordSpec(65, Scale.at(\ionian), [0, 2, 4]);
chords = ChordMap(map)
	.add(0, chordA)
	.add(Pos(0, 1), chordB);

assert.(chordA.isScaleMidi(64), "ChordSpec detects scale tones");
assert.(chordA.isChordTone(64), "ChordSpec detects chord tones");
assert.(chordA.chordToneCandidatesAround(61, 6).includes(60), "ChordSpec lists chord-tone candidates");
assert.(chords.atTick(-1).isNil, "ChordMap returns nil before first region");
assert.(chords.atTick(0) == chordA, "ChordMap returns first chord at its start");
assert.(chords.atTick(959) == chordA, "ChordMap keeps chord active until next region");
assert.(chords.atTick(960) == chordB, "ChordMap changes chord by absolute tick");
assert.(chords.atPos(Pos(0, 1)) == chordB, "ChordMap accepts Pos lookup");

chordVox = Vox.new([
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
	),
	(
		absTime: 960,
		dur: 480,
		midinote: 62,
		velocity: 90,
		channel: 0,
		position: TimeConverter.ticksToPos(960, map),
		metadata: Dictionary.newFrom([
			\grain_origin_midinote, 62,
			\grain_is_onset, true
		])
	),
	(
		absTime: 1440,
		dur: 480,
		midinote: 62,
		velocity: 90,
		channel: 0,
		position: TimeConverter.ticksToPos(1440, map),
		metadata: Dictionary.newFrom([
			\grain_origin_midinote, 62,
			\grain_is_onset, false
		])
	)
], map, \chordManual);

chordMask = HarmonyMask(
	chordMap: chords,
	window: 12,
	seed: 1234,
	chordBias: 4,
	randomness: 0,
	stickiness: 0,
	memoryMode: \origin
);
chordMasked = chordMask.process(chordVox);
chordContinuation = chordMasked.events.last;
chordContinuationPc = (((chordContinuation[\midinote] - chordB.root) % 12) + 12) % 12;

assert.(chordMasked.events[2][\midinote] == 62, "Chord-aware HarmonyMask preserves onsets");
assert.(chordMasked.events[1][\midinote] != chordMasked.events[1][\metadata][\grain_origin_midinote], "Chord-aware HarmonyMask repitches continuation grains when alternatives exist");
assert.(isScalePc.(chordB.scale, chordContinuationPc), "Chord-aware HarmonyMask uses active chord scale");
assert.((chordContinuation[\midinote] - chordContinuation[\metadata][\grain_origin_midinote]).abs <= 12, "Chord-aware HarmonyMask respects window");
assert.(chordContinuation[\metadata][\harmony_mask_chord_root] == 65, "HarmonyMask stores active chord root from absolute tick");
assert.(chordContinuation[\metadata][\harmony_mask_is_chord_tone].notNil, "HarmonyMask stores chord-tone flag");
assert.(chordContinuation[\metadata][\harmony_mask_cost].notNil, "HarmonyMask stores scored candidate cost");

originA = HarmonyMask(chordMap: chords, seed: 5678, randomness: 0.1, memoryMode: \origin).process(chordVox);
originB = HarmonyMask(chordMap: chords, seed: 5678, randomness: 0.1, memoryMode: \origin).process(chordVox);
globalA = HarmonyMask(chordMap: chords, seed: 5678, randomness: 0.1, memoryMode: \global).process(chordVox);
globalB = HarmonyMask(chordMap: chords, seed: 5678, randomness: 0.1, memoryMode: \global).process(chordVox);

assert.(originA.events.collect(_[\midinote]) == originB.events.collect(_[\midinote]), "origin memory mode is seed-reproducible");
assert.(globalA.events.collect(_[\midinote]) == globalB.events.collect(_[\midinote]), "global memory mode is seed-reproducible");
assert.(originA.events.size == chordVox.events.size, "origin memory mode preserves event count");
assert.(globalA.events.size == chordVox.events.size, "global memory mode preserves event count");

"VoxBox granulator/harmony smoke passed.".postln;
)
