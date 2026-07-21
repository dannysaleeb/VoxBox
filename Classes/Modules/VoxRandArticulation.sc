VoxRandArticulation : VoxModule {
	var <profiles, <scope, <division, <boundaries, <seed;
	var warnedDestinations;

	*new { |profiles, scope = \event, division, boundaries, seed|
		^super.new.initRandArticulation(profiles, scope, division, boundaries, seed)
	}

	initRandArticulation { |profilesArg, scopeArg, divisionArg, boundariesArg, seedArg|
		profiles = profilesArg ? Dictionary.new;
		scope = scopeArg ? \event;
		division = divisionArg ? Pos(1);
		boundaries = boundariesArg;
		seed = seedArg;
		warnedDestinations = Set.new;
		^this
	}

	profiles_ { |value| profiles = value ? Dictionary.new; this.touch }
	scope_ { |value| scope = value; this.touch }
	division_ { |value| division = value; this.touch }
	boundaries_ { |value| boundaries = value; this.touch }
	seed_ { |value| seed = value; this.touch }

	warnOnce { |destination, message|
		var key = destination ? \missing;
		if (warnedDestinations.includes(key).not) {
			message.warn;
			warnedDestinations.add(key);
		};
	}

	profileFor { |destination|
		var profile, names, valid;

		if (destination.isKindOf(Symbol).not) {
			this.warnOnce(destination, "VoxRandArticulation: event has no symbolic MIDI destination; preserving it.");
			^nil
		};
		if (profiles.respondsTo(\keys).not) {
			this.warnOnce(destination, "VoxRandArticulation: profiles must be a Dictionary or Event; preserving events.");
			^nil
		};
		profile = profiles[destination];
		if (profile.isNil or: { profile.respondsTo(\keys).not }) {
			this.warnOnce(destination, "VoxRandArticulation: no profile for destination %; preserving events.".format(destination));
			^nil
		};
		names = profile.keys.asArray;
		valid = names.notEmpty and: {
			names.every { |name|
				var spec = profile[name];
				var channel, weight;
				if (spec.respondsTo(\keys)) {
					channel = spec[\channel];
					weight = spec[\weight] ? 1;
				};
				name.isKindOf(Symbol) and: { spec.respondsTo(\keys) } and: {
					channel.isKindOf(Integer) and: { channel >= 0 and: { channel <= 15 } }
				} and: { weight.isNumber and: { weight >= 0 } }
			}
		} and: {
			names.collect { |name| profile[name][\weight] ? 1 }.sum > 0
		};
		if (valid.not) {
			this.warnOnce(destination, "VoxRandArticulation: invalid profile for destination %; preserving events.".format(destination));
			^nil
		};
		^profile
	}

	chooseFor { |destination|
		var profile = this.profileFor(destination);
		var names, weights, name;

		if (profile.isNil) { ^nil };
		names = profile.keys.asArray.sort;
		weights = names.collect { |item| profile[item][\weight] ? 1 };
		name = names.wchoose(weights / weights.sum);
		^(name: name, channel: profile[name][\channel])
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
