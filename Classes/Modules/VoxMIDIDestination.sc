VoxMIDIDestination : VoxModule {
	var <destination;

	*new { |destination|
		^super.new.initMIDIDestination(destination)
	}

	initMIDIDestination { |destinationArg|
		destination = destinationArg;
		^this
	}

	destination_ { |value| destination = value; this.touch }

	provenanceSpec {
		^(op: \midiDestination, params: (destination: destination))
	}

	validDestination {
		if (destination.isKindOf(Symbol).not) {
			"VoxMIDIDestination: destination must be a Symbol.".warn;
			^false
		};
		^true
	}

	assignDestination { |vox, value|
		^Vox.new(
			vox.events.collect { |event|
				var copied = event.copy;
				copied[\midiDestination] = value;
				copied
			},
			vox.metremap,
			vox.label,
			vox.metadata.copy,
			this
		)
	}

	doProcess { |vox|
		if (this.validDestination.not) { ^vox.copy };
		^this.assignDestination(vox, destination)
	}
}

VoxRandMIDIDestination : VoxMIDIDestination {
	var <destinations, <weights, <seed;

	*new { |destinations, weights, seed|
		^super.new(nil).initRandMIDIDestination(destinations, weights, seed)
	}

	initRandMIDIDestination { |destinationsArg, weightsArg, seedArg|
		destinations = destinationsArg ? [];
		weights = weightsArg ? Array.fill(destinations.size, 1);
		seed = seedArg;
		^this
	}

	destinations_ { |value| destinations = value ? []; this.touch }
	weights_ { |value| weights = value; this.touch }
	seed_ { |value| seed = value; this.touch }

	provenanceSpec {
		^(
			op: \randomMIDIDestination,
			params: (
				destinations: destinations,
				weights: weights,
				seed: seed
			)
		)
	}

	validChoices {
		if (destinations.isKindOf(SequenceableCollection).not or: { destinations.isEmpty }) {
			"VoxRandMIDIDestination: destinations must be a non-empty collection.".warn;
			^false
		};
		if (destinations.every { |item| item.isKindOf(Symbol) }.not) {
			"VoxRandMIDIDestination: every destination must be a Symbol.".warn;
			^false
		};
		if (weights.isKindOf(SequenceableCollection).not or: { weights.size != destinations.size }) {
			"VoxRandMIDIDestination: weights must match the destinations.".warn;
			^false
		};
		if (weights.every { |weight| weight.isNumber and: { weight >= 0 } }.not or: { weights.sum <= 0 }) {
			"VoxRandMIDIDestination: weights must be non-negative and at least one must be positive.".warn;
			^false
		};
		^true
	}

	chooseDestination {
		^destinations.wchoose(weights / weights.sum)
	}

	doProcess { |vox|
		if (this.validChoices.not) { ^vox.copy };
		^this.withSeed(seed, {
			this.assignDestination(vox, this.chooseDestination)
		})
	}

	doMultiProcess { |voxMulti|
		if (this.validChoices.not) { ^voxMulti.copy };
		^this.withSeed(seed, {
			var chosen = this.chooseDestination;
			VoxMulti.new(
				voxMulti.asArray.collect { |vox| this.assignDestination(vox, chosen) },
				voxMulti.metremap,
				voxMulti.label,
				voxMulti.metadata,
				this
			)
		})
	}
}

VoxRandMIDIDestinationMask : VoxRandMIDIDestination {
	var <division, <boundaries;

	*new { |destinations, weights, division, boundaries, seed|
		^super.new(destinations, weights, seed).initDestinationMask(division, boundaries)
	}

	initDestinationMask { |divisionArg, boundariesArg|
		division = divisionArg ? Pos(1);
		boundaries = boundariesArg;
		^this
	}

	division_ { |value| division = value; this.touch }
	boundaries_ { |value| boundaries = value; this.touch }

	provenanceSpec {
		^(
			op: \randomMIDIDestinationMask,
			params: (
				destinations: destinations,
				weights: weights,
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

	windowSpecFor { |metremap|
		var ticks, divisionTicks;

		if (boundaries.notNil) {
			ticks = boundaries.asArray.collect { |value| this.ticksFor(value, metremap) };
			if (ticks.includes(nil) or: { ticks.isEmpty }) {
				"VoxRandMIDIDestinationMask: boundaries must contain tick numbers or Pos values.".warn;
				^nil
			};
			^(mode: \boundaries, ticks: ticks.asSet.asArray.sort)
		};

		divisionTicks = this.ticksFor(division, metremap);
		if (divisionTicks.isNil or: { divisionTicks <= 0 }) {
			"VoxRandMIDIDestinationMask: division must resolve to a positive tick duration.".warn;
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

	processVoxes { |voxes, metremap|
		var spec, assignments;

		if (this.validChoices.not) { ^nil };
		spec = this.windowSpecFor(metremap);
		if (spec.isNil) { ^nil };
		assignments = Dictionary.new;

		^voxes.collect { |vox|
			Vox.new(
				vox.events.collect { |event|
					var copied, key, chosen;
					copied = event.copy;
					key = this.windowKeyFor(event[\absTime], spec);
					chosen = assignments[key];
					if (chosen.isNil) {
						chosen = this.chooseDestination;
						assignments[key] = chosen;
					};
					copied[\midiDestination] = chosen;
					copied
				},
				vox.metremap,
				vox.label,
				vox.metadata.copy,
				this
			)
		}
	}

	doProcess { |vox|
		^this.withSeed(seed, {
			var processed = this.processVoxes([vox], vox.metremap);
			processed.isNil.if { vox.copy } { processed[0] }
		})
	}

	doMultiProcess { |voxMulti|
		^this.withSeed(seed, {
			var processed = this.processVoxes(voxMulti.asArray, voxMulti.metremap);
			processed.isNil.if {
				voxMulti.copy
			} {
				VoxMulti.new(processed, voxMulti.metremap, voxMulti.label, voxMulti.metadata, this)
			}
		})
	}
}
