// Rolling live playback without external MIDI hardware.
// Evaluate one parenthesized block at a time.

(
~map = MetreMap.new;
~map.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4], 960)));

~event = { |start, dur, note|
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: 90,
		channel: 0,
		position: TimeConverter.ticksToPos(start, ~map)
	)
};

~line = Box.new([
	~event.(0, 1920, 60),
	~event.(960, 480, 62),
	~event.(1920, 480, 64),
	~event.(2880, 480, 67)
], ~map, \line);

~transpose = CTransposer(0);
~line >>> ~transpose;

~sink = VoxRecordingMIDIOut.new;
~clock = TempoClock.new(2);
~player = VoxPlayer.new(~transpose, ~clock);
~player.channelMap = Dictionary.newFrom([\line, 3]);
~player.loopMIDI(~sink);
)

// While playback is running, evaluate parameter changes.
~transpose.semitones = 7;
~transpose.semitones = 0;
~transpose.semitones = 12;

// Inspect recorded MIDI messages and the bounded rolling queue.
~sink.messages.postln;
~player.scheduledCount.postln;
~player.activeMIDICount.postln;

// Stop always sends note-offs for active notes.
(
~player.stop;
~sink.messages.postln;
)

// For a real MIDI device, replace the sink:
MIDIClient.init;
m = MIDIOut.new(0);
m.latency = 0;
~player = VoxPlayer.new(~transpose, TempoClock.new(2));
~player.loopMIDI(m);
