(
// Durable descriptive provenance and VoxBank JSON round-trip.
// Run this file from SuperCollider after recompiling the class library.
var assert, map, makeEvent, source, transpose, canon, clip, recipe, before, router;
var gathered, bank, arrangement, loaded, loadedOpening, archivePath;
var malformedPath, badVersionPath, duplicatePath, document, midiPath, midiBox, midiMulti;
var badFormatPath, badMapPath, badPayloadPath;
var eagerClip, modules, unsupported, harmony, chordMap, routeTransposer;

assert = { |condition, description|
	if (condition.not) {
		Error("FAIL: %".format(description)).throw
	};
	"PASS: %".format(description).postln;
};

map = MetreMap.new;
map.add(MetreRegion.new(0, Metre([1, 1, 1, 1], [4, 4, 4, 4], 960)));

makeEvent = { |start, dur, note|
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: 90,
		channel: 0,
		position: TimeConverter.ticksToPos(start, map),
		metadata: (fixture: \kept)
	)
};

source = Box.new([
	makeEvent.(0, 960, 60),
	makeEvent.(960, 960, 62),
	makeEvent.(1920, 960, 64)
], map, \dux);
source.metadata[\note] = "manual source";
source.metadata[\runtimeThing] = { "omit me" };
assert.(source.provenance[\op] == \manualBox, "manual Box records fallback origin");
eagerClip = source.clipRange([0, 960]);
assert.(eagerClip.provenance[\op] == \clip, "eager Box clipping stamps boundary");

modules = [
	[CTransposer(1), \transposeChromatic],
	[DTransposer(1), \transposeDiatonic],
	[ModeMap(), \modeMap],
	[VoxCanoniser(1, [\solo], [Pos(0)]), \canonise],
	[Elongator(2), \elongate],
	[RandElong(1, 2, 99), \elongateRandom],
	[Granulator([2], seed: 99), \granulate],
	[HarmonyMask(seed: 99), \harmonyMask]
];
modules.do { |pair|
	source >>> pair[0];
	assert.(pair[0].provenance[\op] == pair[1], "% provenance recipe is supported".format(pair[1]));
};

unsupported = VoxModule.new(\experimental);
source >>> unsupported;
assert.(unsupported.provenance[\op] == \unsupportedNode, "experimental node uses class-only fallback");

chordMap = ChordMap(map).add(0, ChordSpec(60, Scale.at(\ionian), [0, 2, 4]));
harmony = HarmonyMask(chordMap: chordMap, seed: 123);
source >>> harmony;
assert.(harmony.provenance[\params][\chordMap].first[\start] == 0, "HarmonyMask provenance captures chord map");

transpose = CTransposer(7);
canon = VoxCanoniser(2, [\bass, \tenor], [Pos(0), Pos(beat: 1)], \canon);
source >>> transpose;
transpose >>> canon;
clip = canon >>* \tenor >>/ [960, 2400];

recipe = clip.provenance;
assert.(recipe[\op] == \clip, "live clip recipe records clipping");
assert.(recipe[\input][\op] == \select, "live clip recipe records selection");
assert.(recipe[\input][\input][\op] == \canonise, "live clip recipe records canoniser");
assert.(recipe[\input][\input][\input][\op] == \transposeChromatic, "live clip recipe records transposer");
assert.(recipe[\input][\input][\input][\input][\op] == \manualBox, "live recipe reaches manual origin");
before = recipe.asCompileString;
clip.out;
clip.out;
assert.(clip.provenance.asCompileString == before, "repeated out calls do not grow provenance");
clip.postProvenance;

routeTransposer = CTransposer(12);
router = canon >>@ [\tenor <<< routeTransposer, \bass];
recipe = router.provenance;
assert.(recipe[\op] == \route, "router records routing recipe");
assert.(recipe[\params][\branches][\tenor][\op] == \transposeChromatic, "router records processed branch");
assert.(recipe[\params][\branches][\bass][\op] == \routeBranch, "router records fallback branch");

gathered = clip >>> Box.new;
assert.(gathered.provenance[\op] == \gather, "explicit gather stamps boundary");

bank = VoxBank.new(\ideas);
clip >>= bank.slot(\opening);
canon >>= bank.slot(\fullCanon);
assert.(bank.provenanceAt(\opening)[\op] == \bankDeposit, "bank deposit stamps boundary");
assert.(bank.at(\fullCanon).isKindOf(VoxMulti), "bank stores VoxMulti snapshot");
bank.postProvenance(\opening);

arrangement = VoxArrangement.new(map, \piece);
bank.at(\opening) >>= arrangement.slot(\openingA, Pos(2), anchor: \pos);
assert.(arrangement.at(\openingA).vox.provenance[\op] == \arrangementRegion, "arrangement placement stamps boundary");
assert.(arrangement.at(\openingA).vox.provenance[\params][\resolvedTick] == 7680, "arrangement provenance stores resolved tick");
arrangement.move(\openingA, Pos(3));
assert.(arrangement.at(\openingA).vox.provenance[\params][\resolvedTick] == 11520, "arrangement move refreshes provenance tick");

archivePath = "/tmp/voxbox_provenance_smoke.voxbank.json";
bank.writeJSON(archivePath);
loaded = VoxBank.readJSON(archivePath);
assert.(loaded.notNil, "bank JSON archive loads");
assert.(loaded.keys.asArray == [\opening, \fullCanon], "bank JSON archive preserves slot order");
loadedOpening = loaded.at(\opening);
assert.(loadedOpening.events.size == bank.at(\opening).events.size, "bank JSON archive preserves events");
assert.(loadedOpening.events.first[\position].notNil, "bank JSON archive recalculates event positions");
assert.(loadedOpening.metadata[\note] == "manual source", "bank JSON archive preserves safe metadata");
assert.(loadedOpening.metadata[\runtimeThing].isNil, "bank JSON archive omits unsupported metadata");
assert.(loadedOpening.provenance.asCompileString == bank.at(\opening).provenance.asCompileString, "bank JSON archive preserves provenance exactly");
assert.(loaded.at(\fullCanon).keys.asSet == bank.at(\fullCanon).keys.asSet, "bank JSON archive preserves VoxMulti labels");

midiPath = (thisProcess.nowExecutingPath.dirname.dirname +/+ "hc_orig.mid").standardizePath;
midiBox = Box.fromMIDIPath(midiPath, \midiSource);
assert.(midiBox.provenance[\op] == \midiImport, "MIDI path helper records import origin");
assert.(midiBox.provenance[\params][\path] == midiPath, "MIDI path helper stores absolute path");
midiMulti = BoxMulti.fromMIDIPath(midiPath, \midiScore);
assert.(midiMulti.provenance[\op] == \midiImportMulti, "multi-track MIDI path helper records import origin");
assert.(midiMulti.provenance[\params][\path] == midiPath, "multi-track MIDI path helper stores absolute path");

malformedPath = "/tmp/voxbox_provenance_bad_json.voxbank.json";
File.use(malformedPath, "w", { |file| file.write("{ nope") });
assert.(VoxBank.readJSON(malformedPath).isNil, "malformed JSON archive is rejected");

document = JSONlib.parseFile(archivePath, useEvent: false, postWarnings: false);
document["version"] = 2;
badVersionPath = "/tmp/voxbox_provenance_bad_version.voxbank.json";
File.use(badVersionPath, "w", { |file|
	file.write(JSONlib.convertToJSON(document, postWarnings: false))
});
assert.(VoxBank.readJSON(badVersionPath).isNil, "unsupported archive version is rejected");

document = JSONlib.parseFile(archivePath, useEvent: false, postWarnings: false);
document["slots"] = document["slots"] ++ [document["slots"].first];
duplicatePath = "/tmp/voxbox_provenance_duplicate.voxbank.json";
File.use(duplicatePath, "w", { |file|
	file.write(JSONlib.convertToJSON(document, postWarnings: false))
});
assert.(VoxBank.readJSON(duplicatePath).isNil, "duplicate archived bank slots are rejected");

document = JSONlib.parseFile(archivePath, useEvent: false, postWarnings: false);
document["format"] = "not-voxbox";
badFormatPath = "/tmp/voxbox_provenance_bad_format.voxbank.json";
File.use(badFormatPath, "w", { |file|
	file.write(JSONlib.convertToJSON(document, postWarnings: false))
});
assert.(VoxBank.readJSON(badFormatPath).isNil, "wrong archive format is rejected");

document = JSONlib.parseFile(archivePath, useEvent: false, postWarnings: false);
document["slots"].first["snapshot"]["metremap"]["regions"] = [];
badMapPath = "/tmp/voxbox_provenance_bad_map.voxbank.json";
File.use(badMapPath, "w", { |file|
	file.write(JSONlib.convertToJSON(document, postWarnings: false))
});
assert.(VoxBank.readJSON(badMapPath).isNil, "malformed metre map is rejected");

document = JSONlib.parseFile(archivePath, useEvent: false, postWarnings: false);
document["slots"].first["snapshot"]["type"] = "Unknown";
badPayloadPath = "/tmp/voxbox_provenance_bad_payload.voxbank.json";
File.use(badPayloadPath, "w", { |file|
	file.write(JSONlib.convertToJSON(document, postWarnings: false))
});
assert.(VoxBank.readJSON(badPayloadPath).isNil, "invalid payload type is rejected");

"PROVENANCE ARCHIVE SMOKE COMPLETE".postln;
)
