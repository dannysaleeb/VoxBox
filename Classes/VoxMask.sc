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

	voxSpan { |vox|
		var starts, ends, stored;

		stored = vox.metadata[\spanTicks];
		if (stored.notNil) { ^stored.deepCopy };

		if (vox.events.isEmpty) { ^[0, 0] };

		starts = vox.events.collect(_[\absTime]);
		ends = vox.events.collect { |event| event[\absTime] + event[\dur] };
		^[starts.minItem, ends.maxItem]
	}

	materialSpan { |material|
		var spans, starts, ends;

		if (material.isKindOf(Vox)) {
			^this.voxSpan(material)
		};

		if (material.isKindOf(VoxMulti)) {
			spans = material.asArray.collect { |vox| this.voxSpan(vox) };
			if (spans.isEmpty) { ^[0, 0] };
			starts = spans.collect(_[0]);
			ends = spans.collect(_[1]);
			^[starts.minItem, ends.maxItem]
		};

		^[0, 0]
	}

	filterVox { |vox, source, span, scope|
		var metadata = vox.metadata.deepCopy;
		metadata[\spanTicks] = span.deepCopy;

		if (this.isEmpty) {
			^Vox.new(vox.events, vox.metremap, vox.label, metadata, source)
		};
		^Vox.new(
			vox.events.select { |event|
				scope.notNil.if {
					scope.contains(event[\absTime]).if {
						this.includesEvent(event)
					} {
						true
					}
				} {
					this.includesEvent(event)
				}
			},
			vox.metremap,
			vox.label,
			metadata,
			source
		)
	}

	filter { |material, source, scope|
		var span = this.materialSpan(material);

		if (material.isKindOf(Vox)) {
			^this.filterVox(material, source, span, scope)
		};

		if (material.isKindOf(VoxMulti)) {
			^VoxMulti.new(
				material.asArray.collect { |vox| this.filterVox(vox, source, span, scope) },
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
	var <rangeArg, <scopeArg;

	*new { |sourceOrRanges, ranges|
		var source, mask;

		if (sourceOrRanges.isKindOf(Fragments) and: { ranges.notNil }) {
			source = nil;
			mask = sourceOrRanges.select(ranges);
		} {
			if (ranges.isNil and: { this.looksLikeSource(sourceOrRanges).not }) {
				source = nil;
				mask = sourceOrRanges;
			} {
				source = sourceOrRanges;
				mask = ranges;
			}
		};

		^super.new.init(source, mask)
	}

	*looksLikeSource { |object|
		if (object.isNil) { ^false };
		^object.respondsTo(\out).or({
			object.isKindOf(Vox).or({
				object.isKindOf(VoxMulti)
			})
		})
	}

	init { |source, ranges|
		input = source;
		rangeArg = ranges;
		label = \mask;
		metadata = Dictionary.new;
		^this
	}

	atRange { |range|
		scopeArg = range;
		this.touch;
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

	scopedResolutionSource { |material, scope|
		var events = [
			(absTime: scope.start, dur: 0),
			(absTime: scope.end, dur: 0)
		];

		^Vox.new(events, material.metremap, \maskScope)
	}

	out {
		var material, list, scope, resolutionSource;

		if (input.isNil) {
			"VoxMask: no input set.".warn;
			^nil
		};

		material = input.out;
		if (material.isNil) { ^nil };

		scope = scopeArg.notNil.if {
			TimeRange.from(scopeArg, material.metremap)
		} {
			nil
		};
		resolutionSource = scope.notNil.if {
			this.scopedResolutionSource(material, scope)
		} {
			material
		};

		list = TimeRangeList.from(rangeArg, material.metremap, resolutionSource);
		^VoxProvenance.stampTree(
			list.filter(material, this, scope),
			VoxProvenance.node(
				\mask,
				(
					ranges: rangeArg,
					scope: scopeArg
				),
				material.provenance
			)
		)
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
