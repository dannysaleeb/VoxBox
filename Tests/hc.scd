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

Box.fromMIDI(f, \dux) >>> DTransposer(2) >>= \transposed

Box.fromMIDI(f, \dux) >>> DTransposer(4) >>= \transposed_2

Box.fromMIDI(f, \dux) >>= \dux

(
Box.fromMIDI(f, \dux) >>= \dux // the original melody

>>> VoxCanoniser(3, [\bass, \tenor, \treble], [Pos(0), Pos(division: 2), Pos(beat: 1)], \canon)

>>@ [\tenor <<< CTransposer(5), \treble <<< CTransposer(12), \bass]

>>> BoxMulti.new >>= \canon_one // gathering multi (can be routed further after gathering point)

>>@ [
	\bass <<< VoxCanoniser(2, [\bass1, \bass2], [Pos(0), Pos(1)]),
	\tenor <<< VoxCanoniser(2, [\tenor1, \tenor2], [Pos(0), Pos(beat: 1)]),
	\treble <<< VoxCanoniser(2, [\treble1, \treble2], [Pos(0), Pos(division: 2)])
]

>>> BoxMulti.new >>= \canon_two // another gathering point (further routing possible)
)

// tweak params & edit clips
~dux >>/ [Pos(2), Pos(3)] >>> DTransposer(1) >>= ~dux

~dux >>> DTransposer(-2) >>= ~dux

~dux

~canon_one.out[\tenor].events

t = TempoClock.new(1, queueSize: 100000);
z = VoxPlayer.new(~canon_one, t);
z.loopMIDI(m, 4);

t = TempoClock.new(1, queueSize: 100000);
y = VoxPlayer.new(~dux, t);
y.loopMIDI(m, 4);

t = TempoClock.new(1, queueSize: 100000);
w = VoxPlayer.new(~transposed_2, t);
w.loopMIDI(m, 4);

