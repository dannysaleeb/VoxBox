(
MIDIClient.init;
m = MIDIOut.new(0);
m.latency = 0;

f = SimpleMIDIFile.read("/Users/dannysaleeb/Library/CloudStorage/Dropbox/universe/work/Lockton Canons/HC/hc_orig.mid");

// ~arrangment = VoxArrangement.new
)

//////////////////////
// Handle with Care //
//////////////////////
//      CANON 1     //
//////////////////////

(
// material & modules
~hc_dux = Vox.fromMIDI(f, label: \hc_dux);
~hc_canoniser = VoxCanoniser.new(3, [\bass, \tenor, \treble], [Pos(0), Pos(division: 2), Pos(beat: 1)], \handle_with_care);


~tenor_transposer = VoxTransposer.new(5, \tenorUp5);
~treble_transposer = VoxTransposer.new(12, \trebleUp12);
)

(
~hc_dux
>>> ~canoniser
>>= \canon // VoxMulti
>>@ [
	\tenor <<< ~tenor_transposer,
	\treble <<< ~treble_transposer
]
>>+ ~canon
>>= \canon
)



// are there situations where I'd want to put assign a chain? I suppose ... but in that case, I can do it with the normal assignment operator ...
// e.g.

~transposition = ~tenor_transposer >>> ~treble_transposer

(
// tighter, short-form, single-line patch for canon (the aim)
~hc_dux
>>> ~hc_canoniser
>>= \canon
>>@ [\tenor <<< ~tenor_transposer, \treble <<< ~treble_transposer]
>>= \routes
>>+ ~canon
>>= \canon
)

~hc_canoniser.out

~routes.voxes[\tenor]

// ok, now about chaining the inputs as necessary ... look at this next ...

~canon.out.asArray[0].source
~routes[\treble].input

z = VoxPlayer.new(~routes)

z.loopMIDI(m);

~hc_dux.highlight(Pos(0), Pos(0,1))

~hc_dux.highlighted

VoxMulti.fromPlugMulti(~canon.out).voxes.values

~result.voxes.values

a = VoxMulti.fromPlugMulti(~canon.out)

a.merge(~result)

>>! // assign as Vox or VoxMulti ... ??

~canon[\treble].events

// I think might need to reconstruct Vox from plug, basically ...

(
// PLAYBACK
~player = VoxPlayer.new(~hc_transposeTreble);
~player.playMIDI(m);
)

(
// PLAYGROUND (init modules for play)
var subdivider, glom, wordProcessor, inverter, flipper, transposer;
// live update / edit while looping (the ultimate aim)
// this section allows me to play live and is a live composing/coding environment
~hc_dux >>/ [Pos(1), Pos(3)] >>> subdivider >>= ~hc_dux
)

>>==>

// would need a section for committing to arrangement ... 