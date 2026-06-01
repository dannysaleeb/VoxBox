// Replayable random transformations.
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
	~event.(0, 960, 60),
	~event.(960, 960, 62),
	~event.(1920, 960, 64),
	~event.(2880, 960, 67)
], ~map, \line);

~rubato = RandElong(0.6, 1.5, 1234);
~line >>> ~rubato;
)

// Repeated reads are stable because module output is cached by revision.
~rubato.out.events.collect(_[\dur]).postln;
~rubato.out.events.collect(_[\dur]).postln;

// A seeded reroll rebuilds the render reproducibly.
~rubato.reroll;
~rubato.out.events.collect(_[\dur]).postln;

// Change or clear the seed when you deliberately want a different render.
(
~rubato.seed = 5678;
~rubato.reroll;
~rubato.out.events.collect(_[\dur]).postln;
)

// Optional, when RTProbMap and rtdivide are installed:
// ~gran = Granulator([2, 3], RTProbMap.new, 2, 1234);
// ~mask = HarmonyMask(60, Scale.at(\ionian), 12, 1234);
// ~line >>> ~gran >>> ~mask;
// ~mask.out.events.collect(_[\midinote]).postln;
