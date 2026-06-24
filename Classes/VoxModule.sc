VoxModule : VoxNode {
	var <active = true;
	var cachedRevision, cachedOutput;

	*new { |label|
		^super.new.init(label)
	}

	init { |labelArg|
		label = labelArg ? this.class.asSymbol;
		metadata = Dictionary.new;
		^this
	}

	input_ { |source|
		input = source;
		this.touch;
	}

	active_ { |value|
		active = value;
		this.touch;
	}

	atRange { |range|
		^VoxRangeModule.new(this, range)
	}

	reroll {
		^this.touch
	}

	withSeed { |seed, func|
		var previousData;

		if (seed.isNil) { ^func.value };
		previousData = thisThread.randData.copy;

		^{
			thisThread.randSeed = seed;
			func.value
		}.protect {
			thisThread.randData = previousData;
		}
	}

	// checks if active (basic filter)
	// processes vox, returns vox
	process { |vox|
		^active.if {
			this.doProcess(vox);
		} {
			vox;
		}
	}

	// Must return a Vox
	doProcess { |vox|
		"😬 VoxModule subclass must implement doProcess".warn;
		^vox; // return same vox if no process implemented
	}

	// takes VoxMulti
	// Applies single process to each vox in turn
	// Implement on module if anything changes in multiProcess
	// Must return VoxMulti
	doMultiProcess { |voxMulti|

		var voxes = voxMulti.voxes.values.collect { |vox|
			var processed;

			processed = this.process(vox);

			if (processed.isKindOf(Vox).not) {
				vox; // return the unprocessed vox
			} {
				processed
			}
		};

		^VoxMulti.new(voxes, voxMulti.metremap, voxMulti.label, voxMulti.metadata, this); // TO DO: I think this works????
	}

	// implement on module, must return VoxMulti
	doMultiOutput { |vox|
		^nil;  // default: no multi-output
	}

	// Implement on module
	// Not exactly sure what this does at the moment
	// BoxMulti in, BoxMulti or Box out?
	// Must return Vox or VoxMulti
	doMerge { |voxes|
		^nil;
	}

	out {
		var vox, currentRevision;
		var multiOut, processOutput;

		currentRevision = this.effectiveRevision;
		if (cachedRevision == currentRevision and: { cachedOutput.notNil }) {
			^cachedOutput.copy
		};

		vox = input.respondsTo(\out).if {
			input.out
		} {
			input // assumes input is vox (immutable, frozen), but ideally box (mutable, live)
		};

		if (vox.isNil or: { vox.isKindOf(Vox).not && vox.isKindOf(VoxMulti).not }) {
			"😬 VoxModule input is not compatible, not connected or does not support .out".warn;
			// returns single empty vox as default
			^Vox.new;
		};

		// Case 1: BoxMulti in, BoxMulti out
			if (vox.isKindOf(VoxMulti)) {
			// Check if subclass wants to merge
			// CHECK THIS LATER
				var merged = this.doMerge(vox.asArray);
				if (merged.notNil) {
					cachedRevision = currentRevision;
					cachedOutput = merged.copy;
					^merged;
				};
			// Otherwise, process each individually
				processOutput = this.doMultiProcess(vox);
				cachedRevision = currentRevision;
				cachedOutput = processOutput.copy;
				^processOutput;
			};

		// Case 2: Box in, BoxMulti out
		// All doMultiOutput
		multiOut = this.doMultiOutput(vox);
		if (multiOut.notNil) {
			multiOut.isKindOf(VoxMulti).if {
					cachedRevision = currentRevision;
					cachedOutput = multiOut.copy;
					^multiOut; // just return output from module
			} {
				"VoxModule(%): expected VoxMulti, got %".format(this.label, multiOut).warn;
				^VoxMulti.new; // default return expected PlugMulti, empty
			}
		};

		// Case 3: vox in, vox out
		processOutput = this.process(vox);

		// this.process should return a Vox
		if (processOutput.isKindOf(Vox)) {
			cachedRevision = currentRevision;
			cachedOutput = processOutput.copy;
			^processOutput;
		};

		// in case it doesn't, yield empty vox
		"VoxModule(%): expected Vox, got % - returning empty Vox"
		.format(this.label, processOutput).warn;

		^Vox.new;
	}
}

VoxRangeModule : VoxModule {
	var <module, <rangeArg;

	*new { |moduleArg, range|
		^super.new.initRange(moduleArg, range)
	}

	initRange { |moduleArg, range|
		module = moduleArg;
		rangeArg = range;
		label = ("%AtRange".format(moduleArg.label ? moduleArg.class.name)).asSymbol;
		metadata = Dictionary.new;
		^this
	}

	range_ { |value|
		rangeArg = value;
		this.touch;
		^this
	}

	effectiveRevision {
		^[
			super.effectiveRevision,
			rangeArg.hash,
			module.notNil.if { module.effectiveRevision } { nil }
		].hash
	}

	trimEventOutsideRange { |event, range, metremap|
		var startTick, finishTick, left, right, result;

		startTick = event[\absTime];
		finishTick = startTick + event[\dur];
		result = List.new;

		if ((finishTick <= range.start).or({ startTick >= range.end })) {
			result.add(event.deepCopy);
			^result.asArray
		};

		if (startTick < range.start) {
			left = event.deepCopy;
			left[\dur] = range.start - startTick;
			left[\position] = TimeConverter.ticksToPos(left[\absTime], metremap);
			result.add(left);
		};

		if (finishTick > range.end) {
			right = event.deepCopy;
			right[\absTime] = range.end;
			right[\dur] = finishTick - range.end;
			right[\position] = TimeConverter.ticksToPos(right[\absTime], metremap);
			result.add(right);
		};

		^result.asArray
	}

	eventsOutsideRange { |events, range, metremap|
		var result = List.new;

		events.do { |event|
			this.trimEventOutsideRange(event, range, metremap).do { |trimmed|
				result.add(trimmed)
			}
		};

		^result.asArray
	}

	positionEvents { |events, metremap|
		^events.deepCopy.do { |event|
			event[\position] = TimeConverter.ticksToPos(event[\absTime], metremap)
		}
	}

	doProcess { |vox|
		var range, dryEvents, clippedEvents, clippedVox, processed, wetEvents;
		var mergedEvents, outMetadata;

		if (module.isNil) {
			"VoxRangeModule: no wrapped module.".warn;
			^vox.copy
		};

		range = TimeRange.from(rangeArg, vox.metremap);
		if (range.isEmpty) { ^vox.copy };

		clippedEvents = Box.clippedEvents(vox.events, range);
		if (clippedEvents.isEmpty) { ^vox.copy };

		dryEvents = this.eventsOutsideRange(vox.events, range, vox.metremap);
		clippedVox = Vox.new(clippedEvents, vox.metremap, vox.label, vox.metadata, this);
		processed = module.process(clippedVox);

		if (processed.isKindOf(Vox).not) {
			"VoxRangeModule: wrapped module % must return Vox for atRange.".format(module.class.name).warn;
			^vox.copy
		};

		wetEvents = Box.clippedEvents(processed.events, range);
		mergedEvents = this.positionEvents((dryEvents ++ wetEvents).sortBy(\absTime), vox.metremap);

		outMetadata = vox.metadata.deepCopy;
		outMetadata[\provenance] = VoxProvenance.boundary(
			\atRange,
			(rangeTicks: range.asArray, module: module.class.name),
			vox.provenance
		);

		^Vox.new(mergedEvents, vox.metremap, vox.label, outMetadata, this)
	}
}
