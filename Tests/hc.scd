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
Vox.fromMIDI(f, \dux) >>= \dux

>>> DTransposer.new(2, scale: Scale.at(\minor)) >>= \transp_one

>>> VoxCanoniser(3, [\bass, \tenor, \treble], [Pos(0), Pos(division: 2), Pos(beat: 1)], \canon) >>= \canon

>>@ [\tenor <<< CTransposer(5), \treble <<< CTransposer(12), \bass] >>= \complete
)

t = TempoClock.new(1);
z = VoxPlayer.new(~complete, t);
z.loopMIDI(m, 4);



~complete.routes[\bass].out
\hello.postln


~transp_one.degrees =
~dux >>/ [Pos(1), Pos(2)] >>> VoxPlayer.new().loopMIDI(m)

~dux >>> DTransposer.new(3) >>= \transposer

~transposer.out
