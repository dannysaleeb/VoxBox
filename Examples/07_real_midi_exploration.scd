// VoxBox real MIDI exploration notebook.
//
// Purpose:
// - Use Examples/hc_orig.mid as real imported material.
// - Send MIDI to MIDIOut.new(0), assumed to be IAC Driver.
// - Explore live chains, snapshot boundaries, control handles, rebuilding chains,
//   routing, random/seeded processing, VoxBank, and VoxArrangement.
//
// Usage:
// - Recompile the SuperCollider class library after changing VoxBox classes.
// - Open this file in SuperCollider.
// - Evaluate one parenthesized block at a time.
// - Keep Logic listening to IAC Driver.
//
// Safety:
// - The stop block below sends note-offs for active notes.
// - Most blocks reuse ~player; stop it before starting a different source.

////////////////////////////////////////////////////////////
// 0. Bootstrapping: MIDI, source path, and small helpers. //
////////////////////////////////////////////////////////////

(
MIDIClient.init;
~m = MIDIOut.new(0);
~m.latency = 0;

~clock = TempoClock.new(2); // 2 beats/sec. Change while stopped if needed.

~exampleDir = thisProcess.nowExecutingPath.notNil.if {
	thisProcess.nowExecutingPath.dirname
} {
	"/Users/dannysaleeb/dev/supercollider/VoxBoxCodex/Examples"
};
~midiPath = ~exampleDir +/+ "hc_orig.mid";

~stop = {
	if (~player.notNil) {
		~player.stop;
		~player = nil;
	};
	"Stopped current VoxPlayer.".postln;
};

~notes = { |node, max = 24|
	var out = node.respondsTo(\out).if { node.out } { node };
	if (out.isKindOf(Vox)) {
		^[out.label, out.events.copyRange(0, max - 1).collect(_[\midinote])]
	};
	if (out.isKindOf(VoxMulti)) {
		^out.asArray.collect { |vox|
			[vox.label, vox.events.copyRange(0, max - 1).collect(_[\midinote])]
		}
	};
	^out
};

~summary = { |node|
	var out = node.respondsTo(\out).if { node.out } { node };
	if (out.isKindOf(Vox)) {
		if (out.events.isEmpty) {
			("Vox %, 0 events".format(out.label)).postln;
		} {
			("Vox %, events %, first %, last %"
				.format(out.label, out.events.size, out.events.first[\absTime], out.events.last[\absTime]))
				.postln;
		};
	};
	if (out.isKindOf(VoxMulti)) {
		("VoxMulti %, voices %".format(out.label, out.keys)).postln;
		out.asArray.do { |vox|
			if (vox.events.isEmpty) {
				("  %: 0 events".format(vox.label)).postln;
			} {
				("  %: % events, first %, last %"
					.format(vox.label, vox.events.size, vox.events.first[\absTime], vox.events.last[\absTime]))
					.postln;
			};
		};
	};
	out
};

"MIDI path: %".format(~midiPath).postln;
"Ready. If MIDIOut.new(0) is IAC Driver, Logic should receive notes.".postln;
)

// Emergency stop.
~stop.();


/////////////////////////////////////////
// 1. Import the MIDI as Box/BoxMulti. //
/////////////////////////////////////////

(
~file = SimpleMIDIFile.read(~midiPath);

// Single voice import: useful when you want one mutable source Box.
Box.fromMIDIPath(~midiPath, \dux) >>= \dux;

// Multi-track import: useful when the MIDI file has several note tracks.
// With a one-track file this is still useful as a labelled collection.
~multi = BoxMulti.fromMIDIPath(~midiPath, \hc);

"Single Box summary:".postln;
~summary.(~dux);

"BoxMulti summary:".postln;
~summary.(~multi);
)

// Inspect raw labels and first notes.
(
~multi.keys.postln;
~notes.(~dux, 16).postln;
~notes.(~multi, 16).postln;
)


/////////////////////////////////////////////////////////////
// 2. Play a Box directly: mutable material as live source. //
/////////////////////////////////////////////////////////////

(
~stop.();
~player = VoxPlayer.new(~dux, ~clock);
~player.loopMIDI(~m, 4);
"Playing ~dux directly. Edit the Box, then call touch.".postln;
)

// Direct Box event edits are live once you call touch.
// Future unscheduled onsets refresh; already sounding notes finish naturally.
(
~dux.events.first[\midinote] = ~dux.events.first[\midinote] + 12;
~dux.touch;
~notes.(~dux, 8).postln;
)

// Undo the little octave poke if you want to return to the imported state.
(
~dux.events.first[\midinote] = ~dux.events.first[\midinote] - 12;
~dux.touch;
~notes.(~dux, 8).postln;
)


////////////////////////////////////////////////////////
// 3. Live chain with explicit control-surface knobs. //
////////////////////////////////////////////////////////

(
~stop.();

~transpose = CTransposer(0);
~elongate = Elongator(1.0);
~excerpt = nil;

~chain = ~dux >>> ~transpose >>> ~elongate;
~excerpt = ~chain >>/ [Pos(0), Pos(4)];

~player = VoxPlayer.new(~excerpt, ~clock);
~player.loopMIDI(~m, 4);

"Playing live chain: ~dux >>> ~transpose >>> ~elongate >>/ [Pos(0), Pos(4)]".postln;
"Tweak ~transpose.semitones, ~elongate.factor, or ~excerpt.range.".postln;
)

// Current style: keep module handles and turn their parameters.
~transpose.semitones = 7;
~transpose.semitones = -5;
~transpose.semitones = 0;

// Time-scaling is also live.
~elongate.factor = 0.5;
~elongate.factor = 1.5;
~elongate.factor = 1.0;

// The clip node itself is live too.
~excerpt.range = [Pos(0), Pos(2)];
~excerpt.range = [Pos(1), Pos(5)];
~excerpt.range = [Pos(0), Pos(4)];

// Inspect the rendered output at any time.
~summary.(~excerpt);


//////////////////////////////////////////////////////////////
// 4. Snapshot boundary: gather the current result into Box. //
//////////////////////////////////////////////////////////////

(
// This deposits the current live chain output into a new editable Box.
// After this point, ~frozenExcerpt does not follow ~transpose or ~elongate.
~excerpt >>> Box.new >>= \frozenExcerpt;

"Gathered ~excerpt into ~frozenExcerpt.".postln;
"Live excerpt notes:".postln;
~notes.(~excerpt, 12).postln;
"Frozen excerpt notes:".postln;
~notes.(~frozenExcerpt, 12).postln;
)

// Change the upstream knobs. The live excerpt changes; the gathered Box does not.
(
~transpose.semitones = 12;

"Live excerpt after transpose change:".postln;
~notes.(~excerpt, 12).postln;
"Frozen excerpt after transpose change:".postln;
~notes.(~frozenExcerpt, 12).postln;
)

// You can now play the frozen Box as its own editable source.
(
~stop.();
~player = VoxPlayer.new(~frozenExcerpt, ~clock);
~player.loopMIDI(~m, 4);
"Playing frozen editable Box.".postln;
)

// Edit the frozen material directly; this is now a new stage.
(
~frozenExcerpt.events.do { |ev| ev[\velocity] = 70 };
~frozenExcerpt.events.first[\midinote] = 36;
~frozenExcerpt.touch;
~notes.(~frozenExcerpt, 12).postln;
)


//////////////////////////////////////////////////////////////
// 5. Rebuilding chains with args: Tidal-ish text workflow. //
//////////////////////////////////////////////////////////////

(
~stop.();

// Re-evaluate this whole block with different arguments.
// This is simple and readable, but it creates fresh module objects each time.
// So the player is recreated here too.
~chain2 = ~dux
>>> CTransposer(5)
>>> Elongator(0.75)
>>/ [Pos(0), Pos(3)];

~player = VoxPlayer.new(~chain2, ~clock);
~player.loopMIDI(~m, 4);

"Playing rebuilt chain. Change args and re-evaluate this block.".postln;
)

(
~stop.();

// Another rebuilt version. This style is good for structural edits.
~chain2 = ~dux
>>> DTransposer(2, 60, Scale.at(\ionian))
>>> ModeMap(Scale.at(\ionian), 60, Scale.at(\minorPentatonic), 57)
>>/ [Pos(1), Pos(5)];

~player = VoxPlayer.new(~chain2, ~clock);
~player.loopMIDI(~m, 4);

"Playing rebuilt diatonic/modal chain.".postln;
)


//////////////////////////////////////////////////////////
// 6. Canoniser and labelled voice routing through >>@. //
//////////////////////////////////////////////////////////

(
~stop.();

~canon = VoxCanoniser(
	3,
	[\bass, \tenor, \treble],
	[Pos(0), Pos(division: 2), Pos(beat: 1)],
	\canon
);

~tenorTranspose = CTransposer(7);
~trebleTranspose = CTransposer(12);

~canonChain = ~dux >>> ~canon
>>@ [
	\bass,
	\tenor <<< ~tenorTranspose,
	\treble <<< ~trebleTranspose
];

~player = VoxPlayer.new(~canonChain, ~clock);
~player.channelMap = Dictionary.newFrom([
	\bass, 0,
	\tenor, 1,
	\treble, 2
]);
~player.loopMIDI(~m, 4);

"Playing canon routed to MIDI channels 0, 1, 2.".postln;
~summary.(~canonChain);
)

// Tweak routed module handles.
~tenorTranspose.semitones = -12;
~tenorTranspose.semitones = 7;
~trebleTranspose.semitones = 19;
~trebleTranspose.semitones = 12;

// Change the canon offsets live.
(
~canon.namesToOffsetsDict = [
	[\bass, \tenor, \treble],
	[Pos(0), Pos(beat: 1), Pos(beat: 2)]
].lace.asDict;
~summary.(~canonChain);
)

// Select one labelled voice from a live multi-voice chain.
(
~stop.();
~tenorOnly = ~canonChain >>* \tenor;
~player = VoxPlayer.new(~tenorOnly, ~clock);
~player.loopMIDI(~m, 4);
"Playing live selected tenor voice only.".postln;
)


///////////////////////////////////////////////////////
// 7. Random processing: cached, seeded, and reroll. //
///////////////////////////////////////////////////////

(
~stop.();

~rubato = RandElong(0.65, 1.35, 1234);
~rubatoChain = ~dux >>> ~rubato >>/ [Pos(0), Pos(4)];

~player = VoxPlayer.new(~rubatoChain, ~clock);
~player.loopMIDI(~m, 4);

"Playing seeded random elongation. It is stable until reroll/seed changes.".postln;
)

// Repeated reads are stable because the module output is cached by revision.
~rubato.out.events.collect(_[\dur]).copyRange(0, 11).postln;
~rubato.out.events.collect(_[\dur]).copyRange(0, 11).postln;

// Deliberate redraw with the same seed.
(
~rubato.reroll;
~rubato.out.events.collect(_[\dur]).copyRange(0, 11).postln;
)

// Deliberate new random world.
(
~rubato.seed = 5678;
~rubato.reroll;
~rubato.out.events.collect(_[\dur]).copyRange(0, 11).postln;
)

// Optional if RTProbMap/rtdivide support is available in your image.
(
~stop.();
~gran = Granulator([2, 3], RTProbMap.new, 2, 4321);
~mask = HarmonyMask(60, Scale.at(\dorian), 12, 4321);
~grainChain = ~dux >>> ~gran >>> ~mask >>/ [Pos(0), Pos(3)];

~player = VoxPlayer.new(~grainChain, ~clock);
~player.loopMIDI(~m, 4);
"Playing granulated/harmony-masked line.".postln;
)

// HarmonyMask knobs.
~mask.root = 57;
~mask.scale = Scale.at(\minorPentatonic);
~mask.randomness = 0.5;
~mask.preserveOnsets = false;
~mask.preserveOnsets = true;


/////////////////////////////////////////////////
// 8. VoxBank: named frozen musical snapshots. //
/////////////////////////////////////////////////

(
~bank = VoxBank(\ideas);

// Deposit the current state of live sources.
~dux >>= ~bank.slot(\plainImport);
~excerpt >>= ~bank.slot(\liveExcerptAtDepositTime);
~canonChain >>= ~bank.slot(\canonIdea);
~rubatoChain >>= ~bank.slot(\rubatoIdea);

~bank.keys.postln;
)

// Bank values are copied frozen snapshots.
(
~transpose.semitones = -12;

"Live excerpt now:".postln;
~notes.(~excerpt, 8).postln;

"Banked excerpt is unchanged:".postln;
~notes.(~bank.at(\liveExcerptAtDepositTime), 8).postln;
)

// Replace deliberately when the slot should become a new snapshot.
(
~excerpt >>= ~bank.replaceSlot(\liveExcerptAtDepositTime);
~notes.(~bank.at(\liveExcerptAtDepositTime), 8).postln;
)

// Play a banked snapshot.
(
~stop.();
~player = VoxPlayer.new(~bank.at(\canonIdea), ~clock);
~player.channelMap = Dictionary.newFrom([
	\bass, 0,
	\tenor, 1,
	\treble, 2
]);
~player.loopMIDI(~m, 4);
"Playing frozen bank slot \\canonIdea.".postln;
)

// Provenance is descriptive recipe data, not a live patch cable.
~bank.postProvenance(\canonIdea);


//////////////////////////////////////////////////////////////
// 9. VoxArrangement: metre-aware timeline of frozen ideas. //
//////////////////////////////////////////////////////////////

(
~stop.();

~arr = VoxArrangement(~dux.metremap, \piece);

// Bank snapshots placed into an arrangement become copied regions.
~bank.at(\plainImport) >>= ~arr.slot(\opening, Pos(0), \overlay, \ticks);
~bank.at(\canonIdea) >>= ~arr.slot(\canonA, Pos(4), \overlay, \pos);
~bank.at(\rubatoIdea) >>= ~arr.slot(\rubatoAnswer, Pos(8), \interweave, \pos);

~summary.(~arr);
~arr.keys.postln;
)

// Play the arrangement live. Moving regions updates future unscheduled onsets.
(
~player = VoxPlayer.new(~arr, ~clock);
~player.channelMap = Dictionary.newFrom([
	\dux, 0,
	\bass, 1,
	\tenor, 2,
	\treble, 3
]);
~player.loopMIDI(~m, 4);
"Playing live VoxArrangement.".postln;
)

// Move a region while playback is running.
~arr.move(\canonA, Pos(2));
~arr.move(\canonA, Pos(6));

// Replace a region deliberately with a new frozen snapshot.
(
~trebleTranspose.semitones = 24;
~arr.replace(\canonA, Pos(6), ~canonChain, \overlay, \pos);
~summary.(~arr);
)

// Arrangement history is explicit.
(
~arr.commit(\canonHigh);
~arr.move(\rubatoAnswer, Pos(12));
~arr.commit(\laterRubato);
~arr.undo;
~arr.redo;
)

// Tick anchors keep elapsed placement; pos anchors follow metre-map edits.
(
~threeFour = MetreMap.new;
~threeFour.add(MetreRegion(0, Metre([1, 1, 1], [4, 4, 4], ~dux.metremap.tpqn)));
~arr.metremap = ~threeFour;

"Region starts after 3/4 metre map:".postln;
~arr.keys.do { |key| [key, ~arr.at(key).resolvedStart].postln };
)


//////////////////////////////////////////////////////
// 10. Gather arrangement output for local editing. //
//////////////////////////////////////////////////////

(
~arr >>> BoxMulti.new >>= \editablePiece;

"Gathered arrangement into ~editablePiece.".postln;
~summary.(~editablePiece);
)

// Edit one gathered voice directly.
(
~editableFirstKey = ~editablePiece.keys.first;
~editableVoice = ~editablePiece.at(~editableFirstKey);
~editableVoice.events.do { |ev| ev[\velocity] = 60 };
~editablePiece.touch;

"Edited gathered voice %.".format(~editableFirstKey).postln;
)

// Play the gathered BoxMulti. It no longer follows arrangement moves.
(
~stop.();
~player = VoxPlayer.new(~editablePiece, ~clock);
~player.loopMIDI(~m, 4);
"Playing gathered editable BoxMulti.".postln;
)

(
~arr.move(\opening, Pos(4));
"Arrangement moved, but ~editablePiece is unchanged.".postln;
~summary.(~editablePiece);
)


///////////////////////////////////////////////////////////////
// 11. A practical live session pattern with today's system. //
///////////////////////////////////////////////////////////////

(
~stop.();

// Stable handles for controls you want to perform.
~liveT = CTransposer(0);
~liveD = DTransposer(0, 60, Scale.at(\ionian));
~liveE = Elongator(1.0);

// A stable source expression for playback.
~livePatch = ~dux
>>> ~liveT
>>> ~liveD
>>> ~liveE
>>/ [Pos(0), Pos(4)];

~player = VoxPlayer.new(~livePatch, ~clock);
~player.loopMIDI(~m, 4);

"A stable live patch is playing. Use the short control lines below.".postln;
)

// Control surface lines.
~liveT.semitones = 7;
~liveT.semitones = 0;
~liveD.degrees = 2;
~liveD.degrees = -1;
~liveE.factor = 0.75;
~liveE.factor = 1.25;
~livePatch.range = [Pos(0), Pos(2)];
~livePatch.range = [Pos(2), Pos(6)];

// Freeze whatever you currently like.
(
~livePatch >>= ~bank.replaceSlot(\liveExcerptAtDepositTime);
~bank.at(\liveExcerptAtDepositTime) >>= ~arr.slot(\newLiveTake, Pos(16), \overlay, \pos);
"Current live patch deposited into bank and arrangement.".postln;
)

// Clean stop.
~stop.();
