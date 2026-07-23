VoxRandArticulation : VoxModule {
	var <articulationMap, <choices, <scope, <division, <boundaries, <seed;
	var warnedDestinations;

	*new { |articulationMap, choices, scope = \event, division, boundaries, seed|
		^super.new.initRandArticulation(articulationMap, choices, scope, division, boundaries, seed)
	}

	initRandArticulation { |articulationMapArg, choicesArg, scopeArg, divisionArg, boundariesArg, seedArg|
		articulationMap = articulationMapArg ? Dictionary.new;
		choices = choicesArg;
		scope = scopeArg ? \event;
		division = divisionArg ? Pos(1);
		boundaries = boundariesArg;
		seed = seedArg;
		warnedDestinations = Set.new;
		^this
	}

	articulationMap_ { |value| articulationMap = value ? Dictionary.new; this.touch }
	choices_ { |value| choices = value; this.touch }
	scope_ { |value| scope = value; this.touch }
	division_ { |value| division = value; this.touch }
	boundaries_ { |value| boundaries = value; this.touch }
	seed_ { |value| seed = value; this.touch }

	provenanceSpec {
		^(
			op: \randomArticulation,
			params: (
				articulationMap: articulationMap,
				choices: choices,
				scope: scope,
				division: VoxProvenance.posValue(division),
				boundaries: boundaries.notNil.if {
					boundaries.collect { |value| VoxProvenance.posValue(value) }
				} { nil },
				seed: seed
			)
		)
	}

	warnOnce { |destination, message|
		var key = destination ? \missing;
		if (warnedDestinations.includes(key).not) {
			message.warn;
			warnedDestinations.add(key);
		};
	}

	channelMapFor { |destination|
		var channelMap, valid;

		if (destination.isKindOf(Symbol).not) {
			this.warnOnce(destination, "VoxRandArticulation: event has no symbolic MIDI destination; preserving it.");
			^nil
		};
		if (articulationMap.respondsTo(\keys).not) {
			this.warnOnce(destination, "VoxRandArticulation: articulationMap must be a Dictionary or Event; preserving events.");
			^nil
		};
		channelMap = articulationMap[destination];
		if (channelMap.isNil or: { channelMap.respondsTo(\keys).not }) {
			this.warnOnce(destination, "VoxRandArticulation: no articulation map for destination %; preserving events.".format(destination));
			^nil
		};
		valid = channelMap.keys.asArray.notEmpty and: {
			channelMap.keys.asArray.every { |name|
				var channel = channelMap[name];
				name.isKindOf(Symbol) and: {
					channel.isKindOf(Integer) and: { channel >= 0 and: { channel <= 15 } }
				}
			}
		};
		if (valid.not) {
			this.warnOnce(destination, "VoxRandArticulation: invalid articulation map for destination %; preserving events.".format(destination));
			^nil
		};
		^channelMap
	}

	choiceWeightsFor { |destination, channelMap|
		var spec, result, excluded = false;

		if (choices.isNil) {
			result = Dictionary.new;
			channelMap.keys.do { |name| result[name] = 1 };
			^result
		};
		if (choices.respondsTo(\keys).not) {
			this.warnOnce(destination, "VoxRandArticulation: choices must be keyed by destination; preserving events.");
			^nil
		};
		spec = choices[destination];
		if (spec.isNil) {
			this.warnOnce(destination, "VoxRandArticulation: no choices for destination %; preserving events.".format(destination));
			^nil
		};

		result = Dictionary.new;
		if (spec.isKindOf(SequenceableCollection) and: { spec.isString.not }) {
			spec.do { |name|
				if (name.isKindOf(Symbol) and: { channelMap[name].notNil }) {
					result[name] = 1
				} {
					excluded = true
				}
			}
		} {
			if (spec.respondsTo(\keys)) {
				spec.keysValuesDo { |name, weight|
					if (
						name.isKindOf(Symbol) and: { channelMap[name].notNil } and: {
							weight.isNumber and: { weight >= 0 }
						}
					) {
						result[name] = weight
					} {
						excluded = true
					}
				}
			}
		};
		if (result.isEmpty or: { result.values.sum <= 0 }) {
			this.warnOnce(destination, "VoxRandArticulation: destination % has no valid positive articulation choices; preserving events.".format(destination));
			^nil
		};
		if (excluded) {
			this.warnOnce(destination, "VoxRandArticulation: invalid or unmapped choices for destination % were excluded.".format(destination));
		};
		^result
	}

	chooseFor { |destination|
		var channelMap = this.channelMapFor(destination);
		var choiceWeights, names, weights, name;

		if (channelMap.isNil) { ^nil };
		choiceWeights = this.choiceWeightsFor(destination, channelMap);
		if (choiceWeights.isNil) { ^nil };
		names = choiceWeights.keys.asArray.sort;
		weights = names.collect { |item| choiceWeights[item] };
		name = names.wchoose(weights / weights.sum);
		^(name: name, channel: channelMap[name])
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
				"VoxRandArticulation: boundaries must contain tick numbers or Pos values.".warn;
				^nil
			};
			^(mode: \boundaries, ticks: ticks.asSet.asArray.sort)
		};
		divisionTicks = this.ticksFor(division, metremap);
		if (divisionTicks.isNil or: { divisionTicks <= 0 }) {
			"VoxRandArticulation: division must resolve to a positive tick duration.".warn;
			^nil
		};
		^(mode: \division, ticks: divisionTicks)
	}

	windowKeyFor { |tick, spec|
		var index;
		if (spec[\mode] == \division) { ^(tick / spec[\ticks]).floor.asInteger };
		index = spec[\ticks].indexOfGreaterThan(tick);
		if (index.isNil) { ^spec[\ticks].size - 1 };
		^(index - 1).max(0)
	}

	assignmentFor { |event, assignments, windowSpec|
		var destination = event[\midiDestination];
		var key, assignment;

		if (scope == \event) { ^this.chooseFor(destination) };
		if (scope == \render) { key = destination } {
			key = [destination, this.windowKeyFor(event[\absTime], windowSpec)]
		};
		assignment = assignments[key];
		if (assignment.isNil) {
			assignment = this.chooseFor(destination);
			if (assignment.notNil) { assignments[key] = assignment };
		};
		^assignment
	}

	processVoxes { |voxes, metremap|
		var assignments, windowSpec;

		if ([\event, \render, \window].includes(scope).not) {
			"VoxRandArticulation: scope must be \\event, \\render or \\window.".warn;
			^nil
		};
		windowSpec = (scope == \window).if { this.windowSpecFor(metremap) } { nil };
		if (scope == \window and: { windowSpec.isNil }) { ^nil };
		assignments = Dictionary.new;
		warnedDestinations = Set.new;

		^voxes.collect { |vox|
			Vox.new(
				vox.events.collect { |event|
					var copied = event.copy;
					var assignment = this.assignmentFor(event, assignments, windowSpec);
					if (assignment.notNil) {
						copied[\articulation] = assignment[\name];
						copied[\channel] = assignment[\channel];
					};
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
