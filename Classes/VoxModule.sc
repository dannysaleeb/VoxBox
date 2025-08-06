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
	// processes plug, returns vox
	process { |plug|
		^active.if {
			this.doProcess(plug);
		} {
			plug;
		}
	}

	// Must return a VoxPlug
	doProcess { |plug|
		"😬 VoxModule subclass must implement doProcess".warn;
		^plug; // return same plug if no process implemented
	}

	// takes VoxPlugMulti
	// Applies single process to each plug in turn
	// Implement on module if anything changes in multiProcess
	// Must return VoxPlugMulti
	doMultiProcess { |plugMulti|

		var plugs = plugMulti.plugs.values.collect { |plug|
			var processed;

			processed = this.process(plug);

			if (processed.isKindOf(VoxPlug).not) {
				plug; // return the unprocessed plug
			} {
				processed
			}
		};

		^VoxPlugMulti.new(plugs, plugMulti.metremap, plugMulti.label, plugMulti.metadata, this); // TO DO: I think this works????
	}

	// implement on module, must return VoxPlugMulti
	doMultiOutput { |plug|
		^nil;  // default: no multi-output
	}

	// Implement on module
	// Not exactly sure what this does at the moment
	// VoxMulti in, VoxMulti or Vox out?
	// Must return VoxPlug or VoxPlugMulti
	doMerge { |plugs|
		^nil;
	}

	out {
		var plug;
		var multiOut, processOutput;

		plug = input.respondsTo(\out).if {
			input.out
		} {
			input // assumes input is plug, but ideally not
		};

		if (plug.isNil or: { plug.isKindOf(VoxPlug).not && plug.isKindOf(VoxPlugMulti).not }) {
			"😬 VoxModule input is not compatible, not connected or does not support .out".warn;
			// returns single empty plug as default
			^VoxPlug.new;
		};

		// Case 1: VoxMulti in, VoxMulti out
		if (plug.isKindOf(VoxPlugMulti)) {
			// Check if subclass wants to merge
			// CHECK THIS LATER
			var merged = this.doMerge(plug.asArray);
			if (merged.notNil) { ^merged; };
			// Otherwise, process each individually
			^this.doMultiProcess(plug); // returns PlugMulti
		};

		// Case 2: Vox in, VoxMulti out
		// All doMultiOutput
		multiOut = this.doMultiOutput(plug);
		if (multiOut.notNil) {
			multiOut.isKindOf(VoxPlugMulti).if {
				^multiOut; // just return output from module
			} {
				"VoxModule(%): expected VoxPlugMulti, got %".format(this.label, multiOut).warn;
				^VoxPlugMulti.new; // default return expected PlugMulti, empty
			}
		};

		// Case 3: vox in, vox out
		processOutput = this.process(plug);

		// this.process should return a VoxPlug
		if (processOutput.isKindOf(VoxPlug)) {
			^processOutput;
		};

		// in case it doesn't, yield empty plug
		"VoxModule(%): expected VoxPlug, got % - returning empty VoxPlug"
		.format(this.label, processOutput).warn;

		^VoxPlug.new;
	}
}