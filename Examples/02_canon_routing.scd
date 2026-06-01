// Live multi-voice canon routing and labelled selection.
// Evaluate 01_live_boxes.scd first, or run the setup block below.

(
if (~source.isNil) {
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
};

~canoniser = VoxCanoniser(
	3,
	[\bass, \tenor, \treble],
	[Pos(0), Pos(beat: 1), Pos(beat: 2)],
	\canon
);
~tenorTranspose = CTransposer(7);
~trebleTranspose = CTransposer(12);

~canon = ~source >>> ~canoniser >>@ [
	\bass,
	\tenor <<< ~tenorTranspose,
	\treble <<< ~trebleTranspose
];

~liveTenor = ~canon >>* \tenor;
)

~canon.out.keys.postln;
~canon.out.at(\bass).events.collect(_[\midinote]).postln;
~liveTenor.out.events.collect(_[\midinote]).postln;

// Routed parameter edits are audible on the next render or unscheduled onset.
~tenorTranspose.semitones = -5;
~liveTenor.out.events.collect(_[\midinote]).postln;

// Gather a multi-voice editing checkpoint explicitly.
(
~canon >>> BoxMulti.new >>= \canonSnapshot;
~canonSnapshot.out.keys.postln;
~canonSnapshot.out.at(\tenor).events.collect(_[\midinote]).postln;
)
