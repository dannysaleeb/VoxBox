VoxModule {
	var <>active = true;
	var <>label;
	var <input;

	// there is no good input_ setter ...
	// this needs to do the same as Vox and VoxMulti?
	// check if plug or other ... currently it assigns whatever, including plug

	*new { |label = nil|
		^super.new.init(label ?? this.class.name.asSymbol)
	}

	init { |label|
		this.label = label;
		this.active = true;
		^this
	}

	input_ { |source|
		("✅ Input set on %: source = %".format(this.label ? this.class, source.class)).postln;
		input = source;
	}

	process { |plug|
		^active.if {
			this.doProcess(plug)
		} {
			plug
		}
	}

	doProcess { |plug|
		"😬 VoxModule subclass must implement doProcess".warn;
		^plug
	}

	// this currently not working / broken
	doMultiProcess { |plugs|

		^plugs.collect { |plug|
			var processed;

			processed = this.process(plug);

			if (processed.isKindOf(VoxPlug).not) {
				// Return empty plug on error to keep system stable
				VoxPlug.new([], plug.metremap, label, plug.metadata.copy)
			} {
				processed // correct: directly return processed plug
			}
		}
	}

	doMultiOutput { |plug|
		^nil;  // default: no multi-output
	}

	doMerge { |plugs|
		^nil;
	}

	out {
		var plug;
		var multiOut, processOutput;

		plug = input.respondsTo(\out).if {
			input.out
		} {
			input // assumes input is plug
		};

		if (plug.isNil or: { plug.isKindOf(VoxPlug).not && plug.isKindOf(VoxPlugMulti).not }) {
			"😬 VoxModule input is not compatible, not connected or does not support .out".warn;
			^VoxPlug.new([], nil, this.label, ()); // returns empty plug
		};

		// Case 1: VoxPlugMulti input
		if (plug.isKindOf(VoxPlugMulti)) {
			// Check if subclass wants to merge
			var merged = this.doMerge(plug.asArray);
			if (merged.notNil) { ^merged; };
			// Otherwise, process each individually
			^VoxPlugMulti.new(this.doMultiProcess(plug.asArray));
		};

		// Case 2: Single input, but wants to output multi
		multiOut = this.doMultiOutput(plug);
		if (multiOut.notNil) {
			if (multiOut.respondsTo(\asArray).not) {
				"😬 doMultiOutput returned non-array: %".format(multiOut.class).warn;
			};
			^VoxPlugMulti.new(multiOut);
		};

		// Case 3: Default single-input, single-output
		processOutput = this.process(plug);

		// this.process should return a VoxPlug
		if (processOutput.isKindOf(VoxPlug)) {
			^processOutput;
		};

		// in case it doesn't
		^VoxPlug.new(
			processOutput,
			plug.metremap,
			this.label,
			plug.metadata.copy
		);
	}
}