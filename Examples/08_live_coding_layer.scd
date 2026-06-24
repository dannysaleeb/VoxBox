// Live-coding layer: handles, taps, forks, masks and fragments.
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
	~event.(0, 480, 60),
	~event.(480, 480, 62),
	~event.(960, 480, 64),
	~event.(1440, 480, 67)
], ~map, \line);

~sink = VoxRecordingMIDIOut.new;
~clock = TempoClock.new(4);
)

// Handles name patch nodes. They do not change the musical voice label.
(
~line
>>> (Granulator([2], depth: 1) @ \grain)
>>> (HarmonyMask(root: 60) @ \harm)
>>= \chain;

~chain[\grain].depth = 2;
~chain.node(\harm).root = 62;
~chain.out.label.postln;
)

// Audition helpers wrap VoxPlayer and return it for stopping or inspection.
(
~player = ~chain.loopMIDI(~sink, ~clock);
~player.stop;
~sink.messages.postln;
)

// VoxOut taps pass material through unchanged while registering named players.
(
~line
>>> (Granulator([2], depth: 1) @ \grain)
>>> VoxOut.loopMIDI(\grains, ~sink, ~clock).on
>>> (HarmonyMask(root: 60) @ \harm)
>>> VoxOut.loopMIDI(\harm, ~sink, ~clock).off
>>= \tappedChain;

~controls = VoxOutControls.new;
~controls.map(\cmd1, \grains, \pause);
~controls.map(\cmd2, \grains, \resume);
~controls.map(\cmd3, \harm, \on);

~tappedChain.out.events.collect(_[\midinote]).postln;
~controls.trigger(\cmd1);
~controls.trigger(\cmd2);
~controls.trigger(\cmd3);
~controls.stop(\all);
)

// Fork duplicates the same source into independent branches.
(
~fork = ~line >>< [
	\a -> ((VoxCanoniser(1, [\aVoice], [Pos(0)], \canonA) @ \canonA)
		>>> VoxOut.loopMIDI(\a, ~sink, ~clock).off),
	\b -> ((VoxCanoniser(1, [\bVoice], [Pos(0)], \canonB) @ \canonB)
		>>> VoxOut.loopMIDI(\b, ~sink, ~clock).off)
];

~fork[\a][\canonA].numVoices.postln;
~fork.out.keys.postln;
)

// Masks are playback contribution filters, separate from Box highlight.
(
~masked =
~dux
>>> VoxMask(Fragments(\quarter)[0, 2]).atRange([Pos(1), Pos(2)])
>>> VoxOut.loopMIDI(\masked, ~sink, ~clock).on
>>= \maskedChain;

~fork[\a].mask(Eighths[0, 2]);
~fork[\b].mask(Fragments(\eighth)[1, 3]);
~fork[\a].out.at(\aVoice).events.collect(_[\absTime]).postln;
~fork[\b].out.at(\bVoice).events.collect(_[\absTime]).postln;
)

// Hocket helpers assign complementary masks; manual masks can still override.
(
~fork.hocket(\a, \b, by: Fragments(\eighth));
~fork[\a].out.at(\aVoice).events.collect(_[\absTime]).postln;
~fork[\b].out.at(\bVoice).events.collect(_[\absTime]).postln;
)
