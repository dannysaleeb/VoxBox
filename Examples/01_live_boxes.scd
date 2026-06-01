// Live chains, clipping and explicit snapshot gathering.
// Evaluate one parenthesized block at a time.

(
~map = MetreMap.new;
~map.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4], 960)));

~event = { |start, dur, note, velocity = 90, channel = 0|
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: velocity,
		channel: channel,
		position: TimeConverter.ticksToPos(start, ~map)
	)
};

~source = Box.new([
	~event.(0, 960, 60),
	~event.(960, 960, 62),
	~event.(1920, 960, 64),
	~event.(2880, 960, 67)
], ~map, \source);

~transpose = CTransposer(0);
~source >>> ~transpose;
~excerpt = ~transpose >>/ [960, 2880];
)

// Both nodes remain live.
~transpose.out.events.collect(_[\midinote]).postln;
~excerpt.out.events.collect(_[\midinote]).postln;

// Parameter edits invalidate the rendered graph automatically.
~transpose.semitones = 7;
~excerpt.out.events.collect(_[\midinote]).postln;

// Gather the current excerpt when you want editable local material.
(
~excerpt >>> Box.new >>= \editableExcerpt;
~editableExcerpt.out.events.collect(_[\midinote]).postln;
)

// The gathered Box no longer follows upstream parameter edits.
~transpose.semitones = 12;
~excerpt.out.events.collect(_[\midinote]).postln;
~editableExcerpt.out.events.collect(_[\midinote]).postln;

// Direct event-array edits are allowed; call touch afterward.
(
~editableExcerpt.events.first[\midinote] = 48;
~editableExcerpt.touch;
~editableExcerpt.out.events.collect(_[\midinote]).postln;
)
