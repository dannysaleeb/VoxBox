/////////////////////////////////////////////////////////////////////////////
// Vox: a container for composed material, for further creative processing //
/////////////////////////////////////////////////////////////////////////////
Vox : VoxNode {
	classvar idCounter=0;
	var <events, metremap, range, <tpqn;
	var <history;
	var <>id;
	var <>parentVoxMulti;

	assignId {
		id = idCounter;
		idCounter = idCounter + 1;
	}

	// TODO: JUST CHECK METREMAP AUTOMATICALLY CALLS THE GETTER ... ELSE this.metremap NEEDED

	//////////////////
	// CONSTRUCTORS //
	//////////////////
	*new {
		arg events, metremap, label = \anonyvox;

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

		// history created with Vox, and snapshot of full Vox is init commit
		history = VoxHistory.new;
		history.commit(VoxPlug.new(events, metremap, label, metadata, this), "init commit");

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
			"😬 Vox.normaliseRange: Expected a 2-element array as input.";
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

	setRange { |rangeArg|
		range = rangeArg;
		^this
	}

	setNormRangeAndNotify { |rangeArg|
		var normRange = this.normaliseRange(rangeArg);
		this.setRange(normRange); // internal assignment

		parentVoxMulti.notNil.if {
			parentVoxMulti.mirrorVoxHighlight(normRange); // implement this on VoxMulti
		};

		^this
	}

	// for individual voxes - obey method
	// Called by VoxMulti
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
			// set range on Vox, and check for parent VoxMulti
			this.setNormRangeAndNotify(rangeArg);
		};

		^this
	}

	isEmpty {
		^events.isEmpty
	}

	*fromMIDI {
		arg midifile, label = \anonyvox;

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
		history.commit(VoxPlug.new(events, metremap, label, metadata, this), "init commit");

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

	*fromPlug {
		arg plug;
		var vox = this.new(
			plug.events,
			plug.metremap,
			plug.label
		);
		plug.respondsTo(\source).if {
			vox.metadata[\source] = plug.source;
		};
		^vox
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

	// Only called from a VoxMulti, when VoxMulti highlighted
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
		parentVoxMulti.notNil.if {
			^parentVoxMulti.metremap
		} {
			^metremap
		}
	}

	metremap_ { |mm|
		if (parentVoxMulti.notNil) {
			"😬 Cannot set metremap directly on a Vox inside a VoxMulti".warn;
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
		^"Vox(%)".format(label);
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
		var vox;

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

		vox = Vox.new(return, metremap, label);
		vox.metadata[\clip_origin] = this;

		^vox;
	}

	load { |source, strict=true|

		var plug, startTick, endTick;

		// resolve plug
		plug = source.isKindOf(VoxNode).if {
			source.out
		} {
			source // assume VoxPlug
		};

		if (plug.isKindOf(VoxPlug).not) {
			"❌ Vox.load: Expected VoxPlug or VoxNode with .out, got %"
			.format(plug.class).warn;
			^this
		};

		// is there ever a situation where I want to load VoxMulti?

		if (strict and: { plug.label != this.label }) {
			"❌ Vox.load: Plug label % ≠ Vox label %"
			.format(plug.label, this.label).warn;
			^this
		};

		// otherwise process
		startTick = plug.events.first[\absTime];
		endTick = plug.events.last[\absTime] + plug.events.last[\dur];

		events = events.reject { |e|
			e[\absTime] < endTick and: { (e[\absTime] + e[\dur]) > startTick }
		} ++ plug.events;

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
			"❌ Vox.loadFromEvents: No events provided".warn;
			^this
		};

		^this.forceload(Vox.new(eventsArg, metremapArg, labelArg));
	}

	commit { |label = nil|

		parentVoxMulti.isNil.if {
			history.commit(this.out, label);
		};

		"❌ Vox(%).commit: cannot commit to Vox history from within VoxMulti. Use VoxMulti.commit instead.".format(this.label).warn;
	}

	undo {
		parentVoxMulti.isNil.if {
			this.input = history.undo.plug;
		};

		"❌ Vox(%).commit: cannot undo Vox history from within VoxMulti. Use VoxMulti.undo instead.".format(this.label).warn;
	}

	redo {
		parentVoxMulti.isNil.if {
			this.input = history.redo.plug;
		};

		"❌ Vox(%).commit: cannot undo Vox history from within VoxMulti. Use VoxMulti.undo instead.".format(this.label).warn;
	}

	out {
		// Everything copied in VoxPlug
		^VoxPlug.new(
			this.clip(this.range).events,
			metremap,
			label,
			metadata,
			this
		)
	}
}

VoxMulti : VoxNode {
	var <voxes, <history, range, <metremap, <>tpqn;

	*new { |voxes, metremap, label|
		^super.new.init(voxes, metremap, label);
	}

	init { |voxesArg, metremapArg, labelArg|

		voxes = voxesArg ? Dictionary.new;
		label = labelArg ? \anonyvoxmulti;
		metadata = Dictionary.new;

		metremap = metremapArg ?? {
			voxes.notEmpty.if {
				voxes.values.first.metremap // bit arbitrary
			} {
				MetreMap.new
			}
		};

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		// vox updates
		voxes.notEmpty.if {
			voxes.do({ arg vox, i;
				vox.parentVoxMulti = this;
				vox.calculatePositions; // based on parent metremap
			})
		};

		tpqn = metremap.tpqn;

		this.highlightAll;

		history = VoxHistory.new;
		history.commit(this.out, "init commit"); // check this ...

		^this
	}

	*fromMIDI {
		// TODO: implement
	}

	initFromMIDI {
		// TODO: implement
		// get tracks and tracknames ...
	}

	*fromPlugMulti { |plugMulti|
		var voxes;

		// Safety check
		if (plugMulti.isNil or: { plugMulti.plugs.isNil }) {
			"❌ Cannot create VoxMulti from nil plugMulti; empty VoxMulti returned".warn;
			^this.new(Dictionary.new, MetreMap.new, plugMulti.label);
		};

		voxes = plugMulti.plugs.values.collect { |plug|
			Vox.new(plug.events, plug.metremap, plug.label);
		};

		^VoxMulti.new(voxes, plugMulti.metremap, plugMulti.label);
	}

	normaliseRange { |rangeArg|
		var start, end;

		// Ensure it's a 2-element array-like input
		if (rangeArg.isKindOf(SequenceableCollection).not or: { rangeArg.size != 2 }) {
			"😬 Vox.normaliseRange: Expected a 2-element array as input.";
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
		this.propagateRangeToVoxes;
		^this
	}

	propagateRangeToVoxes {
		voxes.do{
			arg vox;
			vox.mirrorRangeFromMulti(this.range);
		}
	}

	// for internal use
	setRange { |rangeArg|
		range = rangeArg;
		^this
	}

	highlightAll {
		var starts, ends;

		// maybe just check this is best emptiness check
		if (voxes.isEmpty) {
			range = [0, 0];
		} {
			starts = voxes.values.collect { arg v; v.earliestEventStart };
			ends = voxes.values.collect { arg v; v.latestEventEnd };
			this.setRangeAndPropagate([starts, ends]);
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
		^voxes.isEmpty;
	}

	duration {
		^(range[1] - range[0]);
	}

	// IS THIS THE WAY TO DEAL WITH RANGE??
	at { |key|
		var vox = voxes[key].deepCopy;
		vox.parentVoxMulti = nil;
		vox.highlightAll;
		^vox
	}

	// this works well, referencing this.range and using clipRange on voxes
	clip {
		var clippedVoxes = Dictionary.new;

		voxes.do({
			arg vox;
			clippedVoxes[vox.label] = vox.clipRange(range);
		});

		^VoxMulti.new(clippedVoxes, metremap, label);
	}

	load { |source, strict = true|

		var plug = source.isKindOf(VoxNode).if {
			source.out
		} {
			source
		};

		// if empty and not strict, load
		this.isEmpty.if {
			if (strict.not and: { plug.isKindOf(VoxPlug) }) {
				var tempVox = Vox.fromPlug(plug);
				voxes[tempVox.label] = tempVox;
				label = tempVox.label;
				metadata = tempVox.metadata;
				metremap = tempVox.metremap;
				tpqn = metremap.tpqn;

				voxes.notEmpty.if {
					voxes.do({
						arg vox, i;
						vox.parentVoxMulti = this;
						vox.calculatePositions;
					})
				};

				this.highlightAll;
				this.commit;
				^this
			};

			if (strict.not and: { plug.isKindOf(VoxPlugMulti) }) {
				var tempVox = VoxMulti.fromPlugMulti(plug, plug.label);
				voxes = tempVox.voxes;
				label = tempVox.label;
				metadata = tempVox.metadata;
				metremap = tempVox.metremap;
				tpqn = metremap.tpqn;

				voxes.notEmpty.if {
					voxes.do({
						arg vox, i;
						vox.parentVoxMulti = this;
						vox.calculatePositions;
					})
				};

				this.highlightAll;
				this.commit;
				^this
			};

			"😬 VoxMulti(%).load: No match found, cannot load to empty VoxMulti in strict mode."
			.format(this.label).warn;
			^this
		};

		if (plug.isKindOf(VoxPlug)) {
			strict.if {
				var match = voxes[plug.label];
				match.notNil.if {
					match.load(plug);
					if (range.isNil or: { this.duration == 0 }) {
						this.highlightAll
					};
					this.commit;
					^this
				} {
					"😬 VoxMulti(%).load: No matching Vox found for label %"
					.format(this.label, plug.label).warn;
					^this
				}
			};

			voxes.values.first.load(plug);
			if (range.isNil or: { this.duration == 0 }) {
				this.highlightAll
			};
			this.commit;
			^this
		};

		// if strict and PlugMulti
		if (strict and: { plug.isKindOf(VoxPlugMulti) }) {
			plug.plugs.do { |inputPlug|
				var target = voxes[inputPlug.label];
				if (target.notNil) {
					target.load(inputPlug);
					// I guess if these are loaded to empty Voxes,
					// those Vox ranges can't be updated ??
				} {
					"😬 VoxMulti(%): No match for plug label %"
					.format(this.label, inputPlug.label).warn;
				}
			};
			if (range.isNil or: { this.duration == 0 }) {
				this.highlightAll;
				range.postln; // HERE IS A PROBLEM NOT SETTING RANGE WITH NEW EVENTS
			};
			this.commit;
			^this;
		};

		// if not strict and PlugMulti
		if (strict.not and: { plug.isKindOf(VoxPlugMulti) }) {
			var limit;

			limit = [plug.size, voxes.size].minItem;

			limit.do({
				arg i;
				voxes.values[i].forceload(plug.plugs[i]);
			});

			if (range.isNil or: { this.duration == 0 }) {
				this.highlightAll
			};
			this.commit;
			^this
		};

		// Catch-all fallback
		"😬 VoxMulti(%).load: Unrecognized source type %"
		.format(this.label, plug.class).warn;
		^this
	}

	forceload { |source|
		this.load(source, false);
		if (range.isNil or: { this.duration == 0 }) {
			this.highlightAll;
		};
		^this
	}

	loadFromDict { |voxesDict, metremapArg, labelArg|
		if (voxesDict.isNil or: { voxesDict.isEmpty }) {
			"❌ VoxMulti.loadFromDict: no voxes provided".warn;
			^this
		};

		^this.forceload(VoxMulti.new(voxesDict, metremapArg, labelArg));
	}

	commit { |label = nil|
		history.commit(this.out, label);
	}

	undo {
		this.input = history.undo.plug;
	}

	redo {
		this.input = history.redo.plug;
	}

	out {
		var plugs = voxes.values.collect(_.out);
		^VoxPlugMulti.new(plugs)
	}
}