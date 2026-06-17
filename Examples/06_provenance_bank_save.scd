(
MIDIClient.init;
m = MIDIOut.new(0);
m.latency = 0
)

(
// Durable descriptive provenance and frozen VoxBank JSON persistence.
// JSONlib and wslib are required. Recompile the class library before running.

~midiPath = "/Users/dannysaleeb/Library/Mobile Documents/com~apple~CloudDocs/Projects/Lockton Canons/meeting place/midi/ALL_dux.mid";
~archivePath = "/Users/dannysaleeb/Desktop/mp.voxbank.json";

~source = Box.fromMIDIPath(~midiPath, \dux);
~transpose = CTransposer(7);
~canon = VoxCanoniser(
	2,
	[\bass, \tenor],
	[Pos(0), Pos(beat: 2)],
	\canon
);

~source >>> ~transpose >>> ~canon;
~opening = ~canon >>* \tenor >>/ [Pos(0), Pos(4)];

~opening.postProvenance;

~bank = VoxBank(\ideas);
~opening >>= ~bank.slot(\opening);
~canon >>= ~bank.slot(\fullCanon);

~bank.playMIDI(\fullCanon, m, TempoClock.new(4));

~bank.postProvenance(\opening);
~bank.writeJSON(~archivePath);

// Loading restores frozen Vox and VoxMulti values, not live patch cables.
~loaded = VoxBank.readJSON(~archivePath);
~editableOpening = Box.fromVox(~loaded.at(\opening));
~editableCanon = BoxMulti.fromVoxMulti(~loaded.at(\fullCanon));
)
