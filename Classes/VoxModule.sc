VoxModule : VoxNode {
	var <>active = true;

	*new { |label|
		^super.new.init(label)
	}

	init { |labelArg|
		label = labelArg ? this.class.asSymbol;
		metadata = Dictionary.new;
		^this
	}

	input_ { |source|
		("✅ Input set on %: source = %".format(this.label ? this.class, source.class)).postln;
		input = source;
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
		var vox;
		var multiOut, processOutput;

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
			if (merged.notNil) { ^merged; };
			// Otherwise, process each individually
			^this.doMultiProcess(vox); // returns PlugMulti
		};

		// Case 2: Box in, BoxMulti out
		// All doMultiOutput
		multiOut = this.doMultiOutput(vox);
		if (multiOut.notNil) {
			multiOut.isKindOf(VoxMulti).if {
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
			^processOutput;
		};

		// in case it doesn't, yield empty vox
		"VoxModule(%): expected Vox, got % - returning empty Vox"
		.format(this.label, processOutput).warn;

		^Vox.new;
	}
}