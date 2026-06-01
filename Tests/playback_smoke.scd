// Rolling VoxPlayer smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// It uses VoxRecordingMIDIOut rather than external MIDI hardware.

(
var assert, map, makeEvent, sourceBox, transposer, sink, clock, player;

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

sourceBox = Box.new([
	makeEvent.(0, 3840, 60),
	makeEvent.(960, 240, 62)
], map, \line);
transposer = CTransposer(0);
sourceBox >>> transposer;

sink = VoxRecordingMIDIOut.new;
clock = TempoClock.new(20);
player = VoxPlayer.new(transposer, clock);
player.channelMap = Dictionary.newFrom([\line, 3]);
player.loopMIDI(sink);

Routine({
	var firstOff, retrigger;

	// The first note starts immediately and remains held across the edit.
	0.02.wait;
	assert.(sink.messages.detect { |message|
		message[\type] == \noteOn and: { message[\note] == 60 }
	}.notNil, "initial note-on is emitted");
	assert.(player.scheduledCount < 3, "lookahead keeps the queued onset count bounded");

	// The second onset has not been scheduled yet. It should hear the edit.
	transposer.semitones = 12;
	0.06.wait;
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

	"VoxBox playback smoke passed.".postln;
}).play(AppClock);
)
