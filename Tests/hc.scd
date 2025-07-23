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
~hc_dux = Vox.new(f, label: \hc_dux);
~hc_canoniserModule = VoxCanoniser.new(3, [\bass, \tenor, \treble], [Pos(0), Pos(division: 2), Pos(beat: 1)], \handle_with_care);


~hc_transposeTenor = VoxTransposer.new(5, \tenorUp5);
~hc_transposeTreble = VoxTransposer.new(12, \trebleUp12);
)

(
// Long-form patch for canon
~hc_dux
>>> ~hc_canoniserModule
>>= ~hc_canon;

~hc_canon
>>* (\tenor, [1])
>>> ~hc_tranposeTenor
>>= ~tenor_tranposed;

~hc_canon
>>* (\treble, [2])
>>> ~hc_tranposeTreble
>>= ~treble_tranposed;

~hc_canon
>>* (\bass, [0]) >>+ ~tenor_tranposed >>+ ~treble_tranposed
>>= (~hc_canon = VoxMulti.new);
)

(
// tighter, short-form, single-line patch for canon (the aim)
~hc_dux
>>> ~hc_canoniserModule
>>= ~canon
>>< [\trebleAndTenor, [\treble, \tenor]]
>>@ [\trebleAndTenor <<< (~hc_transpose5 >>> ~inverter), ~canon[\bass] <<< ~hc_transpose12]
>>+ ~canon
>>= ~canon
) // my aim for right now is basically just to make this function

>>@ [\tenor <<< (~hc_transpose5 >>> ~inverter), \treble <<< ~hc_transpose12]

(
// PLAYBACK
~player = VoxPlayer.new(~canon);
~player.playMIDI(m);
)

(
// PLAYGROUND (init modules for play)
var subdivider, glom, wordProcessor, inverter, flipper, transposer;
// live update / edit while looping (the ultimate aim)
// this section allows me to play live and is a live composing/coding environment
~hc_dux >>/ [Pos(1), Pos(3)] >>> subdivider >>= ~hc_dux
)

// would need a section for committing to arrangement ... 