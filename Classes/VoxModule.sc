VoxModule {
	var <>active = true;
	var <>label;
	var <>input;

	*new { |label = nil|
		^super.new.init(label ?? this.class.name.asSymbol)
	}

	init { |label|
		this.label = label;
		this.active = true;
		^this
	}

	process { |plug|
		^active.if {
			this.doProcess(plug)
		} {
			plug
		}
	}

	doProcess { |plug|
		"VoxModule subclass must implement doProcess".warn;
		^plug
	}

	doMultiProcess { |plugs|
		// Default: process each plug individually
		^plugs.collect { |plug|
			VoxPlug.new(
				this.process(plug),
				plug.metremap,
				label,
				plug.metadata.copy
			)
		}
	}

	doMultiOutput { |plug|
		nil;  // default: no multi-output
	}

	doMerge { |plugs|
		nil;
	}

	out {
		var plug = input.tryPerform(\out);
		var multiOut, processOutput;

		if (plug.isNil) {
			"VoxModule input is not connected or does not support `.out`".warn;
			^VoxPlug.new([], nil, this.label, ());
		};

		// Case 1: VoxPlugMulti input
		if (plug.isKindOf(VoxPlugMulti)) {
			// Check if subclass wants to merge
			var merged = this.doMerge(plug.asArray);
			if (merged.notNil) {
				^merged;
			};

			// Otherwise, process each individually
			^VoxPlugMulti.new(this.doMultiProcess(plug.asArray));
		};

		// Case 2: Single input, but wants to output multi
		multiOut = this.doMultiOutput(plug);
		if (multiOut.notNil) {
			^VoxPlugMulti.new(multiOut);
		};

		processOutput = this.process(plug);

		if (processOutput.isKindOf(VoxPlug)) {
			^processOutput;
		}

		// Case 3: Default single-input, single-output
		^VoxPlug.new(
			processOutput,
			plug.metremap,
			this.label,
			plug.metadata.copy
		);
	}
}