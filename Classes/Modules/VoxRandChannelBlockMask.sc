VoxRandChannelBlockMask : VoxRandChannelBlock {
	var <division, <boundaries;

	*new { |blocks, weights, sourceChannels, division, boundaries, seed|
		^super.new(blocks, weights, sourceChannels, seed).initChannelBlockMask(division, boundaries)
	}

	initChannelBlockMask { |divisionArg, boundariesArg|
		division = divisionArg ? Pos(1);
		boundaries = boundariesArg;
		^this
	}

	division_ { |value| division = value; this.touch }
	boundaries_ { |value| boundaries = value; this.touch }

	provenanceSpec {
		^(
			op: \randomChannelBlockMask,
			params: (
				blocks: blocks,
				weights: weights,
				sourceChannels: sourceChannels,
				division: VoxProvenance.posValue(division),
				boundaries: boundaries.notNil.if {
					boundaries.collect { |value| VoxProvenance.posValue(value) }
				} { nil },
				seed: seed
			)
		)
	}

	ticksFor { |value, metremap|
		if (value.isKindOf(Pos)) { ^TimeConverter.posToTicks(value, metremap) };
		if (value.isNumber) { ^value };
		^nil
	}

	boundaryTicksFor { |metremap|
		var ticks;

		if (boundaries.isNil) { ^nil };
		ticks = boundaries.asArray.collect { |boundary|
			this.ticksFor(boundary, metremap)
		};
		if (ticks.includes(nil)) {
			"VoxRandChannelBlockMask: boundaries must contain only tick numbers or Pos values.".warn;
			^nil
		};
		^ticks.asSet.asArray.sort
	}

	windowSpecFor { |metremap|
		var boundaryTicks, divisionTicks;

		if (boundaries.notNil) {
			boundaryTicks = this.boundaryTicksFor(metremap);
			if (boundaryTicks.isNil or: { boundaryTicks.isEmpty }) {
				"VoxRandChannelBlockMask: boundaries must contain at least one valid boundary.".warn;
				^nil
			};
			^(mode: \boundaries, ticks: boundaryTicks)
		};

		divisionTicks = this.ticksFor(division, metremap);
		if (divisionTicks.isNil or: { divisionTicks <= 0 }) {
			"VoxRandChannelBlockMask: division must resolve to a positive tick duration.".warn;
			^nil
		};
		^(mode: \division, ticks: divisionTicks)
	}

	windowKeyFor { |tick, spec|
		var index;

		if (spec[\mode] == \division) {
			^(tick / spec[\ticks]).floor.asInteger
		};
		index = spec[\ticks].indexOfGreaterThan(tick);
		if (index.isNil) { ^spec[\ticks].size - 1 };
		^(index - 1).max(0)
	}

	remapVoxByWindow { |vox, mappings, spec|
		var events;

		events = vox.events.collect { |event|
			var newEvent, mapping, mapped;
			newEvent = event.copy;
			mapping = mappings[this.windowKeyFor(event[\absTime], spec)];
			mapped = mapping[event[\channel] ? 0];
			if (mapped.notNil) { newEvent[\channel] = mapped };
			newEvent
		};
		^Vox.new(events, vox.metremap, vox.label, vox.metadata.copy, this)
	}

	processVoxes { |voxes, metremap|
		var inputChannels, spec, keys, mappings;

		inputChannels = this.inputChannelsFor(voxes);
		if (this.validSpecFor(inputChannels).not) { ^nil };
		spec = this.windowSpecFor(metremap);
		if (spec.isNil) { ^nil };
		keys = voxes.collect { |vox|
			vox.events.collect { |event| this.windowKeyFor(event[\absTime], spec) }
		}.flatten.asSet.asArray.sort;
		mappings = Dictionary.new;
		keys.do { |key| mappings[key] = this.chooseMapping(inputChannels) };
		^voxes.collect { |vox| this.remapVoxByWindow(vox, mappings, spec) }
	}

	doProcess { |vox|
		^this.withSeed(seed, {
			var processed;
			processed = this.processVoxes([vox], vox.metremap);
			processed.isNil.if { vox.copy } { processed[0] }
		})
	}

	doMultiProcess { |voxMulti|
		^this.withSeed(seed, {
			var processed;
			processed = this.processVoxes(voxMulti.asArray, voxMulti.metremap);
			processed.isNil.if {
				voxMulti.copy
			} {
				VoxMulti.new(processed, voxMulti.metremap, voxMulti.label, voxMulti.metadata, this)
			}
		})
	}
}
