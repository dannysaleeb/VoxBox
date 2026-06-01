// Named frozen ideas and a metre-aware arrangement timeline.
// Evaluate one parenthesized block at a time.

(
~map = MetreMap.new;
~map.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4], 960)));

~event = { |start, dur, note, channel = 0|
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: 90,
		channel: channel,
		position: TimeConverter.ticksToPos(start, ~map)
	)
};

~opening = Box.new([
	~event.(480, 480, 60),
	~event.(960, 480, 62),
	~event.(1440, 960, 67)
], ~map, \melody);

~transpose = CTransposer(7);
~opening >>> ~transpose;

~bank = VoxBank(\ideas);
~opening >>= ~bank.slot(\plain);
~transpose >>= ~bank.slot(\upAFifth);
)

// Bank entries are copied frozen values.
~bank.keys.postln;
~bank.at(\upAFifth).events.collect(_[\midinote]).postln;

(
~arr = VoxArrangement(~map, \piece);

// The first source onset is normalised onto each requested region start.
~bank.at(\plain) >>= ~arr.slot(\openingA, Pos(0), anchor: \ticks);
~bank.at(\upAFifth) >>= ~arr.slot(\answerA, Pos(1), mode: \overlay, anchor: \pos);

~arr.out.at(\melody).events.collect({ |event|
	[event[\absTime], event[\midinote]]
}).postln;
)

// Position anchors follow metre edits; tick anchors keep elapsed placement.
(
~threeFour = MetreMap.new;
~threeFour.add(MetreRegion(0, Metre([1, 1, 1], [4, 4, 4], 960)));
~arr.metremap = ~threeFour;
~arr.at(\openingA).resolvedStart.postln;
~arr.at(\answerA).resolvedStart.postln;
)

// Timeline editing is explicit and undoable at chosen musical checkpoints.
(
~arr.commit(\threeFourDraft);
~arr.move(\answerA, Pos(2));
~arr.commit(\laterAnswer);
~arr.undo;
~arr.redo;
)

// Gather only when you want a mutable local copy.
~arr >>> BoxMulti.new >>= \editableArrangement;

// Or play the live arrangement directly with the rolling MIDI scheduler.
(
~sink = VoxRecordingMIDIOut.new;
~player = VoxPlayer.new(~arr, TempoClock.new(2));
~player.loopMIDI(~sink);
)

~arr.move(\answerA, Pos(3)); // future unscheduled onsets refresh
~player.stop;
