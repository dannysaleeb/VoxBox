(
// Run this file from SuperCollider after recompiling the class library.
var assert, makeMap, makeEvent, map4, map3, map480;
var source, replacement, bank, stored, retrieved, sink;
var arrangement, output, tickBefore, posBefore, revisionBefore, gathered, livePlayer;
var base, insert, other, multi, overlay, interweave, splice;
var voiceEvents, otherEvents, historyArrangement, badVox, beforeBadAdd;
var sequenceArrangement, rows, readout;

assert = { |condition, description|
	if (condition.not) {
		Error("FAIL: %".format(description)).throw
	};
	"PASS: %".format(description).postln;
};

makeMap = { |beats, divisions, tpqn = 960|
	var map = MetreMap.new;
	beats = beats ?? { [1, 1, 1, 1] };
	divisions = divisions ?? { [4, 4, 4, 4] };
	map.add(MetreRegion.new(0, Metre.new(beats, divisions, tpqn)));
	map
};

makeEvent = { |start, dur = 480, note = 60, channel = 0, map|
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: 90,
		channel: channel,
		position: TimeConverter.ticksToPos(start, map)
	)
};

map4 = makeMap.();
map3 = makeMap.([1, 1, 1], [4, 4, 4]);
map480 = makeMap.([1, 1, 1, 1], [4, 4, 4, 4], 480);

// VoxBank stores named copies, rejects duplicate labels, and returns copies.
source = Box.new([makeEvent.(480, 480, 60, 0, map4)], map4, \voice);
replacement = Box.new([makeEvent.(960, 480, 67, 0, map4)], map4, \voice);
bank = VoxBank.new(\ideas);
source.channel = 6;
source >>= bank.slot(\opening);
assert.(bank.keys.asArray == [\opening], "node-to-bank >>= stores a named snapshot");
stored = bank.at(\opening);
assert.(stored.events[0][\channel] == 6, "box channel assignment is stored in bank snapshots");
source.events[0][\midinote] = 99;
source.channel = 9;
source.touch;
assert.(bank.at(\opening).events[0][\midinote] == 60, "bank snapshot is isolated from source mutation");
assert.(bank.at(\opening).events[0][\channel] == 6, "bank channel snapshot is isolated from source mutation");
retrieved = bank.at(0);
retrieved.events[0][\midinote] = 88;
assert.(bank.at(\opening).events[0][\midinote] == 60, "bank at returns a defensive copy");
replacement >>= bank.slot(\opening);
assert.(bank.at(\opening).events[0][\midinote] == 60, "duplicate bank slot is rejected");
bank.replace(\opening, replacement);
assert.(bank.at(\opening).events[0][\midinote] == 67, "bank replace overwrites explicitly");
replacement >>= bank.slot(\reply);
assert.(bank.at(1).events[0][\midinote] == 67, "bank preserves stable insertion order");
bank.remove(\reply);
assert.(bank.keys.asArray == [\opening], "bank removal updates the named order");
sink = VoxRecordingMIDIOut.new;
assert.(bank.playMIDI(\missing, sink).isNil, "bank playback guards missing entries");

// Arrangement accepts both frozen values and live node deposits.
arrangement = VoxArrangement.new(map4, \piece);
bank.at(\opening) >>= arrangement.slot(\openingA, Pos(1), anchor: \ticks);
source >>= arrangement.slot(\replyA, Pos(2), anchor: \pos);
assert.(arrangement.keys.asArray == [\openingA, \replyA], "value-to-arrangement and node-to-arrangement >>= both work");
assert.(arrangement.at(\openingA).resolvedStart == 3840, "tick anchor converts Pos once");
assert.(arrangement.at(\replyA).resolvedStart == 7680, "position anchor initially resolves through arrangement map");
output = arrangement.out;
assert.(output.isKindOf(VoxMulti), "arrangement out returns a VoxMulti snapshot");
assert.(output.at(\voice).events[0][\absTime] == 3840, "placement normalises the first source onset to region start");

tickBefore = arrangement.at(\openingA).resolvedStart;
posBefore = arrangement.at(\replyA).resolvedStart;
arrangement.metremap = map3;
assert.(arrangement.at(\openingA).resolvedStart == tickBefore, "tick anchor remains fixed after metre edit");
assert.(arrangement.at(\replyA).resolvedStart != posBefore, "position anchor moves after metre edit");
assert.(arrangement.at(\replyA).resolvedStart == 5760, "position anchor follows edited metre map");

revisionBefore = arrangement.effectiveRevision;
livePlayer = VoxPlayer.new(arrangement);
assert.(livePlayer.refreshRendered.at(\voice).events.last[\absTime] == 5760, "player caches current arrangement render");
arrangement.move(\replyA, Pos(3));
assert.(arrangement.effectiveRevision != revisionBefore, "arrangement movement invalidates rolling playback revision");
assert.(livePlayer.refreshRendered.at(\voice).events.last[\absTime] == 8640, "player refreshes arrangement render after movement");
gathered = arrangement >>> BoxMulti.new;
assert.(gathered.isKindOf(BoxMulti), "arrangement gathers explicitly into editable BoxMulti");

// Overlay preserves prior events.
base = Vox.new([makeEvent.(0, 4000, 60, 0, map4)], map4, \voice);
insert = Vox.new([makeEvent.(0, 1000, 72, 0, map4)], map4, \voice);
overlay = VoxArrangement.new(map4);
overlay.add(\base, 0, base);
overlay.add(\insert, 1000, insert, mode: \overlay);
assert.(overlay.out.at(\voice).events.size == 2, "overlay adds without clearing");

// Interweave clears only colliding labels and splits a spanning note.
interweave = VoxArrangement.new(map4);
interweave.add(\base, 0, base);
interweave.add(\insert, 1000, insert, mode: \interweave);
voiceEvents = interweave.out.at(\voice).events;
assert.(voiceEvents.size == 3, "interweave preserves both outside pieces around inserted material");
assert.(voiceEvents.collect(_[\absTime]) == [0, 1000, 2000], "interweave split positions are exact");
assert.(voiceEvents.collect(_[\dur]) == [1000, 1000, 2000], "interweave split durations are exact");

// Splice clears the full span across labels; multi placement retains labels.
other = Vox.new([makeEvent.(0, 4000, 48, 1, map4)], map4, \other);
multi = VoxMulti.new([base, other], map4, \twoVoices);
gathered = BoxMulti.fromVoxMulti(multi);
gathered.channels(\voice -> 2, \other -> 3);
assert.(gathered.out.at(\voice).events[0][\channel] == 2, "BoxMulti channels sets a persistent channel by label");
assert.(gathered.out.at(\other).events[0][\channel] == 3, "BoxMulti channels preserves separate label mappings");
gathered >>= bank.slot(\mappedMulti);
assert.(bank.at(\mappedMulti).at(\voice).events[0][\channel] == 2, "banked BoxMulti preserves mapped voice channel");
splice = VoxArrangement.new(map4);
splice.add(\baseMulti, 0, multi);
splice.add(\insert, 1000, insert, mode: \splice);
voiceEvents = splice.out.at(\voice).events;
otherEvents = splice.out.at(\other).events;
assert.(voiceEvents.size == 3, "splice trims colliding voice around new event");
assert.(otherEvents.collect(_[\absTime]) == [0, 2000], "splice clears the new span on other labels");
assert.(otherEvents.collect(_[\dur]) == [1000, 2000], "splice trims and splits cross-label notes");
assert.(splice.out.keys.asArray.sort == [\other, \voice], "multi-voice rendering preserves stable labels");

// Sequence helpers make region placement work as contiguous blocks.
sequenceArrangement = VoxArrangement.new(map4);
sequenceArrangement.append(\opening, insert);
sequenceArrangement.append(\reply, insert);
assert.(sequenceArrangement.keys.asArray == [\opening, \reply], "append places regions in sequence order");
assert.(sequenceArrangement.at(\opening).resolvedStart == 0, "first append starts at the arrangement beginning");
assert.(sequenceArrangement.at(\reply).resolvedStart == 1000, "second append starts after the previous region");
sequenceArrangement.insertBefore(\bridge, \reply, insert);
assert.(sequenceArrangement.keys.asArray == [\opening, \bridge, \reply], "insertBefore inserts into the block sequence");
assert.(sequenceArrangement.at(\reply).resolvedStart == 2000, "insertBefore shifts following regions by inserted duration");
sequenceArrangement.moveAfter(\opening, \reply);
assert.(sequenceArrangement.keys.asArray == [\bridge, \reply, \opening], "moveAfter reorders the block sequence");
assert.(sequenceArrangement.at(\bridge).resolvedStart == 0, "moveAfter repacks from the first region start");
assert.(sequenceArrangement.at(\reply).resolvedStart == 1000, "moveAfter keeps reordered regions contiguous");
assert.(sequenceArrangement.at(\opening).resolvedStart == 2000, "moveAfter places moved region after the target");
sequenceArrangement.insertAfter(\tag, \reply, insert);
assert.(sequenceArrangement.keys.asArray == [\bridge, \reply, \tag, \opening], "insertAfter inserts directly after its target");
assert.(sequenceArrangement.at(\opening).resolvedStart == 3000, "insertAfter shifts later regions by inserted duration");
rows = sequenceArrangement.readoutRows;
assert.(rows.size == 4, "arrangement readout rows expose every region");
assert.(rows[0][\id] == \bridge, "arrangement readout rows preserve render order");
readout = sequenceArrangement.readout;
assert.(readout.find("VoxArrangement").notNil, "arrangement readout names the arrangement");
assert.(readout.find("bridge").notNil, "arrangement readout includes region IDs");

// Arrangement history is explicit and restores exact copied states.
historyArrangement = VoxArrangement.new(map4);
historyArrangement.add(\phrase, 0, insert);
historyArrangement.commit(\placed);
historyArrangement.move(\phrase, 2400);
historyArrangement.commit(\moved);
historyArrangement.undo;
assert.(historyArrangement.at(\phrase).resolvedStart == 0, "arrangement undo restores committed placement");
historyArrangement.redo;
assert.(historyArrangement.at(\phrase).resolvedStart == 2400, "arrangement redo restores committed placement");

// Duplicate IDs and incompatible TPQN snapshots are rejected.
beforeBadAdd = historyArrangement.keys.size;
historyArrangement.add(\phrase, 3000, insert);
assert.(historyArrangement.keys.size == beforeBadAdd, "duplicate arrangement IDs are rejected");
badVox = Vox.new([makeEvent.(0, 240, 65, 0, map480)], map480, \bad);
historyArrangement.add(\badTPQN, 0, badVox);
assert.(historyArrangement.keys.size == beforeBadAdd, "incompatible TPQN source is rejected");

assert.(VoxArrangement.new(map4).out.size == 0, "empty arrangement renders an empty VoxMulti");
"BANK ARRANGEMENT SMOKE COMPLETE".postln;
)
