// VoxExport smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// Requires JSONlib and a python3 executable with music21.

(
var assert, valueAt, makeMap, makeEvent, map, map3, box, multi, arr, source, reply;
var basePath, jsonPath, multiJsonPath, arrJsonPath, xmlPath, multiXmlPath, arrXmlPath;
var keepXmlPath, badXmlPath, keepJsonPath, badJsonPath, doc, parts, result, xmlText;

assert = { |condition, message|
	if (condition.not) {
		Error("VoxExport smoke failed: " ++ message).throw;
	};
	"PASS: %".format(message).postln;
};

valueAt = { |dictionary, key|
	dictionary[key] ? dictionary[key.asString]
};

makeMap = { |beats, divisions, tpqn = 960|
	var output = MetreMap.new;
	beats = beats ?? { [1, 1, 1, 1] };
	divisions = divisions ?? { [4, 4, 4, 4] };
	output.add(MetreRegion.new(0, Metre.new(beats, divisions, tpqn)));
	output
};

map = makeMap.();
map3 = makeMap.([1, 1, 1], [4, 4, 4]);

makeEvent = { |start, dur = 480, note = 60, velocity = 90, channel = 0, eventMap|
	var activeMap = eventMap ? map;
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: velocity,
		channel: channel,
		position: TimeConverter.ticksToPos(start, activeMap)
	)
};

basePath = "/tmp/vox_export_smoke".standardizePath;
jsonPath = basePath ++ "_box.json";
multiJsonPath = basePath ++ "_multi.json";
arrJsonPath = basePath ++ "_arr.json";
xmlPath = basePath ++ "_box.musicxml";
multiXmlPath = basePath ++ "_multi.musicxml";
arrXmlPath = basePath ++ "_arr.musicxml";
keepXmlPath = basePath ++ "_keep.musicxml";
badXmlPath = basePath ++ "_bad.musicxml";
keepJsonPath = keepXmlPath ++ ".voxexport.json";
badJsonPath = badXmlPath ++ ".voxexport.json";

[
	jsonPath, multiJsonPath, arrJsonPath, xmlPath, multiXmlPath, arrXmlPath,
	keepXmlPath, badXmlPath, keepJsonPath, badJsonPath
].do { |path|
	if (File.exists(path)) { File.delete(path) };
};

box = Box.new([
	makeEvent.(0, 480, 60, 90, 1),
	makeEvent.(960, 480, 64, 80, 1)
], map, \lead);

assert.(box.exportJSON(jsonPath).isKindOf(Vox), "Box.exportJSON returns exported Vox snapshot");
doc = JSONlib.parseFile(jsonPath, useEvent: false, postWarnings: false);
assert.(valueAt.(doc, \format) == "voxbox-export", "JSON writes VoxExport format");
assert.(valueAt.(doc, \version) == 1, "JSON writes schema version");
assert.(valueAt.(doc, \type) == "Vox", "Box JSON exports Vox type");
assert.(valueAt.(doc, \tpqn) == 960, "JSON writes tpqn");
assert.(valueAt.(valueAt.(valueAt.(doc, \metreMap), \regions)[0], \timeSignature) == "4/4", "JSON writes metre time signature");
parts = valueAt.(doc, \parts);
assert.(valueAt.(parts[0], \label) == "lead", "JSON writes part label");
assert.(valueAt.(valueAt.(parts[0], \events)[1], \start) == 960, "JSON writes event start ticks");
assert.(valueAt.(valueAt.(parts[0], \events)[1], \midinote) == 64, "JSON writes MIDI notes");
assert.(valueAt.(valueAt.(parts[0], \events)[0], \channel) == 1, "JSON writes event channels");

multi = BoxMulti.new([
	box,
	Box.new([makeEvent.(0, 960, 48, 70, 2)], map, \bass)
], map, \score);
assert.(multi.exportJSON(multiJsonPath).isKindOf(VoxMulti), "BoxMulti.exportJSON returns VoxMulti snapshot");
doc = JSONlib.parseFile(multiJsonPath, useEvent: false, postWarnings: false);
parts = valueAt.(doc, \parts);
assert.(parts.collect { |part| valueAt.(part, \label) } == ["bass", "lead"], "BoxMulti JSON writes stable sorted parts");

source = Vox.new([makeEvent.(0, 480, 72, 90, 0)], map, \lead);
reply = Vox.new([makeEvent.(0, 480, 76, 90, 0)], map, \lead);
arr = VoxArrangement.new(map, \piece);
arr.add(\source, 0, source);
arr.add(\reply, Pos(1), reply);
assert.(arr.exportJSON(arrJsonPath).isKindOf(VoxMulti), "VoxArrangement.exportJSON returns rendered VoxMulti snapshot");
doc = JSONlib.parseFile(arrJsonPath, useEvent: false, postWarnings: false);
parts = valueAt.(doc, \parts);
assert.(valueAt.(valueAt.(parts[0], \events)[1], \start) == 3840, "arrangement export writes rendered placed ticks");

assert.(box.exportMusicXML(xmlPath).isKindOf(Vox), "Box.exportMusicXML returns exported Vox snapshot");
assert.(File.exists(xmlPath) and: { File.fileSize(xmlPath) > 0 }, "Box.exportMusicXML creates non-empty MusicXML");
xmlText = File.readAllString(xmlPath);
assert.(xmlText.find("lead").notNil, "MusicXML contains part name");

assert.(multi.exportMusicXML(multiXmlPath).isKindOf(VoxMulti), "BoxMulti.exportMusicXML creates MusicXML");
assert.(File.exists(multiXmlPath) and: { File.fileSize(multiXmlPath) > 0 }, "BoxMulti MusicXML is non-empty");

assert.(arr.exportMusicXML(arrXmlPath).isKindOf(VoxMulti), "VoxArrangement.exportMusicXML creates MusicXML");
assert.(File.exists(arrXmlPath) and: { File.fileSize(arrXmlPath) > 0 }, "VoxArrangement MusicXML is non-empty");

assert.(box.exportMusicXML(keepXmlPath, keepJSON: true).isKindOf(Vox), "keepJSON export succeeds");
assert.(File.exists(keepJsonPath), "keepJSON leaves intermediate JSON");

File.use(badXmlPath, "w", { |file| file.write("existing") });
result = box.exportMusicXML(badXmlPath, "/definitely/not/python");
assert.(result.isNil, "bad python executable returns nil");
assert.(File.readAllString(badXmlPath) == "existing", "bad python executable does not corrupt existing XML");
assert.(File.exists(badJsonPath), "failed MusicXML conversion leaves JSON for debugging");

[
	jsonPath, multiJsonPath, arrJsonPath, xmlPath, multiXmlPath, arrXmlPath,
	keepXmlPath, badXmlPath, keepJsonPath, badJsonPath
].do { |path|
	if (File.exists(path)) { File.delete(path) };
};

"VOX EXPORT SMOKE COMPLETE".postln;
)
