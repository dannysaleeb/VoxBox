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

~voices = 50;
~names = ~voices.collect(_.asSymbol);
~positions = ~voices.collect {|i| Pos(0, i, i, i*50)};

~test_canoniser = VoxCanoniser.new(~voices, ~names, ~positions, \test);

~hc_canoniser = VoxCanoniser.new(3, [\bass, \tenor, \treble], [Pos(0), Pos(division: 2), Pos(beat: 1)], \handle_with_care);


~tenor_transposer = VoxTransposer.new(5, \tenorUp5);
~treble_transposer = VoxTransposer.new(12, \trebleUp12);

~transposer_down2 = VoxTransposer.new(-2, \down2);
)

(
// tighter, short-form, single-line patch for canon (the aim)
~hc_dux
>>> ~hc_canoniser
>>@ [
	\tenor <<< ~tenor_transposer,
	\treble <<< ~treble_transposer
]
>>+ \bass
>>= \complete_canon
)


(
~hc_dux >>/ [Pos(3), Pos(4)] >>> ~transposer_down2 >>= ~hc_dux
)

z = ~hc_dux >>/ [Pos(1), Pos(2)] >>> ~transposer_down2 >>= ~hc_dux

t = TempoClock.new(1);

y = VoxPlayer.new(~complete_canon, t);

y.loopMIDI(m, 4);

t.tempo =



\hello

~new_clip = ~hc_dux >>/ [Pos(1), Pos(2)]

z = VoxPlayer.new(~complete_canon);
z.loopMIDI(m)

~hc_dux.highlight(Pos(0), Pos(5))
