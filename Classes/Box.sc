/////////////////////////////////////////////////////////////////////////////////////
// Box: a mutable container for composed material, for further creative processing //
/////////////////////////////////////////////////////////////////////////////////////
Box : VoxNode {
	classvar idCounter=0;
	var <events, metremap, range, <tpqn;
	var <history;
	var <>id;
	var <>parentBoxMulti;

	assignId {
		id = idCounter;
		idCounter = idCounter + 1;
	}

	// TODO: JUST CHECK METREMAP AUTOMATICALLY CALLS THE GETTER ... ELSE this.metremap NEEDED

	//////////////////
	// CONSTRUCTORS //
	//////////////////
	*new {
		arg events, metremap, label = \anonybox;

		^super.new.init(events, metremap, label);
	}

	init { |eventsArg, metremapArg, labelArg|
		events = eventsArg ?? [];
		label = labelArg;
		metremap = metremapArg ?? MetreMap.new;
		tpqn = this.metremap.tpqn; // default is 960 on MetreMap

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion.new(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		// sort events
		events.isEmpty.not.if {
			events = events.sortBy(\absTime)
		};

		// add Pos values to events
		this.calculatePositions;

		// set highlight to span the entire voice.
		this.highlightAll;

		metadata = Dictionary.new;

		// history created with Box, and snapshot of full Box is init commit
		history = VoxHistory.new;
		history.commit(Vox.new(events, metremap, label, metadata, this), "init commit");

		this.assignId;

		^this
	}

	calculatePositions {
		events.do {
			arg e;
			e[\position] = TimeConverter.ticksToPos(e[\absTime], metremap);
		}
	}

	earliestEventStart {
		^events.first[\absTime];
	}

	latestEventEnd {
		^events.last[\absTime] + events.last[\dur]
	}

	normaliseRange { |rangeArg|
		var start, end;

		// Ensure it's a 2-element array-like input
		if (rangeArg.isKindOf(SequenceableCollection).not or: { rangeArg.size != 2 }) {
			"Box.normaliseRange: Expected a 2-element array as input.".warn;
			^[0, 0]
		};

		start = rangeArg[0].abs;
		end = rangeArg[1].abs;

		if (start.isNumber.not or: { end.isNumber.not }) {
			"Box.normaliseRange: Expected a 2-element array of numbers as input.".warn;
			^[0, 0]
		};

		if (end < start) {
			var temp = start;
			start = end;
			end = temp
		};

		^[start, end]
	}

	// for internal use only
	setRange { |rangeArg|
		range = rangeArg;
		^this
	}

	// for internal use only
	setNormRangeAndNotify { |rangeArg|
		var normRange = this.normaliseRange(rangeArg);
		this.setRange(normRange);

		parentBoxMulti.notNil.if {
			parentBoxMulti.mirrorVoxHighlight(normRange);
		};

		^this
	}

	// for internal use only
	// Called by BoxMulti
	mirrorRangeFromMulti { |rangeArg|
		var normRange = this.normaliseRange(rangeArg);
		this.setRange(normRange);
	}

	highlightAll {
		var first = events.first;
		var last = events.last;

		this.isEmpty.if {
			range = [0,0];
		} {
			var rangeArg;

			rangeArg = [first.absTime, last.absTime + last.dur];

			// set range on Box, and check for parent BoxMulti
			this.setNormRangeAndNotify(rangeArg);
		};

		^this
	}

	isEmpty {
		^events.isEmpty
	}

	*fromMIDI {
		arg midifile, label = \anonybox;

		^super.new.initFromMIDI(midifile, label)
	}

	initFromMIDI {
		arg midifileArg, labelArg;
		var timesigArrs;

		label = labelArg;
		tpqn = midifileArg.division;

		// get metremap from time sig info
		// define fromTimeSigEvents on MetreMap later
		timesigArrs = midifileArg.timeSignatureEvents;
		metremap = MetreMap.new;
		// for each timeSig, create MetreRegion entry
		timesigArrs.do({
			arg timeSigArr;
			metremap.add(MetreRegion(
				timeSigArr[0],
				TimeConverter.midiTimeSigToMetre(timeSigArr.last, tpqn))
			);
		});

		events = this.noteSustainEventsToVoxEvents(midifileArg.noteSustainEvents);

		// set highlight to span the entire voice if possible
		this.highlightAll;

		label = labelArg;

		metadata = Dictionary.new;

		history = VoxHistory.new;
		history.commit(Vox.new(events, metremap, label, metadata, this), "init commit");

		this.assignId;

		^this
	}

	noteSustainEventsToVoxEvents { |noteSustainEvents|
		var eventsArr;
		eventsArr = noteSustainEvents.collect({
			arg nse;
			var event;
			event = Event.newFrom([[\track, \absTime, \midicmd, \channel, \midinote, \velocity, \dur, \upVelo], nse].lace);
		});

		eventsArr.sortBy(\absTime);
		eventsArr.do({
			arg event;
			event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
		});

		^eventsArr;
	}

	*fromVox {
		arg vox;
		var box = this.new(
			vox.events,
			vox.metremap,
			vox.label
		);
		vox.respondsTo(\source).if {
			box.metadata[\source] = vox.source;
		};
		^box
	}

	highlight { |startPos, endPos|
		var start, end;

		start = TimeConverter.posToTicks(startPos, metremap);
		end = TimeConverter.posToTicks(endPos, metremap);

		^this.setNormRangeAndNotify([start, end]);
	}

	tickHighlight { |startTick, endTick|
		range = this.normaliseRange([startTick, endTick]);
		^this;
	}

	// Only called from a BoxMulti, when BoxMulti highlighted
	multivoxHighlight { |startTick, endTick|
		range = [startTick, endTick];
		// no return necessary
	}

	highlighted {
		^"% --> %".format(range[0].toPos(metremap), range[1].toPos(metremap))
	}

	getRange {
		^range
	}

	metremap {
		parentBoxMulti.notNil.if {
			^parentBoxMulti.metremap
		} {
			^metremap
		}
	}

	metremap_ { |mm|
		if (parentBoxMulti.notNil) {
			"😬 Cannot set metremap directly on a Box inside a BoxMulti".warn;
			^this
		};

		metremap = mm;
		tpqn = mm.tpqn;

		if (events.notEmpty) {
			events.do { |event|
				event[\position] = TimeConverter.ticksToPos(event[\absTime], this.metremap);
			}
		}
	}

	asString {
		^"Box(%)".format(label);
	}

	duration {
		^(range[1] - range[0]);
	}

	// to utils?
	clip {
		^this.clipRange(range);
    }

	clipRange { |rangeArg|
		var rangeStart = rangeArg[0], rangeStartPos;
        var rangeEnd = rangeArg[1], rangeEndPos;
		var events, return = [];
		var box;

		events = this.events.deepCopy;

		return = events.select({
			arg event;

			var eventStart = event[\absTime];
			var eventEnd = eventStart + event[\dur];

			(eventStart < rangeEnd) and: (eventEnd > rangeStart);

		}).do({
			arg event;

			var eventStart = event[\absTime];
			var eventEnd = eventStart + event[\dur];

			if ((eventStart < rangeStart) && (eventEnd > rangeStart)) {
				event[\dur] = eventEnd - rangeStart;
				event[\absTime] = rangeStart;
			};

			if ((eventStart < rangeEnd) && (eventEnd > rangeEnd)) {
				event[\dur] = rangeEnd - eventStart;
			}
		});

		// create label
		rangeStartPos = TimeConverter.ticksToPos(rangeStart, metremap);
		rangeEndPos = TimeConverter.ticksToPos(rangeEnd, metremap);

		box = Box.new(return, metremap, label);
		box.metadata[\clip_origin] = this;

		^box;
	}

	merge { |source|
		var vox = source.respondsTo(\out).if {
			source.out
		} {
			"Box.merge: Requires VoxNode as input".warn;
			^this
		};

		// if Vox, replace if label match, combine together if not
		if (vox.isKindOf(Vox)) {
			if (vox.label == this.label) {
				^Box.fromVox(vox);
			} {
				^BoxMulti.new([this.copy, Box.fromVox(vox)], vox.metremap, this.label);
			}
		};

		// if vox is VoxMulti, replace match, combine with non-matches
		if (vox.isKindOf(VoxMulti)) {
			var match = vox[this.label];
			var boxes;

			if (match.notNil) {
				var otherVoxes = vox.asArray.reject({ |p| p.label == this.label });
				boxes = [match] ++ otherVoxes;
			} {
				boxes = [this.copy] ++ vox.asArray;
			};

			boxes = boxes.collect(Box.fromVox(_));
			^BoxMulti.new(boxes, vox.metremap, this.label);
		};

		"Box.merge: Unrecognized vox type %".format(vox.class).warn;
		^this
	}

	load { |source, strict=true|

		var vox, startTick, endTick;

		// resolve vox
		vox = source.isKindOf(VoxNode).if {
			source.out
		} {
			source // assume Vox
		};

		if (vox.isKindOf(Vox).not) {
			"❌ Box.load: Expected Vox or VoxNode with .out, got %"
			.format(vox.class).warn;
			^this
		};

		// is there ever a situation where I want to load BoxMulti?

		if (strict and: { vox.label != this.label }) {
			"❌ Box.load: Vox label % ≠ Box label %"
			.format(vox.label, this.label).warn;
			^this
		};

		// otherwise process
		startTick = vox.events.first[\absTime];
		endTick = vox.events.last[\absTime] + vox.events.last[\dur];

		events = events.reject { |e|
			e[\absTime] < endTick and: { (e[\absTime] + e[\dur]) > startTick }
		} ++ vox.events;

		events.sortBy(\absTime);

		if (this.duration == 0) {
			this.highlightAll;
		};

		^this
	}

	// some tidy-up to do (range etc.)
	forceload { |source|
		this.load(source, false);
		if (this.duration == 0) {
			this.highlightAll;
		};
		^this
	}

	loadFromEvents { |eventsArg, metremapArg, labelArg|
		if (eventsArg.isNil or: { eventsArg.isEmpty }) {
			"❌ Box.loadFromEvents: No events provided".warn;
			^this
		};

		^this.forceload(Box.new(eventsArg, metremapArg, labelArg));
	}

	commit { |label = nil|

		parentBoxMulti.isNil.if {
			history.commit(this.out, label);
		};

		"❌ Box(%).commit: cannot commit to Box history from within BoxMulti. Use BoxMulti.commit instead.".format(this.label).warn;
	}

	undo {
		parentBoxMulti.isNil.if {
			this.input = history.undo.vox;
		};

		"❌ Box(%).commit: cannot undo Box history from within BoxMulti. Use BoxMulti.undo instead.".format(this.label).warn;
	}

	redo {
		parentBoxMulti.isNil.if {
			this.input = history.redo.vox;
		};

		"❌ Box(%).commit: cannot undo Box history from within BoxMulti. Use BoxMulti.undo instead.".format(this.label).warn;
	}

	out {

		if (input.notNil) {
			var vox = input.out;
			^vox.copy
		};

		// Everything copied in Vox
		^Vox.new(
			this.clip(range).events,
			metremap,
			label,
			metadata,
			this
		)
	}
}

BoxMulti : VoxNode {
	var <boxes, <history, range, <metremap, <>tpqn;

	*new { |boxes, metremap, label|
		^super.new.init(boxes, metremap, label);
	}

	init { |boxesArg, metremapArg, labelArg|

		label = labelArg ? \anonyboxmulti;
		metadata = Dictionary.new;

		boxesArg.notNil.if {
			var labels;
			labels = boxesArg.collect(_.label);
			boxes = Dictionary.newFrom([labels, boxesArg].lace);
		} {
			boxes = Dictionary.new;
		};

		metremap = metremapArg ?? {
			boxes.notEmpty.if {
				boxes.values.first.metremap // bit arbitrary
			} {
				MetreMap.new
			}
		};

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		// box updates
		boxes.notEmpty.if {
			boxes.values.do({ arg box, i;
				box.parentBoxMulti = this;
				box.calculatePositions; // based on parent metremap
			})
		};

		tpqn = metremap.tpqn;

		this.highlightAll;

		history = VoxHistory.new;
		history.commit(this.out, "init commit"); // check this ...

		^this
	}

	*fromMIDI { |midifile, label|

		// collect boxes
		var timesigArrs,
		tracks = Dictionary.new,
		trackNums, boxes,
		nse = midifile.noteSustainEvents,
		metremap;

		// MetreMap creation
		timesigArrs = midifile.timeSignatureEvents;
		metremap = MetreMap.new;
		// for each timeSignatureEvent, create MetreRegion entry
		timesigArrs.do({
			arg timesigArr;
			var region = MetreRegion(
				timesigArr[1],
				TimeConverter.midiTimeSigToMetre(timesigArr.last, midifile.division)
			);
			metremap.add(region);
		});

		metremap.listEntries;

		// get all unique note-containing track numbers
		trackNums = nse.collect { |e| e[0] }.asSet;

		// get label for each track, collect and sort all events in each track, and assign in tracks Dict
		trackNums.do({
			arg i;
			var events = nse.select { |e| e[0] == i };
			var label = midifile.trackNames.detect {arg item; item[0] == i}.at(1);

			tracks[i] = Dictionary.new;
			tracks[i][\label] = label;

			events.sort({ |a, b| a[1] < b[1] });
			tracks[i][\events] = events
		});

		// collect one Box per track
		boxes = tracks.values.collect({
			arg track;
			Box.new(this.noteSustainEventsToVoxEvents(track[\events], metremap), metremap, track[\label])
		});

		^BoxMulti.new(boxes, metremap, label)
	}

	*noteSustainEventsToVoxEvents { |noteSustainEvents, metremap|
		var eventsArr;
		eventsArr = noteSustainEvents.collect({
			arg nse;
			var event;
			event = Event.newFrom([[\track, \absTime, \midicmd, \channel, \midinote, \velocity, \dur, \upVelo], nse].lace);
		});

		eventsArr.sortBy(\absTime);
		eventsArr.do({
			arg event;
			event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
		});

		^eventsArr;
	}

	*fromDict { |boxesDict, metremap, label|
		^super.new.initFromDict(boxesDict, metremap, label)
	}

	initFromDict { |boxesDict, metremapArg, labelArg|

		boxes = boxesDict ? Dictionary.new;
		label = labelArg ? \anonyboxmulti;
		metadata = Dictionary.new;

		metremap = metremapArg ?? {
			boxes.notEmpty.if {
				boxes.values.first.metremap // bit arbitrary
			} {
				MetreMap.new
			}
		};

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		// box updates
		boxes.notEmpty.if {
			boxes.values.do({ arg box, i;
				box.parentBoxMulti = this;
				box.calculatePositions; // based on parent metremap
			})
		};

		tpqn = metremap.tpqn;

		this.highlightAll;

		history = VoxHistory.new;
		history.commit(this.out, "init commit"); // check this ...

		^this
	}

	*fromVoxMulti { |voxMulti|
		var boxesArg;

		// Safety check
		if (voxMulti.isNil or: { voxMulti.voxes.isNil }) {
			"❌ Cannot create BoxMulti from nil voxMulti; empty BoxMulti returned".warn;
			^this.new;
		};

		boxesArg = voxMulti.voxes.values.collect { |vox|
			Box.new(vox.events, vox.metremap, vox.label);
		};

		boxesArg.do({ arg v; v.input })

		^BoxMulti.new(boxesArg, voxMulti.metremap, voxMulti.label);
	}

	normaliseRange { |rangeArg|
		var start, end;

		// Ensure it's a 2-element array-like input
		if (rangeArg.isKindOf(SequenceableCollection).not or: { rangeArg.size != 2 }) {
			"😬 Box.normaliseRange: Expected a 2-element array as input.";
			^[0, 0]
		};

		start = rangeArg[0];
		end = rangeArg[1];

		if (end < start) {
			var temp = start;
			start = end;
			end = temp
		};

		^[start, end]
	}

	setRangeAndPropagate { |rangeArg|
		var normRange = this.normaliseRange(rangeArg);
		this.setRange(normRange);
		this.propagateRangeToBoxes;
		^this
	}

	propagateRangeToBoxes {
		boxes.values.do{
			arg box;
			box.mirrorRangeFromMulti(range);
		}
	}

	// for internal use
	setRange { |rangeArg|
		range = rangeArg;
		^this
	}

	highlightAll {
		var starts, ends;

		if (this.isEmpty) {
			this.setRangeAndPropagate([0, 0]);
		} {
			starts = boxes.values.collect { arg v; v.earliestEventStart };
			ends = boxes.values.collect { arg v; v.latestEventEnd };
			this.setRangeAndPropagate([starts.minItem, ends.maxItem]);
		};

		^this
	}

	highlight { |startPos, endPos|
		var start = TimeConverter.posToTicks(startPos, metremap);
		var end   = TimeConverter.posToTicks(endPos, metremap);
		this.setRangeAndPropagate([start, end]);
		^this
	}

	mirrorVoxHighlight { |rangeArg|
		var normRange = this.normaliseRange(rangeArg);
		this.setRange(normRange);
		^this
	}

	highlighted {
		var start = range[0].toPos(metremap);
		var end   = range[1].toPos(metremap);
		^"% --> %".format(start, end);
	}

	isEmpty {
		^boxes.isEmpty or: {
			boxes.values.every(_.isEmpty)
		}
	}

	duration {
		^(range[1] - range[0]);
	}

	at { |key|
		var box = boxes[key];

		// THIS COULD BECOME PROBLEMATIC, RETURN TO THIS
		// RETURNING EMPTY VOX MIGHT BE BETTER DEFAULT
		// BEHAVIOUR
		if (box.isNil) {
			"BoxMulti.at: No Box found for key %; returning this BoxMulti".format(key).warn;
			^this;
		};

		// does a copy need to be made???
		box = box.deepCopy;
		box.parentBoxMulti = nil;
		box.highlightAll;
		^box;
	}


	clip {
		var clippedBoxes;

		clippedBoxes = boxes.collect({
			arg box;
			box.clipRange(range);
		});

		^BoxMulti.new(clippedBoxes, metremap, label);
	}

	merge { |source|
		var vox = source.respondsTo(\out).if {
			source.out
		} {
			source
		};

		var newBoxes = boxes.deepCopy;

		if (vox.isKindOf(Vox)) {
			// if exists, replaces, else creates
			newBoxes[vox.label] = Box.fromVox(vox);

			^BoxMulti.fromDict(newBoxes, vox.metremap, this.label);
		};

		// add boxmulti, check for matches, replace etc.
		if (vox.isKindOf(VoxMulti)) {
			vox.asArray.do({ |p|
				// if exists, replaces, else creates
				newBoxes[p.label] = Box.fromVox(p);
			});

			^BoxMulti.fromDict(newBoxes, vox.metremap, this.label);
		};

		"BoxMulti.merge: Unrecognized vox type %".format(vox.class).warn;
		^this
	}

	load { |source, strict = true|

		var vox = source.isKindOf(VoxNode).if {
			source.out
		} {
			source
		};

		// if empty and not strict, load
		this.boxes.isEmpty.if {
			if (strict.not and: { vox.isKindOf(Vox) }) {
				var tempBox = Box.fromVox(vox);
				boxes[tempBox.label] = tempBox;
				label = tempBox.label;
				metadata = tempBox.metadata;
				metremap = tempBox.metremap;
				tpqn = metremap.tpqn;

				boxes.notEmpty.if {
					boxes.values.do({
						arg box, i;
						box.parentBoxMulti = this;
						box.calculatePositions;
					})
				};

				this.highlightAll;
				this.commit;
				^this
			};

			if (strict.not and: { vox.isKindOf(VoxMulti) }) {
				var tempBox = BoxMulti.fromVoxMulti(vox, vox.label);
				boxes = tempBox.boxes;
				label = tempBox.label;
				metadata = tempBox.metadata;
				metremap = tempBox.metremap;
				tpqn = metremap.tpqn;

				boxes.notEmpty.if {
					boxes.values.do({
						arg box, i;
						box.parentBoxMulti = this;
						box.calculatePositions;
					})
				};

				this.highlightAll;
				this.commit;
				^this
			};

			"😬 BoxMulti(%).load: No match found, cannot load to empty BoxMulti in strict mode."
			.format(this.label).warn;
			^this
		};

		if (vox.isKindOf(Vox)) {
			strict.if {
				var match = boxes[vox.label];
				match.notNil.if {
					match.load(vox);
					if (range.isNil or: { this.duration == 0 }) {
						this.highlightAll
					};
					this.commit;
					^this
				} {
					"😬 BoxMulti(%).load: No matching Box found for label %"
					.format(this.label, vox.label).warn;
					^this
				}
			};

			boxes.values.first.load(vox);
			if (range.isNil or: { this.duration == 0 }) {
				this.highlightAll
			};
			this.commit;
			^this
		};

		// if strict and VoxMulti
		if (strict and: { vox.isKindOf(VoxMulti) }) {
			vox.voxes.do { |inputVox|
				var target = boxes[inputVox.label];
				if (target.notNil) {
					target.load(inputVox);
					// I guess if these are loaded to empty Boxes,
					// those Box ranges can't be updated ??
				} {
					"😬 BoxMulti(%): No match for vox label %"
					.format(this.label, inputVox.label).warn;
				}
			};
			if (range.isNil or: { this.duration == 0 }) {
				this.highlightAll;
				range.postln; // HERE IS A PROBLEM NOT SETTING RANGE WITH NEW EVENTS
			};
			this.commit;
			^this;
		};

		// if not strict and VoxMulti
		if (strict.not and: { vox.isKindOf(VoxMulti) }) {
			var limit;

			limit = [vox.size, boxes.size].minItem;

			"limit is: %".format(limit).postln;
			"vox size is: %".format(vox.size).postln;
			"boxes size is: %".format(boxes.size).postln;

			"voxes are: %".format(vox.voxes).postln;

			limit.do({
				arg i;
				var vox_vals = boxes.values;
				var plug_vals = vox.voxes.values;

				vox_vals[i].forceload(plug_vals[i]);
			});

			if (range.isNil or: { this.duration == 0 }) {
				this.highlightAll
			};
			this.commit;
			^this
		};

		// Catch-all fallback
		"😬 BoxMulti(%).load: Unrecognized source type %"
		.format(this.label, vox.class).warn;
		^this
	}

	forceload { |source|
		this.load(source, false);
		if (range.isNil or: { this.duration == 0 }) {
			this.highlightAll;
		};
		^this
	}

	loadFromDict { |boxesDict, metremapArg, labelArg|
		if (boxesDict.isNil or: { boxesDict.isEmpty }) {
			"❌ BoxMulti.loadFromDict: no boxes provided".warn;
			^this
		};

		^this.forceload(BoxMulti.fromDict(boxesDict, metremapArg, labelArg));
	}

	commit { |label = nil|
		history.commit(this.out, label);
	}

	undo {
		this.input = history.undo.vox;
	}

	redo {
		this.input = history.redo.vox;
	}

	out {
		var voxes = boxes.values.collect(_.out);

		if (input.notNil) {
			var voxMulti = input.out;
			^voxMulti.copy
		};

		^VoxMulti.new(voxes)
	}
}