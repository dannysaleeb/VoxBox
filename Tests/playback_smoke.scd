// Rolling VoxPlayer smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// It uses VoxRecordingMIDIOut rather than external MIDI hardware.

(
var assert, map, makeEvent, sourceBox, transposer, sink, clock, player, persistentPlayer;
var largeBoxes, largeMulti, indexPlayer, indexedParts, missingSelector, missingPlayer;

assert = { |condition, message|
	if (condition.not) {
		Error("VoxBox playback smoke failed: " ++ message).throw;
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

// A large BoxMulti builds one sorted onset index, then window lookup visits
// only nearby notes rather than flattening and scanning the whole score.
largeBoxes = 4.collect { |voice|
	Box.new(
		1000.collect { |index|
			makeEvent.(index * 960, 240, 48 + voice, 90, voice)
		},
		map,
		("voice" ++ voice).asSymbol
	)
};
largeMulti = BoxMulti.new(largeBoxes, map, \largeScore);
indexPlayer = VoxPlayer.new(largeMulti);
indexedParts = indexPlayer.eventParts;
assert.(indexedParts.size == 4000, "large multi is flattened into one playback index");
assert.(indexPlayer.indexBuildCount == 1, "index builds once on first render");
indexPlayer.eventParts;
assert.(indexPlayer.indexBuildCount == 1, "stable reads reuse playback index");
indexedParts = indexPlayer.partsBetween(9600, 9600);
assert.(indexedParts.size == 4, "window lookup returns only matching multi-voice onsets");
assert.(indexPlayer.lastWindowVisitCount == 4, "window lookup visits only nearby onsets");
largeBoxes[0].events[0][\midinote] = 72;
largeBoxes[0].touch;
indexPlayer.eventParts;
assert.(indexPlayer.indexBuildCount == 2, "source edit rebuilds playback index once");

// Invalid live selections render once per revision rather than flooding warnings
// on every rolling-player poll.
missingSelector = largeMulti >>* \missingVoice;
missingPlayer = VoxPlayer.new(missingSelector);
assert.(missingPlayer.eventParts.isEmpty, "missing live selection renders as an empty player index");
missingPlayer.eventParts;
assert.(missingPlayer.indexBuildCount == 1, "missing live selection is cached for the current revision");

sourceBox = Box.new([
	makeEvent.(0, 3840, 60),
	makeEvent.(960, 240, 62)
], map, \line);
sourceBox.channel = 5;
persistentPlayer = VoxPlayer.new(sourceBox, clock);
assert.(persistentPlayer.channelFor(persistentPlayer.eventParts.first) == 5, "Box channel assignment is used without an explicit player map");
transposer = CTransposer(0);
sourceBox >>> transposer;

sink = VoxRecordingMIDIOut.new;
clock = TempoClock.new(20);
player = VoxPlayer.new(transposer, clock);
player.channelMap = Dictionary.newFrom([\line, 3]);
player.loopMIDI(sink);

Routine({
	var firstOff, retrigger;
	var boundarySource, boundarySink, boundaryClock, boundaryPlayer, boundaryOns;
	var portA, portB, multiPortClock, multiPortSource, multiPortPlayer, noDefaultSink, noDefaultPlayer;

	// The first note starts immediately and remains held across the edit.
	0.02.wait;
	assert.(player.indexBuildCount == 1, "rolling playback builds index once before stable polling");
	assert.(sink.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 60 }
	}.notNil, "initial note-on is emitted");
	assert.(player.scheduledCount < 3, "lookahead keeps the queued onset count bounded");

	// The second onset has not been scheduled yet. It should hear the edit.
	transposer.semitones = 12;
	0.06.wait;
	assert.(player.indexBuildCount == 2, "live edit rebuilds rolling playback index once");
	assert.(sink.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 74 }
	}.notNil, "future onset refreshes after revision change");

	// The held note is not retriggered when the graph changes.
	firstOff = sink.messages.detectIndex { |message|
		message[\type] == \noteOff and: { message[\note] == 60 }
	};
	retrigger = sink.messages.detectIndex { |message|
		message[\type] == \noteOn and: { message[\note] == 72 }
	};
	assert.(
		retrigger.isNil or: { firstOff.notNil and: { firstOff < retrigger } },
		"held note is not retriggered before its original note-off"
	);
	assert.(sink.messages.detect { |message|
		message[\type] == \noteOn and: { message[\channel] == 3 }
	}.notNil, "explicit label-to-channel mapping is used");

	player.stop;
	assert.(player.activeMIDICount == 0, "stop clears active MIDI notes");
	assert.(sink.messages.detect { |message|
		message[\type] == \noteOff and: { message[\note] == 60 }
	}.notNil, "stop sends note-off for held notes");

	// Lookahead crossing a loop boundary schedules the next cycle onset.
	boundarySource = Box.new([
		makeEvent.(0, 10, 50),
		makeEvent.(950, 50, 51)
	], map, \boundary);
	boundarySink = VoxRecordingMIDIOut.new;
	boundaryClock = TempoClock.new(100);
	boundaryPlayer = VoxPlayer.new(boundarySource, boundaryClock);
	boundaryPlayer.lookahead = 0.2;
	boundaryPlayer.pollInterval = 0.05;
	boundaryPlayer.loopMIDI(boundarySink);
	0.05.wait;
	boundaryOns = boundarySink.messages.select { |message|
		message[\type] == \noteOn and: { message[\note] == 50 }
	};
	assert.(boundaryOns.size >= 2, "loop-boundary lookahead schedules next-cycle onset");
	boundaryPlayer.stop;

	// One rolling player preserves a shared timeline while resolving events to multiple MIDI outputs.
	portA = VoxRecordingMIDIOut.new;
	portB = VoxRecordingMIDIOut.new;
	multiPortClock = TempoClock.new(20);
	multiPortSource = BoxMulti.new([
		Box.new([
			makeEvent.(0, 3840, 60, 90, 2).put(\midiDestination, \violin1),
			makeEvent.(0, 240, 65, 90, 3)
		], map, \multiA),
		Box.new([
			makeEvent.(0, 3840, 60, 90, 5).put(\midiDestination, \horn1),
			makeEvent.(960, 240, 62, 90, 6).put(\midiDestination, \horn1)
		], map, \multiB)
	], map, \multiPort);
	multiPortPlayer = VoxPlayer.new(multiPortSource, multiPortClock);
	multiPortPlayer.playMultiMIDI((violin1: portA, horn1: portB), \violin1);
	0.02.wait;
	assert.(portA.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 60 and: { message[\channel] == 2 } }
	}.notNil, "multi-port player sends assigned material to first destination");
	assert.(portB.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 60 and: { message[\channel] == 5 } }
	}.notNil, "multi-port player sends equal pitches independently to another destination");
	assert.(portA.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 65 }
	}.notNil, "multi-port player sends unassigned material to the explicit default");
	assert.(portB.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 62 }
	}.isNil, "multi-port player preserves delayed absolute timing");
	0.06.wait;
	assert.(portB.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 62 and: { message[\channel] == 6 } }
	}.notNil, "delayed multi-port material plays later on the shared timeline");
	multiPortPlayer.off;
	assert.(portA.messages.detect { |message|
		message[\type] == \noteOff and: { message[\note] == 60 }
	}.notNil, "multi-port mute sends noteOff to first destination");
	assert.(portB.messages.detect { |message|
		message[\type] == \noteOff and: { message[\note] == 60 }
	}.notNil, "multi-port mute sends noteOff to second destination");
	assert.(multiPortPlayer.activeMIDICount == 0, "multi-port mute clears active notes across destinations");
	multiPortPlayer.stop;

	noDefaultSink = VoxRecordingMIDIOut.new;
	noDefaultPlayer = VoxPlayer.new(
		Box.new([makeEvent.(0, 240, 66).put(\midiDestination, \unknown)], map, \unknown),
		TempoClock.new(20)
	);
	noDefaultPlayer.playMultiMIDI((violin1: noDefaultSink));
	0.02.wait;
	assert.(noDefaultSink.messages.isEmpty, "unresolved multi-port events without a default are skipped");
	noDefaultPlayer.stop;

	"VoxBox playback smoke passed.".postln;
}).play(AppClock);
)
