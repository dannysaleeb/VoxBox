/////////////////////////////////////////////////////////////////////////////
// Vox: a container for composed material, for further creative processing //
/////////////////////////////////////////////////////////////////////////////
Vox : VoxNode {
	classvar idCounter=0;
	var <events, metremap, <range, <tpqn;
	var <history;
	var <>id;
	var <>multiIndex;
	var <>parentVoxMulti;

	assignId {
		id = idCounter;
		idCounter = idCounter + 1;
	}

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
		if (events.isEmpty.not) {
			this.setFullRange;
		};

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
			e[\position] = TimeConverter.ticksToPos(e[\absTicks], metremap);
		}
	}

	// to utils
	setFullRange {
		var first = events.first;
		var last = events.last;
		range = [first.absTime, last.absTime + last.dur];
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

		// set highlight to span the entire voice.
		// farm out to this.updateRange later
		if (events.isEmpty.not) {
			this.setFullRange;
		};

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

		range = [start, end];

		^this;
	}

	highlighted {
		^"% --> %".format(range[0].toPos(metremap), range[1].toPos(metremap))
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
				event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
			}
		}
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

		^this
	}

	forceload { |source|
		^this.load(source, false);
	}

	loadFromEvents { |eventsArg, metremap, label|
		if (eventsArg.isNil or: { eventsArg.isEmpty }) {
			"❌ Vox.loadFromEvents: No events provided".warn;
			^this
		}

		^this.forceload(Vox.new(eventsArg, metremap, label));
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

// I think VoxMulti should inherit from Vox
VoxMulti : VoxNode {
	var <voxes, <label, <metadata, <history, <range, <metremap, <>tpqn;

	*new { |voxes, metremap, label = \anonyvoxmulti|
		^super.new.init(voxes, metremap, label);
	}

	init { |voxesArg, metremapArg, labelArg|

		voxes = voxesArg ? Dictionary.new;
		label = labelArg;
		metadata = Dictionary.new;

		metremap = metremapArg ?? {
			voxes.notEmpty.if {
				voxes.first.metremap
			} {
				MetreMap.new
			}
		};

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		// vox updates: multiIndex and parentVoxMulti
		voxes.notEmpty.if {
			voxes.do({ arg vox, i;
				vox.multiIndex = i;
				vox.parentVoxMulti = this;
				vox.calculatePositions; // based on parent metremap
			})
		};

		// if anonymous labels, give meaningful ones in group context
		voxes.do({
			arg vox, i;
			if (vox.label.isPrefix(\anonyvox)) {
				vox.label = (vox.label ++ \_ ++ this.label ++ '(' ++ vox.multiIndex.asSymbol ++ ')').asSymbol;
			}
		});

		tpqn = metremap.tpqn;

		this.setFullRange;

		history = VoxHistory.new;
		history.commit(this.out, "init commit"); // check this ...

		^this
	}

	*fromPlugMulti { |plugMulti, label = \anonymulti|
		var voxes;

		// Safety check
		if (plugMulti.isNil or: { plugMulti.plugs.isNil }) {
			"❌ Cannot create VoxMulti from nil plugMulti.".warn;
			^this.new([], MetreMap.new, label);
		};

		voxes = plugMulti.plugs.collect { |plug|
			// Attempt to recover original Vox if embedded
			if (plug.respondsTo(\source) and: { plug.source.isKindOf(Vox) }) {
				plug.source  // reattach original Vox (live!)
			} {
				// Otherwise, fallback: make a frozen Vox from the plug
				Vox.fromPlug(plug)  // you can optionally tag this as frozen
			}
		};

		^VoxMulti.new(voxes, nil, label);
	}

	setFullRange {
		var starts, ends;

		if (voxes.isEmpty) {
			range = [0, 0];
		} {
			starts = voxes.collect { |v| v.range[0] };
			ends   = voxes.collect { |v| v.range[1] };
			range = [starts.minItem, ends.maxItem];
		};
	}

	highlight { |startPos, endPos|
		var start = TimeConverter.posToTicks(startPos, metremap);
		var end   = TimeConverter.posToTicks(endPos, metremap);
		range = [start, end];
		// need to perhaps look at how this controls all child vox data?
		// I wonder if individual voxes ought to pull from parent MultiVox as needed if .range is called on an individual vox, or if sent elsewhere ...
		voxes.do { |vox| vox.highlight(startPos, endPos) };
	}

	highlighted {
		var start = range[0].toPos(metremap);
		var end   = range[1].toPos(metremap);
		^"% --> %".format(start, end);
	}

	isEmpty {
		^voxes.isEmpty;
	}

	clip {
		var clippedVoxes = Dictionary.new;

		voxes.do({
			arg vox;
			clippedVoxes[vox.label] = vox.clipRange(range);
		});

		^VoxMulti.new(clippedVoxes, metremap, label);
	}

	// multiplug required to load to VoxMulti
	// no this can load to whichever voxes match, or do a forced load ...
	// THIS NEEDS A PROPER LOOK AT HOW VOICES WILL MATCH ETC
	load { |plugMulti, label="loaded"|
		if (plugMulti.isKindOf(VoxPlugMulti).not) {
			("VoxMulti.load: expected VoxPlugMulti in %, got %".format(this.label, plugMulti.class)).warn;
			^this
		};

		// for each plug, load to corresponding vox ...
		// this assumes plugs line up with voxes
		// could there be a better way of labelling inputs (dict with common keys?)
		plugMulti.do { |plug, i|
			var vox = voxes[i];
			if (vox.notNil) {
				vox.load(plug, label);
			}
		};

		this.updateRange;
		^this
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
		var plugs = voxes.collect(_.out);
		^VoxPlugMulti.new(plugs)
	}
}