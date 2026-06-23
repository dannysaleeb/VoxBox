TimeRangeList {
	var <ranges;

	*new { |ranges|
		^super.new.init(ranges)
	}

	*from { |rangeArg, metremap, source|
		if (rangeArg.isNil) { ^this.new([]) };
		if (rangeArg.isKindOf(TimeRangeList)) { ^rangeArg.copy };
		if (rangeArg.respondsTo(\resolve)) {
			^rangeArg.resolve(source, metremap)
		};
		if (rangeArg.isKindOf(TimeRange)) { ^this.new([rangeArg]) };

		if (rangeArg.isKindOf(SequenceableCollection)) {
			if (rangeArg.size == 2 and: {
				rangeArg[0].isKindOf(Pos) or: { rangeArg[0].isNumber }
			}) {
				^this.new([TimeRange.from(rangeArg, metremap)])
			};

			^this.new(rangeArg.collect { |range|
				TimeRange.from(range, metremap)
			})
		};

		"TimeRangeList.from: unsupported range spec.".warn;
		^this.new([])
	}

	init { |rangesArg|
		ranges = (rangesArg ? []).collect(_.copy);
		^this
	}

	copy {
		^TimeRangeList.new(ranges.collect(_.copy))
	}

	isEmpty {
		^ranges.isEmpty
	}

	contains { |tick|
		^ranges.any { |range| range.contains(tick) }
	}

	includesEvent { |event|
		^this.contains(event[\absTime])
	}

	filterVox { |vox, source|
		if (this.isEmpty) { ^vox.copy };
		^Vox.new(
			vox.events.select { |event| this.includesEvent(event) },
			vox.metremap,
			vox.label,
			vox.metadata,
			source
		)
	}

	filter { |material, source|
		if (material.isKindOf(Vox)) {
			^this.filterVox(material, source)
		};

		if (material.isKindOf(VoxMulti)) {
			^VoxMulti.new(
				material.asArray.collect { |vox| this.filterVox(vox, source) },
				material.metremap,
				material.label,
				material.metadata,
				source
			)
		};

		^material
	}
}

VoxMask : VoxNode {
	var <rangeArg;

	*new { |source, ranges|
		^super.new.init(source, ranges)
	}

	init { |source, ranges|
		input = source;
		rangeArg = ranges;
		label = \mask;
		metadata = Dictionary.new;
		^this
	}

	range_ { |ranges|
		rangeArg = ranges;
		this.touch;
		^this
	}

	mask { |ranges|
		^this.range_(ranges)
	}

	playSections { |ranges|
		^this.mask(ranges)
	}

	clearMask {
		rangeArg = nil;
		this.touch;
		^this
	}

	out {
		var material = input.out;
		var list;

		if (material.isNil) { ^nil };

		list = TimeRangeList.from(rangeArg, material.metremap, material);
		^list.filter(material, this)
	}
}

+ VoxNode {
	mask { |ranges|
		^VoxMask.new(this, ranges)
	}

	playSections { |ranges|
		^this.mask(ranges)
	}

	clearMask {
		^this
	}
}
