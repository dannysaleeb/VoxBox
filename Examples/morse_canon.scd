// Morse canon sketch.
//
// Assumes ~dux has already been loaded, and m is a MIDIOut.
// Evaluate one parenthesized block at a time.
(
MIDIClient.init;
m = MIDIOut.new(0);
m.latency = 0
)

~dux = Box.fromMIDIPath("/Users/dannysaleeb/Library/Mobile Documents/com~apple~CloudDocs/Projects/Lockton Canons/meeting place/midi/mp_dux_3.6.26.mid", \dux)

(
~clock = ~clock ? TempoClock.new(1);
~morseUnit = \eighth; // one Morse time unit: a quaver.

~canons = ~dux >>< [
	\original -> ( // dah: both parts speak on the beat.
		VoxCanoniser.voices(2).names(\originalTenor, \originalBass).offsets(Pos(0), Pos(0, 2))
	),

	\allminusquaver -> ( // offbeat dah: both parts shifted earlier.
		VoxCanoniser.voices(2).names(\allminusquaverTenor, \allminusquaverBass).offsets(Pos(0), Pos(0, 2))
		>>> VoxShifter.earlier(Pos(division: 2)).atRange(~dux.getRange)
	),

	\allplusquaver -> ( // offbeat dah: both parts shifted later.
		VoxCanoniser.voices(2).names(\allplusquaverTenor, \allplusquaverBass).offsets(Pos(0), Pos(0, 2))
		>>> VoxShifter.later(Pos(division: 2)).atRange(~dux.getRange)
	),

	\iiplusquaver -> ( // dit movement: second part enters a quaver later.
		VoxCanoniser.voices(2).names(\iiplusquaverTenor, \iiplusquaverBass).offsets(Pos(0), Pos(0, 2, 2))
	),

	\iiminusquaver -> ( // dit movement: second part enters a quaver earlier.
		VoxCanoniser.voices(2).names(\iiminusquaverTenor, \iiminusquaverBass).offsets(Pos(0), Pos(0, 1, 2))
	),

	\iipluscrotchet -> (
		VoxCanoniser.voices(2).names(\iipluscrotchetTenor, \iipluscrotchetBass).offsets(Pos(0), Pos(0, 3))
	),

	\iiminuscrotchet -> (
		VoxCanoniser.voices(2).names(\iiminuscrotchetTenor, \iiminuscrotchetBass).offsets(Pos(0), Pos(0, 1))
	)
];
)

// Paper-strip splice helpers.
//
// Splice specs use beat numbers: 1 is a crotchet, 0.5 is a quaver.
// A spec with no place appends after the previous fragment. A spec with place
// is dropped at that output beat and later appends continue from its end.
(
~quarterTicks = { Fragments(\quarter).unitTicks(~dux.metremap) };

~beatsToTicks = { |beats|
	(beats * ~quarterTicks.()).round.asInteger
};

~beatRangeToTicks = { |beatRange|
	var sourceRange = ~dux.getRange;
	var origin = sourceRange.notNil.if { sourceRange.start } { 0 };
	[
		origin + ~beatsToTicks.(beatRange[0]),
		origin + ~beatsToTicks.(beatRange[1])
	]
};

~voiceRole = { |label|
	var text = label.asString;
	(text.find("Bass").notNil or: { text.find("bass").notNil }).if {
		\bass
	} {
		\tenor
	}
};

~shiftEvents = { |events, delta, metremap|
	events.collect { |event|
		var shifted = event.deepCopy;
		shifted[\absTime] = event[\absTime] + delta;
		shifted[\position] = TimeConverter.ticksToPos(shifted[\absTime], metremap);
		shifted
	}
};

~canonVoices = { |key, spliceIndex|
	var branch = ~canons.branch(key);
	var source;
	var voices = nil;

	if (branch.isNil) {
		"Missing canon branch % at splice %.".format(key, spliceIndex).warn;
	} {
		source = branch.out;
		if (source.isNil) {
			"Canon branch % rendered nil at splice %.".format(key, spliceIndex).warn;
		} {
			if (source.isKindOf(Vox)) {
				voices = [source];
			} {
				if (source.isKindOf(VoxMulti)) {
					voices = source.asArray;
				} {
					"Canon branch % rendered %, expected Vox or VoxMulti.".format(key, source.class).warn;
				};
			};
		};
	};

	voices
};

~assembleSplices = { |splices|
	var map = ~dux.metremap;
	var cursor = 0.0;
	var outputEvents = (
		tenor: List.new,
		bass: List.new
	);

	splices.do { |splice, index|
		var branchKey = splice[\canon];
		var voices, take, sourceTicks, takeDur, placeBeat, placeTick, delta;

		voices = ~canonVoices.(branchKey, index);
		if (voices.isNil) {
			"Skipping splice %.".format(index).warn;
		} {
			take = splice[\take];
			if (take.isNil or: { take.size != 2 }) {
				"Splice % needs take: [startBeat, endBeat].".format(index).warn;
			} {
				sourceTicks = ~beatRangeToTicks.(take);
				takeDur = take[1] - take[0];
				placeBeat = splice[\place] ? cursor;
				placeTick = ~beatsToTicks.(placeBeat);
				delta = placeTick - sourceTicks[0];

				voices.do { |voice|
					var role = ~voiceRole.(voice.label);
					var clipped = Box.clippedEvents(voice.events, sourceTicks);
					var shifted = ~shiftEvents.(clipped, delta, map);
					outputEvents[role].addAll(shifted);
				};

				cursor = placeBeat + takeDur;
			};
		};
	};

	VoxMulti.new([
		Vox.new(outputEvents[\tenor].asArray.sortBy(\absTime), map, \tenor, (splices: splices), ~canons),
		Vox.new(outputEvents[\bass].asArray.sortBy(\absTime), map, \bass, (splices: splices), ~canons)
	], map, \morseCanon, (splices: splices), ~canons)
};
)

// Optional sanity check: confirm each canon branch renders before assembling.
(
~debugCanons = {
	~canons.keys.asArray.sort { |left, right| left.asString < right.asString }.do { |key|
		var voices = ~canonVoices.(key, \debug);
		if (voices.notNil) {
			"% -> %".format(key, voices.collect(_.label)).postln;
		};
	};
};

~debugCanons.();
)

// First sketch: hand-cut and append fragments from the canon palette.
(
~sketchSplices = [
	(canon: \original, take: [0, 1]),
	(canon: \iiplusquaver, take: [1, 2]),
	(canon: \iiminusquaver, take: [2, 3]),
	(canon: \iipluscrotchet, take: [3, 4])
];

~morseCanon = ~assembleSplices.(~sketchSplices);
~morseCanon.keys.postln;
)

// Example word: "meeting"
//
// Morse:
// m = dah dah
// e = dit
// e = dit
// t = dah
// i = dit dit
// n = dah dit
// g = dah dah dit
//
// Each row cuts a source range from one canon version. When place is omitted,
// the fragment appends immediately after the previous fragment.
(
~meetingSplices = [
	// M: dah dah
	(letter: \m, canon: \original, take: [0, 2]),

	// E + E: two neighbouring dits squeezed into one crotchet.
	(letter: \e_e, canon: \iiplusquaver, take: [2, 3]),

	// T: dah
	(letter: \t, canon: \original, take: [3, 4]),

	// I: dit dit
	(letter: \i, canon: \iiplusquaver, take: [4, 5]),

	// N: dah dit
	(letter: \n, canon: \original, take: [5, 6]),
	(letter: \n, canon: \iiplusquaver, take: [5, 5.5]),

	// G: offbeat dah dah dit
	(letter: \g, canon: \iiminuscrotchet, take: [6, 8.5]),

	// and again GOOD UP TO HERE?
	(letter: \m, canon: \iiminuscrotchet, take: [9, 12]),

	// E + E: two neighbouring dits squeezed into one crotchet.
	(letter: \e_e, canon: \iiminuscrotchet, take: [12, 12.5]),
	(letter: \e, canon: \iiminusquaver, take: [12.5, 13]),

	// T: dah
	(letter: \t, canon: \iiminuscrotchet, take: [12, 13]),

	// I: dit dit
	(letter: \i, canon: \iiminusquaver, take: [13, 14]),

	// N: dah dit
	(letter: \n, canon: \original, take: [14, 15]),
	(letter: \n, canon: \iiminusquaver, take: [15, 15.5]),

	// G: offbeat dah dah dit
	(letter: \g, canon: \allminusquaver, take: [15.5, 16.5]),
	(letter: \g, canon: \allminusquaver, take: [16.5, 17.5]),
	(letter: \g, canon: \allminusquaver, take: [17.5, 18]),
];

~morseCanon = ~assembleSplices.(~meetingSplices);
~morseCanon.keys.postln;
)

// Inspect the assembled paper-strip canon.
(
~inspectMorseCanon = { |material|
	var allEvents, lastEnd, totalBeats;

	if (material.isNil) {
		"~morseCanon is nil. Evaluate the canon palette, helper block, then a splice block first.".warn;
	} {
		"Voice labels: %".format(material.keys.asArray.sort { |left, right| left.asString < right.asString }).postln;
		material.asArray.do { |voice|
			"% first events [beat, dur]: %".format(
				voice.label,
				voice.events.copyRange(0, 19).collect { |event|
					[
						event[\absTime] / ~quarterTicks.(),
						event[\dur] / ~quarterTicks.()
					]
				}
			).postln;
		};

		allEvents = material.asArray.collect(_.events).flat;
		lastEnd = allEvents.isEmpty.if {
			0
		} {
			allEvents.collect { |event| event[\absTime] + event[\dur] }.maxItem
		};
		totalBeats = lastEnd / ~quarterTicks.();
		"Total span in beats: %".format(totalBeats).postln;
	};
};

~inspectMorseCanon.(~morseCanon);
)

// Audition the assembled paper-strip canon.
(
if (~player.notNil) { ~player.stop };
~player = VoxPlayer.new(~morseCanon, TempoClock(1));
~player.channelMap = (
	tenor: 0,
	bass: 1
);
~player.loopMIDI(m, 4);
)

// Stop audition.
(
if (~player.notNil) {
	~player.stop;
	~player = nil;
};
)
