Fragments {
	var <unit, <indices;

	*new { |unit = \eighth, indices|
		^super.new.init(unit, indices)
	}

	init { |unitArg, indicesArg|
		unit = unitArg;
		indices = indicesArg ? [];
		^this
	}

	at { |... selected|
		^Fragments.new(unit, selected.flat)
	}

	select { |selected|
		^Fragments.new(unit, selected.flat)
	}

	add { |index|
		indices = indices.add(index);
		^this
	}

	unitTicks { |metremap|
		if (unit == \quarter) {
			^TimeConverter.posToTicks(Pos(beat: 1), metremap)
		};
		if (unit == \eighth) {
			^TimeConverter.posToTicks(Pos(division: 2), metremap)
		};
		if (unit == \sixteenth) {
			^TimeConverter.posToTicks(Pos(division: 1), metremap)
		};

		if (unit.isNumber) {
			^(metremap.tpqn * unit * 4).asInteger
		};

		"Fragments: unknown unit %, using eighths.".format(unit).warn;
		^TimeConverter.posToTicks(Pos(division: 2), metremap)
	}

	sourceSpan { |source|
		var events, starts, ends;

		if (source.isKindOf(Vox)) {
			events = source.events;
		};
		if (source.isKindOf(VoxMulti)) {
			events = source.asArray.collect(_.events).flat;
		};

		if (events.isNil or: { events.isEmpty }) {
			^[0, 0]
		};

		starts = events.collect(_[\absTime]);
		ends = events.collect { |event| event[\absTime] + event[\dur] };
		^[starts.minItem, ends.maxItem]
	}

	resolve { |source, metremap|
		var span = this.sourceSpan(source);
		var cellTicks = this.unitTicks(metremap);
		var ranges = indices.collect { |index|
			var start = span[0] + (index * cellTicks);
			TimeRange.fromTicks(start, start + cellTicks)
		};

		^TimeRangeList.new(ranges)
	}
}

Eighths {
	var <indices;

	*new {
		^super.new.init
	}

	init {
		indices = [];
		^this
	}

	add { |index|
		indices = indices.add(index);
		^this
	}

	resolve { |source, metremap|
		^Fragments(\eighth).select(indices).resolve(source, metremap)
	}
}
