/////////////////////////////////////////////////////////////////////////////
// Vox: a container for composed material, for further creative processing //
/////////////////////////////////////////////////////////////////////////////
Vox : VoxNode {
	var <events, <metremap, <range, <tpqn;
	var <history;
	var <>id;

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
		metremap.regions.isEmpty.if {
			metremap.add(MetreRegion.new(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		// sort events
		events.isEmpty.not.if {
			events = events.sortBy(\absTime)
		};

		// add Pos values to events
		events.do { |event|
			event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
		};

		// set highlight to span the entire voice.
		if (events.isEmpty.not) {
			this.setFullRange;
		};

		metadata = Dictionary.new;

		// history created with Vox, and snapshot of full Vox is init commit
		history = VoxHistory.new;
		history.commit(VoxPlug.new(events, metremap, label, metadata, this), "init commit");

		^this
	}

	// to utils
	setFullRange {
		var first = events.first;
		var last = events.last;
		range = [first.absTime, last.absTime + last.dur];
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

	// METREMAP SETTER (Update Event Positions)
	metremap_ { |mm|
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
		var rangeStart = range[0], rangeStartPos;
        var rangeEnd = range[1], rangeEndPos;
		var events, return = [], labelArg;

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
		labelArg = "%: %-->%".format(label, rangeStartPos, rangeEndPos).asSymbol;

		// clip from same vox, so same metremap? metremap is not changing
		^Vox.new(return, metremap, labelArg);
    }

	// FOR LATER > NEEDS WORK
	// load should load material provided label is the same?
	// load should load material in correct block, also
	// needs some careful consideration ... but deal with later.
	load { |plug, label="loaded"|

		var startTick = plug.events.first[\absTime];
		var endTick = plug.events.last[\absTime] + plug.events.last[\dur];

		// this line looks a little fragile, check it.
		events = events.reject { |e|
			e[\absTime] < endTick and: { (e[\absTime] + e[\dur]) > startTick }
		} ++ plug.events.deepCopy;

		events.sortBy(\absTime);

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

	// SORT INPUT

	// if something chained to Vox, should immediately load contents provided a single Vox
	// What happens if Vox is chained to Vox?
	input_ { |source|
		var plug;

		if (source.isKindOf(VoxNode)) {
			input = source;
		} {
			"❌ Cannot set % as input to Vox".format(source.class).warn;
			^source
		};

		// I think this should force source.out, not allow a direct plug connection?
		plug = source.out;
		(plug.isKindOf(VoxPlug).not or: { plug.isKindOf(VoxPlugMulti).not }).if {
			"❌ Expected VoxPlug or VoxPlugMulti, got %".format(plug.class).warn;
			^source;
		};

		if (plug.isKindOf(VoxPlugMulti)) {
			"❌ Cannot load multiple Voxes to one Vox, expected VoxPlug got %"
			.format(plug.class).warn;
			^source;
		} {
			// if not copying here, these refs were created for the VoxPlug
			events = plug.events;
			metremap = plug.metremap;
			tpqn = metremap.tpqn;
			label = plug.label;
			metadata = plug.metadata;
		};

		if (events.notEmpty) {
			events.do { |event|
				event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
			};

			range = [
				events.first[\absTime],
				events.last[\absTime] + events.last[\dur]
			];
		}
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
	} // out will always just yield important contents of Vox as plug
}

// I think VoxMulti should inherit from Vox
VoxMulti : VoxNode {
	var <voxes, <label, <metadata, <history, <range, <metremap, <>tpqn;
	var <input;

	*new { |voxes, metremap, label = \anonymulti|
		^super.new.init(voxes, metremap, label);
	}

	init { |voxesArg, metremapArg, labelArg|

		voxes = voxesArg ? List.new;
		label = labelArg;
		metadata = Dictionary.new;

		metremap = metremapArg ?? {
			voxes.notEmpty.if {
				voxes.first.metremap
			} {
				MetreMap.new
			}
		};

		// give each vox integer id in turn
		voxes.notEmpty.if {
			voxes.do({ arg vox, i;
				vox.id = i;
			})
		};

		// if anoymous labels, give meaningful ones in group context
		voxes.do({
			arg vox, i;
			if (vox.label.isPrefix(\anonyvox)) {
				vox.label = (vox.label ++ \_ ++ this.label ++ '(' ++ vox.id.asSymbol ++ ')').asSymbol;
			}
		});

		tpqn = metremap.tpqn;

		this.reassignMetreMaps;
		this.updateRange;

		history = VoxHistory.new;
		history.commit(this.out, "init commit");

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

	// this is a bit hacky I think ...
	reassignMetreMaps {
		voxes.do { |vox|
			if (vox.metremap != metremap) {
				vox.metremap = metremap.copy;
				vox.tpqn = metremap.tpqn;
				vox.events.do { |event|
					event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
				};
			}
		};
	}

	updateRange {
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

	clip {
		// this should clip according to all ranges, maybe should pull info from MultiVox.range
		var clippedVoxes = voxes.collect(_.clip);
		^VoxMulti.new(clippedVoxes, metremap, label);
	}

	// multiplug required to load to VoxMulti
	// no this can load to whichever voxes match, or do a forced load ...
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

	// should be .in to match .out
	input_ { |source|
		var plug;

		input = source; // assign source directly to input (whether plug or other)
		// could put in a safeguard? input should be: Vox type, or VoxModule type
		// only Vox, VoxModule and VoxPlayer types to think about.
		// later also VoxRouter and VoxSplitter? Both of those would respond to .in and .out

		// here plug is pulled or assumed
		plug = source.respondsTo(\out).if {
			source.out;
		} {
			source
		};

		// check for multi
		if (plug.isKindOf(VoxPlugMulti)) {
			// load plug's voxes to plug events
			// this is a bit confusing, it's just plug.plugs.collect ...
			voxes = plug.asArray.collect { |p|
				Vox.new(p.events, p.metremap)
			};
			// again metremap assigned from first vox, not ideal
			metremap = voxes.first.metremap.copy;
			tpqn = metremap.tpqn;
			this.reassignMetreMaps;
			this.updateRange;
		}
	}

	out {
		// maybe check this syntax works ok
		var plugs = voxes.collect(_.out);
		// this is v straightforward, it just holds an array of plugs...
		^VoxPlugMulti.new(plugs)
	}
}