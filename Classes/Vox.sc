/////////////////////////////////////////////////////////////////////////////
// Vox: a container for composed material, for further creative processing //
/////////////////////////////////////////////////////////////////////////////
Vox : VoxNode {
	var events, metremap, range, >tpqn;
	var <history;
	var <>id;

	*new {
		arg events, metremap, label = \anonyvox;

		^super.new.init(events, metremap, label);
	}

	init { |eventsArg, metremapArg, labelArg|
		events = eventsArg ?? [];
		label = labelArg ?? \anonyvox;
		metremap = metremapArg ?? MetreMap.new;
		tpqn = this.metremap.tpqn;

		events.isEmpty.not.if {
			events = events.sortBy(\absTime)
		};

		events.do { |event|
			event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
		};

		// set highlight to span the entire voice.
		// farm out to this.updateRange later
		if (events.isEmpty.not) {
			var
			first = events.first,
			last = events.last;
			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
		};

		metadata = Dictionary.new;
		/*history = VoxHistory.new;
		history.commit(this.out, "init commit");*/

		^this
	}

	*fromMIDI {
		arg midifile, label = \anonyvox;

		^super.new.initFromMIDI(midifile, label)
	}

	initFromMIDI {
		arg midifileArg, labelArg;
		var timesigArrs;

		tpqn = midifileArg.division;

		// get metremap from time sig info
		// define fromTimeSigEvents on MetreMap later
		timesigArrs = midifileArg.timeSignatureEvents;
		"timesigArrs was %".format(timesigArrs).postln;
		metremap = MetreMap.new;
		// for each timeSig, create MetreRegion entry
		timesigArrs.do({
			arg timeSigArr;
			metremap.add(MetreRegion(
				timeSigArr[0],
				TimeConverter.midiTimeSigToMetre(timeSigArr.last, tpqn))
			);
		});

		// all of this ought to be packaged up in a method
		events = midifileArg.noteSustainEvents.collect({
			arg nse;
			var event;

			event = Event.newFrom([[\track, \absTime, \midicmd, \channel, \midinote, \velocity, \dur, \upVelo], nse].lace);
		});
		// sort
		events.sortBy(\absTime);
		// add pos
		events.do({
			arg event;
			event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
		});

		// set highlight to span the entire voice.
		// farm out to this.updateRange later
		if (events.isEmpty.not) {
			var
			first = events.first,
			last = events.last;
			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
			"range is: %".format(range).postln;
		};

		label = labelArg;

		metadata = Dictionary.new;
		/*history = VoxHistory.new;
		history.commit(this.out, "init commit");*/

		^this
	}

	*fromPlug {
		arg plug;
		var vox = this.new(
			plug.events,
			plug.metremap,
			plug.label
		);
		vox.postln;
		plug.respondsTo(\source).if {
			vox.metadata[\source] = plug.source;
		};
		^vox
	}

	events { ^events.deepCopy }
	metremap { ^metremap.deepCopy }
	range { ^range.deepCopy }
	tpqn { ^tpqn }

	*newFromEventsArray {
		arg events, metremap;
		var range;

		// make sure they're sorted
		events.sortBy(\absTime);

		// set range
		if (events.isEmpty) { nil }
		{
			var
			first = events[0],
			last = events[events.size-1];

			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
		};

		^super.newCopyArgs(events, metremap, range);
		// something doesn't feel quite right about this, should it call init?
	}

	highlight { |startPos, endPos|
		var start, end;
		start = TimeConverter.posToTicks(startPos, metremap);
		end = TimeConverter.posToTicks(endPos, metremap);
		^range = [start, end];
	}

	highlighted {
		^"% --> %".format(range[0].toPos(metremap), range[1].toPos(metremap))
	}

	metremap_ { |mm|
		metremap = mm.copy;
		tpqn = mm.tpqn;

		if (events.notEmpty) {
			events.do { |event|
				event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap);
			}
		}
	}

	clip {
		var rangeStart = range[0];
        var rangeEnd = range[1];
		var events, return = [];

		// copy events
		events = this.events.deepCopy;
		\fine_one.postln;

		return = events.select({
			arg event;

			var eventStart = event[\absTime]; // gets events tick position
			var eventEnd = eventStart + event[\dur];

			(eventStart < rangeEnd) and: (eventEnd > rangeStart);

		}).do({
			arg event;

			var eventStart = event[\absTime]; // gets events tick position
			var eventEnd = eventStart + event[\dur];

			if ((eventStart < rangeStart) && (eventEnd > rangeStart)) {
				event[\dur] = eventEnd - rangeStart;
				event[\absTime] = rangeStart;
			};

			if ((eventStart < rangeEnd) && (eventEnd > rangeEnd)) {
				event[\dur] = rangeEnd - eventStart;
			}
		});
		"return is:".postln;
		return.postln;

		^Vox.new(return, metremap, \clipped);
    }

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

	input_ { |source|
		var plug;

		input = source; // again, no safeguard here - improve

		plug = source.respondsTo(\out).if {
			source.out;
		} {
			source
		};

		// loads information from plug straight to vox on input (it 'loads' actually)
		// there might be a confusion around naming methods (check 'load', maybe 'loadSection' better)
		if (plug.isKindOf(VoxPlug)) {
			events = plug.events.deepCopy;
			metremap = plug.metremap.copy;
			tpqn = metremap.tpqn;
			label = plug.label;
			metadata = plug.metadata.deepCopy;
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
		// just returns a plug populated with correct info
		^VoxPlug.new(
			this.clip(this.range).events,
			metremap.copy,
			label,
			metadata.deepCopy,
			this
		)
	}
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

		// Assign shared metremap
		// This feels a bit dodgy, but no other way?

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
				Vox.newFromEventsArray(p.events.deepCopy, p.metremap)
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

// Vox and VoxMulti could be built from MIDI file, in which case take metre from midi.
// Otherwise, metremap needs to be passed  ... so a VoxPlugMulti needs a metremap? It should have a master metremap, so child voxes pull from that as needed.

// Voxes are static, when .out is called, they simply present their data as 'plugs'
// VoxModules are dynamic, when .out is called, they run their process on incoming data and present relevant events etc. as plugs
// VoxPlayer is a sink, it doesn't have a .out - it plays.
// VoxSplitter will take a VoxPlugMulti and map child plugs either as VoxPlugs or bundled as VoxPlugMultis (in both cases holding all relevant information, including label for each plug, as well as label for Multi) to key symbols. .out enacts this process dynamically, pulling from .in - and presenting ? a VoxPatchBranch or similar ...
// VoxRouter takes VoxPatchBranch as input, and routes to wherever, ultimately yields a VoxPlugMulti ... need to make sure the plugs line up with correct keys/labels.