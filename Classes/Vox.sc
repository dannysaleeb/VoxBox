/////////////////////////////////////////////////////////////////////////////
// Vox: a container for composed material, for further creative processing //
/////////////////////////////////////////////////////////////////////////////
Vox {
	var events, metremap, range, >tpqn;
	var <input;
	var <>label;
	var <>metadata;
	var <history;

	*new {
		arg midifile, metremap, label = \anonyvox;

		^super.new.init(midifile, metremap, label);
	}

	init {
		arg midifileArg, metremapArg, labelArg;
		var timesigArrs;

		// no midifile, set empty events, empty metremap if none specified.
		if (midifileArg.isNil) {
			events = List.new;
			metremap = metremapArg ?? MetreMap.new;
			label = labelArg;
			^this;
		};

		tpqn = midifileArg.division;

		// initialise metremap
		if (metremapArg.notNil) { metremap = metremapArg };

		// get metremap from time sig info
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

		// midifile present
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
		if (events.isEmpty.not) {
			var
			first = events.first,
			last = events.last;
			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
		};

		label = labelArg;

		history = VoxHistory.new;
		history.commit(this.out, "init commit");

		^this

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

		^Vox.newFromEventsArray(return, metremap);
    }

	load { |plug, label="loaded"|

		var startTick = plug.events.first[\absTime];
		var endTick = plug.events.last[\absTime] + plug.events.last[\dur];

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

		input = source;

		plug = source.respondsTo(\out).if {
			source.out;
		} {
			source
		};

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
		^VoxPlug.new(
			this.clip(this.range).events.deepCopy,
			metremap.copy,
			label,
			metadata.deepCopy
		)
	}
}

VoxMulti {
	var <voxes, <label, <metadata, <history, <range, <metremap, <>tpqn;
	var <input;

	*new { |voxes, metremap, label = \anonmulti|
		^super.new.init(voxes, metremap, label);
	}

	init { |voxesArg, metremapArg, labelArg|

		voxesArg.isNil.if { voxes = List.new } { voxes = voxesArg.deepCopy };
		label = labelArg;
		metadata = Dictionary.new;

		// Assign shared metremap
		if (metremapArg.isNil) {
			voxes.notEmpty.if { metremap = voxes.first.metremap.copy } { metremap = MetreMap.new }
		} {
			metremap = metremapArg.copy;
		};

		tpqn = metremap.tpqn;
		this.reassignMetreMaps;

		this.updateRange;
		history = VoxHistory.new;
		history.commit(this.out, "init commit");

		^this
	}

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
		voxes.do { |vox| vox.highlight(startPos, endPos) };
	}

	highlighted {
		var start = range[0].toPos(metremap);
		var end   = range[1].toPos(metremap);
		^"% --> %".format(start, end);
	}

	clip {
		var clippedVoxes = voxes.collect(_.clip);
		^VoxMulti.new(clippedVoxes, metremap, label);
	}

	load { |plugMulti, label="loaded"|
		if (plugMulti.isKindOf(VoxPlugMulti).not) {
			("VoxMulti.load: expected VoxPlugMulti, got %".format(plugMulti.class)).warn;
			^this
		};

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

	input_ { |source|
		var plug;

		input = source;

		plug = source.respondsTo(\out).if {
			source.out;
		} {
			source
		};

		if (plug.isKindOf(VoxPlugMulti)) {
			voxes = plug.asArray.collect { |p|
				Vox.newFromEventsArray(p.events.deepCopy, p.metremap)
			};
			metremap = voxes.first.metremap.copy;
			tpqn = metremap.tpqn;
			this.reassignMetreMaps;
			this.updateRange;
		}
	}

	out {
		var plugs = voxes.collect(_.out);
		^VoxPlugMulti.new(plugs)
	}
}
