TimeRange {
	var <start, <end;

	*new { |startTick = 0, endTick = 0|
		^super.new.init(startTick, endTick)
	}

	*fromTicks { |startTick = 0, endTick = 0|
		^this.new(startTick, endTick)
	}

	*fromPositions { |startPos, endPos, metremap|
		if (metremap.isNil) {
			"TimeRange.fromPositions requires a MetreMap.".warn;
			^this.new(0, 0)
		};

		^this.new(
			TimeConverter.posToTicks(startPos, metremap),
			TimeConverter.posToTicks(endPos, metremap)
		)
	}

	*from { |rangeArg, metremap|
		if (rangeArg.isKindOf(TimeRange)) {
			^rangeArg.copy
		};

		if (rangeArg.isKindOf(SequenceableCollection).not or: { rangeArg.size != 2 }) {
			"TimeRange.from: expected a two-item range.".warn;
			^this.new(0, 0)
		};

		if (rangeArg[0].isKindOf(Pos) or: { rangeArg[1].isKindOf(Pos) }) {
			if (rangeArg[0].isKindOf(Pos).not or: { rangeArg[1].isKindOf(Pos).not }) {
				"TimeRange.from: position ranges must contain two Pos values.".warn;
				^this.new(0, 0)
			};
			^this.fromPositions(rangeArg[0], rangeArg[1], metremap)
		};

		^this.fromTicks(rangeArg[0], rangeArg[1])
	}

	init { |startTick, endTick|
		startTick = startTick ? 0;
		endTick = endTick ? 0;

		if (startTick.isNumber.not or: { endTick.isNumber.not }) {
			"TimeRange requires numeric ticks.".warn;
			startTick = 0;
			endTick = 0;
		};

		startTick = startTick.asInteger;
		endTick = endTick.asInteger;

		if (endTick < startTick) {
			var temp = startTick;
			startTick = endTick;
			endTick = temp;
		};

		start = startTick;
		end = endTick;
		^this
	}

	duration {
		^end - start
	}

	isEmpty {
		^this.duration <= 0
	}

	contains { |tick|
		^((tick >= start) and: { tick < end })
	}

	overlaps { |other|
		other = TimeRange.from(other);
		^((start < other.end) and: { end > other.start })
	}

	intersection { |other|
		var interStart, interEnd;
		other = TimeRange.from(other);

		if (this.overlaps(other).not) { ^nil };

		interStart = start.max(other.start);
		interEnd = end.min(other.end);
		^TimeRange.fromTicks(interStart, interEnd)
	}

	asArray {
		^[start, end]
	}

	asPosRange { |metremap|
		^[TimeConverter.ticksToPos(start, metremap), TimeConverter.ticksToPos(end, metremap)]
	}

	copy {
		^TimeRange.fromTicks(start, end)
	}

	asString {
		^"TimeRange(%, %)".format(start, end)
	}
}
