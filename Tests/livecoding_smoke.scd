// VoxBox live-coding layer smoke checks.
//
// Run this file from SuperCollider after recompiling the class library.
// It uses VoxRecordingMIDIOut rather than external MIDI hardware.

(
var assert, map, makeEvent, box, multi, vox, voxMulti;
var sink, clock, boxPlayer, voxPlayer, multiPlayer, chainPlayer;
var grain, harm, chain, oldGrain, rebound, revBefore;
var tap, tapChain, plainOut, tappedOut, tapPlayer, tapAgain, tapA, tapB;
var split, fork, forkOut, masked, branchMasked, fragmentMasked;
var hocketBox, hocketFork, maskedPlayer, chainMask, scopedMaskOut;
var bindPlayer, offPlayer, forkTap, controls, clipChain, oldClip, fluentChain, fluentOut;

assert = { |condition, message|
	if (condition.not) {
		Error("VoxBox livecoding smoke failed: " ++ message).throw;
	};
};

map = MetreMap.new;
map.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4], 960)));

makeEvent = { |start, dur, note, channel = 0|
	(
		absTime: start,
		dur: dur,
		midinote: note,
		velocity: 90,
		channel: channel,
		position: TimeConverter.ticksToPos(start, map)
	)
};

box = Box.new([
	makeEvent.(0, 960, 60),
	makeEvent.(960, 960, 62)
], map, \line);
multi = BoxMulti.new([
	box,
	Box.new([makeEvent.(0, 960, 67, 1)], map, \answer)
], map, \pair);
vox = box.out;
voxMulti = multi.out;

sink = VoxRecordingMIDIOut.new;
clock = TempoClock.new(20);

boxPlayer = box.loopMIDI(sink, clock);
assert.(boxPlayer.isKindOf(VoxPlayer), "Box.loopMIDI returns a VoxPlayer");
boxPlayer.stop;

voxPlayer = vox.loopMIDI(sink, clock);
assert.(voxPlayer.isKindOf(VoxPlayer), "Vox.loopMIDI returns a VoxPlayer");
voxPlayer.stop;

multiPlayer = voxMulti.loopMIDI(sink, clock);
assert.(multiPlayer.isKindOf(VoxPlayer), "VoxMulti.loopMIDI returns a VoxPlayer");
multiPlayer.stop;

grain = Granulator([2], depth: 1) @ \grain;
harm = HarmonyMask(root: 60) @ \harm;
chain = box >>> grain >>> harm;
chainPlayer = chain.loopMIDI(sink, clock);
assert.(chainPlayer.isKindOf(VoxPlayer), "live chain loopMIDI returns a VoxPlayer");
chainPlayer.stop;

assert.(grain.handle == \grain, "@ stores a node handle");
assert.(harm.handle == \harm, "@ stores downstream node handle");
assert.(chain.node(\grain) === grain, "node(handle) finds upstream handle");
assert.(chain[\grain] === grain, "at(handle) supports bracket lookup");
chain[\grain].depth = 2;
assert.(grain.depth == 2, "handle lookup mutates the original node");
assert.(chain.handles == [\grain, \harm], "handles are listed from head to tail");
assert.(chain.out.label == \line, "handles do not change musical voice labels");

VoxSession.reset;
chain = box >>> (Granulator([2], depth: 1) @ \grain) >>= \chain;
oldGrain = \chain.envirGet[\grain];
revBefore = \chain.envirGet.effectiveRevision;
rebound = box >>> (Granulator([2], depth: 3) @ \grain) >>= \chain;
assert.(\chain.envirGet === rebound, ">>= stores the reconciled chain in the environment");
assert.(rebound[\grain] === oldGrain, "session preserves same-class handled node identity");
assert.(oldGrain.depth == 3, "session copies compatible parameters into preserved node");
assert.(rebound.effectiveRevision != revBefore, "session parameter reconciliation updates revision");

fluentChain = box
>>> VoxCanoniser.voices(2).names(\tenor, \bass).offsets(Pos(0), Pos(0, 1, 2))
>>> CTransposer.semitones(12)
>>/ (0 -> 960);
assert.(fluentChain.out.at(\tenor).events.collect(_[\midinote]) == [72], "fluent module builders compose in chains");
assert.(fluentChain.out.at(\bass).events.isEmpty, "association ranges clip fluent chains");

VoxSession.reset;
clipChain = box >>/ [0, 960] @ \clip >>= \clipChain;
oldClip = \clipChain.envirGet[\clip];
assert.(clipChain.out.events.collect(_[\midinote]) == [60], "handled clip initially uses bound range");
clipChain = box >>/ [960, 1920] @ \clip >>= \clipChain;
assert.(\clipChain.envirGet[\clip] === oldClip, "session preserves handled clip identity");
assert.(clipChain.out.events.collect(_[\midinote]) == [62], "session copies handled clip range on rebind");

VoxSession.reset;
plainOut = box.out;
tap = VoxOut.loopMIDI(\tap, sink, clock).on;
tapChain = box >>> tap;
tappedOut = tapChain.out;
tapPlayer = VoxSession.current.out(\tap);
assert.(tapPlayer.isKindOf(VoxPlayer), "VoxOut registers a persistent player");
assert.(tappedOut.events.collect(_[\midinote]) == plainOut.events.collect(_[\midinote]), "VoxOut passes material through unchanged");
tapAgain = box >>> VoxOut.loopMIDI(\tap, sink, clock).on;
tapAgain.out;
assert.(VoxSession.current.out(\tap) === tapPlayer, "same named VoxOut preserves player object");
tap.off;

tapA = VoxOut.loopMIDI(\tapA, sink, clock).on;
tapB = VoxOut.loopMIDI(\tapB, sink, clock).off;
(box >>> tapA).out;
(box >>> tapB).out;
assert.(VoxSession.current.out(\tapA).isKindOf(VoxPlayer), "first VoxOut tap registers independently");
assert.(VoxSession.current.out(\tapB).isKindOf(VoxPlayer), "second VoxOut tap registers independently");
assert.(tapA.audible and: { tapB.audible.not }, "VoxOut taps keep independent audible flags");
tapA.off;
tapB.off;

VoxSession.reset;
fluentOut = VoxOut.named(\fluentTap).channels(\line -> 2).clock(clock).loopMIDI(sink).on;
box >>> fluentOut >>= \fluentOutChain;
assert.(VoxSession.current.out(\fluentTap).channelMap[\line] == 2, "fluent VoxOut builder sets channel map");
assert.(VoxSession.current.outputSpecs[\fluentTap][\clock] === clock, "fluent VoxOut builder sets clock");
assert.(VoxSession.current.outputSpecs[\fluentTap][\midiout] === sink, "fluent VoxOut builder sets MIDI output");
fluentOut = VoxOut.channels(\line -> 4).clock(clock).loopMIDI(sink).on;
box >>> fluentOut >>= \defaultOutChain;
assert.(VoxSession.current.out(\out).channelMap[\line] == 4, "default VoxOut.channels builder uses out name");

VoxSession.reset;
VoxSession.current.midiout = sink;
VoxSession.current.clock = clock;
box >>> VoxOut.loopMIDI(\bindTap).on >>= \bindChain;
bindPlayer = VoxSession.current.out(\bindTap);
assert.(bindPlayer.isKindOf(VoxPlayer), "binding starts/registers an on VoxOut without explicit out");
assert.(VoxSession.current.outputSpecs[\bindTap][\transport] == \running, "binding starts VoxOut transport");
assert.(VoxSession.current.outputSpecs[\bindTap][\audible] == true, "on binding marks VoxOut audible");
box >>> CTransposer(7) >>> VoxOut.loopMIDI(\bindTap).on >>= \bindChain;
assert.(VoxSession.current.out(\bindTap) === bindPlayer, "rebinding same named on VoxOut preserves player");
box >>> VoxOut.loopMIDI(\bindTap).channels(\line -> 5).on >>= \bindChain;
assert.(VoxSession.current.out(\bindTap) === bindPlayer, "channel-map rebind preserves player");
assert.(bindPlayer.channelMap[\line] == 5, "VoxOut channels helper updates player channel map");
assert.(VoxSession.current.outputSpecs[\bindTap][\channelMap][\line] == 5, "VoxOut stores channel map in output spec");
box >>> VoxOut.loopMIDI(\bindTap).channels(\line -> 7).on >>= \bindChain;
assert.(bindPlayer.channelMap[\line] == 7, "VoxOut channel map updates on live rebind");
box >>> VoxOut.loopMIDI(\bindTap).off >>= \bindChain;
offPlayer = VoxSession.current.out(\bindTap);
assert.(offPlayer === bindPlayer, "off rebind preserves player object");
assert.(VoxSession.current.outputSpecs[\bindTap][\transport] == \running, "off rebind keeps transport running");
assert.(VoxSession.current.outputSpecs[\bindTap][\audible] == false, "off rebind makes output silent");
box >>> VoxOut.loopMIDI(\bindTap).on >>= \bindChain;
assert.(VoxSession.current.out(\bindTap) === bindPlayer, "on after off preserves player object");
assert.(VoxSession.current.outputSpecs[\bindTap][\audible] == true, "on after off makes output audible");

box
>>> VoxOut.loopMIDI(\raw).on
>>> CTransposer(7)
>>> VoxOut.loopMIDI(\shifted).off
>>> CTransposer(5)
>>> VoxOut.loopMIDI(\silent).off
>>= \multiTapChain;
assert.(VoxSession.current.outputSpecs[\raw][\transport] == \running, "first tap transport can run");
assert.(VoxSession.current.outputSpecs[\shifted][\audible] == false, "second tap can be silent independently");
assert.(VoxSession.current.outputSpecs[\silent][\transport] == \running, "silent tap transport still runs");

forkTap = box >>< [
	\a -> (CTransposer(0) >>> VoxOut.loopMIDI(\forkA).on),
	\b -> (CTransposer(7) >>> VoxOut.loopMIDI(\forkB).off)
];
forkTap >>= \forkTapChain;
assert.(VoxSession.current.out(\forkA).isKindOf(VoxPlayer), "fork branch on tap activates during bind");
assert.(VoxSession.current.outputSpecs[\forkB][\audible] == false, "fork branch off tap activates silently during bind");

split = multi >>< [\only -> \line];
assert.(split.isKindOf(VoxPatcher), "existing >>< split specs still return VoxPatcher");
assert.(split[\only].label == \line, "existing >>< split selects VoxMulti voices");

fork = box >>< [
	\a -> (VoxCanoniser(1, [\aVoice], [Pos(0)], \canonA) @ \canonA),
	\b -> (VoxCanoniser(1, [\bVoice], [Pos(0)], \canonB) @ \canonB)
];
assert.(fork.isKindOf(VoxFork), "node-valued >>< specs create a VoxFork");
assert.(fork[\a].isKindOf(VoxForkBranch), "fork branches are addressable by key");
assert.(fork[\a][\canonA].numVoices == 1, "branch handle lookup reaches branch chain");
fork[\a][\canonA].numVoices = 2;
assert.(fork[\a][\canonA].numVoices == 2, "branch handles mutate independently");
forkOut = fork.out;
assert.(forkOut.isKindOf(VoxMulti), "fork output is a VoxMulti");
assert.(forkOut.at(\aVoice).notNil and: { forkOut.at(\bVoice).notNil }, "fork combines branch outputs");

masked = box.mask([[Pos(beat: 1), Pos(beat: 2)]]).out;
assert.(masked.events.size == 1, "VoxMask omits inactive onsets");
assert.(masked.events.first[\absTime] == 960, "VoxMask preserves absolute timing");
assert.(box.out.events.size == 2, "VoxMask does not mutate the source Box");

fork[\a].mask([[Pos(beat: 1), Pos(beat: 2)]]);
branchMasked = fork[\a].out;
assert.(branchMasked.at(\aVoice).events.first[\absTime] == 960, "fork branch masks preserve in-place timing");
fork[\a].clearMask;
assert.(fork[\a].out.at(\aVoice).events.size == 2, "fork branch masks can be cleared");

fragmentMasked = box.mask(Eighths[0, 2]).out;
assert.(fragmentMasked.events.collect(_[\absTime]) == [0, 960], "Eighths shorthand selects metrical cells");
fragmentMasked = box.mask(Fragments(\eighth)[2]).out;
assert.(fragmentMasked.events.collect(_[\absTime]) == [960], "Fragments symbol selector resolves cells");
fragmentMasked = box.mask(Fragments(1/16)[4]).out;
assert.(fragmentMasked.events.collect(_[\absTime]) == [960], "Fragments numeric selector resolves note fractions");
assert.(fragmentMasked.metadata[\spanTicks] == [0, 1920], "VoxMask preserves source span metadata");
maskedPlayer = VoxPlayer.new(box.mask(Eighths[0]), clock);
maskedPlayer.eventParts;
assert.(maskedPlayer.cycleTicks == 1920, "VoxPlayer keeps masked loops aligned to source span");
chainMask = box >>> VoxMask(Fragments(\quarter)[1]);
assert.(chainMask.out.events.collect(_[\absTime]) == [960], "VoxMask can be written directly into a chain");
chainMask = box >>> VoxMask(Fragments(\quarter), [1]);
assert.(chainMask.out.events.collect(_[\absTime]) == [960], "VoxMask accepts fragment selector plus index list");

hocketBox = Box.new([
	makeEvent.(0, 240, 60),
	makeEvent.(480, 240, 61),
	makeEvent.(960, 240, 62),
	makeEvent.(1440, 240, 63)
], map, \hocketSource);
scopedMaskOut = (
	hocketBox >>> VoxMask(Fragments(\eighth)[0]).atRange([480, 1440])
).out;
assert.(scopedMaskOut.events.collect(_[\absTime]) == [0, 480, 1440], "VoxMask atRange masks only the scoped span");
hocketFork = hocketBox >>< [
	\a -> (VoxCanoniser(1, [\ha], [Pos(0)], \haCanon) @ \haCanon),
	\b -> (VoxCanoniser(1, [\hb], [Pos(0)], \hbCanon) @ \hbCanon)
];
hocketFork.hocket(\a, \b, by: Fragments(\eighth));
assert.(hocketFork[\a].out.at(\ha).events.collect(_[\absTime]) == [0, 960], "hocket assigns alternating cells to first branch");
assert.(hocketFork[\b].out.at(\hb).events.collect(_[\absTime]) == [480, 1440], "hocket assigns complementary cells to second branch");
hocketFork.distribute(\roundRobin, \eighth);
assert.(hocketFork[\a].out.at(\ha).events.collect(_[\absTime]) == [0, 960], "distribute roundRobin assigns masks");

Routine({
	var asyncSink = VoxRecordingMIDIOut.new;
	var asyncClock = TempoClock.new(30);
	var asyncPlayer, noteOnCount, noteOffCount, beforePauseOns, afterPauseOns;

	VoxSession.reset;
	VoxSession.current.midiout = asyncSink;
	VoxSession.current.clock = asyncClock;
	controls = VoxOutControls.new;

	box >>> VoxOut.loopMIDI(\offAsync).off >>= \offAsyncChain;
	asyncPlayer = VoxSession.current.out(\offAsync);
	0.08.wait;
	noteOnCount = asyncSink.messages.count { |message| message[\type] == \noteOn };
	assert.(noteOnCount == 0, "off MIDI output emits no noteOns while transport runs");
	assert.(asyncPlayer.audible == false, "off async player remains silent");
	assert.(VoxSession.current.outputSpecs[\offAsync][\transport] == \running, "off async output keeps transport running");
	controls.on(\offAsync);
	assert.(asyncPlayer.audible == true, "controls on makes silent player audible");
	controls.off(\offAsync);
	assert.(asyncPlayer.audible == false, "controls off makes audible player silent");

	box >>> VoxOut.loopMIDI(\heldAsync).on >>= \heldAsyncChain;
	0.04.wait;
	assert.(VoxSession.current.out(\heldAsync).activeMIDICount > 0, "active test player holds MIDI notes");
	controls.pause(\heldAsync);
	noteOffCount = asyncSink.messages.count { |message| message[\type] == \noteOff };
	assert.(noteOffCount > 0, "pausing an active MIDI player sends noteOffs");
	assert.(VoxSession.current.outputSpecs[\heldAsync][\transport] == \paused, "controls pause marks transport paused");
	beforePauseOns = asyncSink.messages.count { |message| message[\type] == \noteOn };
	0.05.wait;
	afterPauseOns = asyncSink.messages.count { |message| message[\type] == \noteOn };
	assert.(beforePauseOns == afterPauseOns, "paused output schedules no new noteOns");
	controls.resume(\heldAsync);
	assert.(VoxSession.current.outputSpecs[\heldAsync][\transport] == \running, "controls resume marks transport running");
	controls.stop(\heldAsync);
	assert.(VoxSession.current.outputSpecs[\heldAsync][\transport] == \stopped, "controls stop marks transport stopped");
	controls.start(\heldAsync);
	assert.(VoxSession.current.outputSpecs[\heldAsync][\transport] == \running, "controls start restarts stopped transport");
	controls.map(\cmd1, \heldAsync, \pause);
	controls.trigger(\cmd1);
	assert.(VoxSession.current.outputSpecs[\heldAsync][\transport] == \paused, "mapped trigger invokes transport command");
	controls.unmap(\cmd1);
	controls.clear;
	controls.stop(\all);
	assert.(VoxSession.current.outputSpecs.values.every { |spec| spec[\transport] == \stopped }, "controls all-stop stops every output");

	VoxSession.current.stopAllOutputs;
	"VoxBox livecoding smoke passed.".postln;
}).play(AppClock);
)
