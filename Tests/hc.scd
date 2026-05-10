(
MIDIClient.init;
m = MIDIOut.new(0);
m.latency = 0;

f = SimpleMIDIFile.read("/Users/dannysaleeb/Library/CloudStorage/Dropbox/universe/work/Lockton Canons/HC/hc_orig.mid");
)

//////////////////////
// Handle with Care //
//////////////////////
//      CANON 1     //
//////////////////////

(
// SETUP //

// MATERIALS
Box.fromMIDI(f, \dux) >>= \dux;

// MODULES
~canoniser = VoxCanoniser(3, [\bass, \tenor, \treble], [Pos(0), Pos(division: 2), Pos(beat: 1)], \canon);
~ctranspose_tenor = CTransposer(5);
~ctranspose_treble = CTransposer(12);
)

~ctranspose_tenor.semitones = 2;
~ctranspose_treble.semitones = 12;

(
~canon_chain = ~dux >>> ~canoniser

>>@ [\tenor <<< ~ctranspose_tenor, \treble <<< ~ctranspose_treble, \bass]
)

(
~canon_chain >>> BoxMulti.new >>= \canon_one; // snapshot gather: can be edited or routed further after gathering
)

/*>>@ [
	\bass <<< VoxCanoniser(2, [\bass1, \bass2], [Pos(0), Pos(1)]),
	\tenor <<< VoxCanoniser(2, [\tenor1, \tenor2], [Pos(0), Pos(beat: 1)]),
	\treble <<< VoxCanoniser(2, [\treble1, \treble2], [Pos(0), Pos(division: 2)])
]

>>> BoxMulti.new >>= \canon_two // another gathering point (further routing possible)

>>@ [\bass1, \bass2] >>> BoxMulti.new >>= \basscanon*/

// tweak params & edit clips
~dux >>/ [Pos(1), Pos(2)] >>> DTransposer(3) >>= ~dux

t = TempoClock.new(1, queueSize: 100000);
z = VoxPlayer.new(~canon_chain, t); // live chain playback: module parameter changes apply next loop
z.loopMIDI(m, 4);

u = TempoClock.new(1, queueSize: 100000);
y = VoxPlayer.new(~canon_one.out.at(\tenor), t);
y.loopMIDI(m, 4)

x = TempoClock.new(1, queueSize: 100000);
w = VoxPlayer.new(~canon_one.out.at(\treble), t);
w.loopMIDI(m, 4);

z.stop
y.stop
w.stop
