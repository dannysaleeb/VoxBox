VoxModule {
	var <>active = true;
	var <>label;
	var <>input;

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

	process { |plug|

		("PROCESS: active = %, plug = %, class = %")
		.format(active, plug, plug.class)
		.postln;

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

	// this currently not working / broken
	doMultiProcess { |plugs|
		// Default: process each plug individually
		^plugs.collect { |plug|
			//DEBUG
			var processed, events;
			"Calling process on plug of class %".format(plug.class).postln;
			processed = this.process(plug);
			"Process returned: %".format(processed).postln;
			//ENDEBUG

			events = processed.isKindOf(VoxPlug).if {
				processed.events;
			} {
				"⚠️ process returned %, not VoxPlug".format(processed.class).warn;
				[];
			};

			VoxPlug.new(
				events,
				plug.metremap,
				label,
				plug.metadata.copy
			)

			/*VoxPlug.new(
				this.process(plug),
				plug.metremap,
				label,
				plug.metadata.copy
			)*/
		}
	}

	doMultiOutput { |plug|
		nil;  // default: no multi-output
	}

	doMerge { |plugs|
		nil;
	}

	out {
		var plug;
		var multiOut, processOutput;

		// I think this fixes the issue with assigning plug etc...
		// it checks if input is not plug and has out
		plug = input.respondsTo(\out).if {
			input.out
		} {
			input // assumes input is plug
		};

		("→ VoxModule[%]: plug is %"
		.format(this.label ? this.class, plug.class)).postln;
		// this is being called twice?

		// this assumes plug, but might be not plug

		if (plug.isNil or: { plug.isKindOf(VoxPlug).not && plug.isKindOf(VoxPlugMulti).not }) {
			"VoxModule input is not compatible, not connected or does not support `.out`".warn;
			^VoxPlug.new([], nil, this.label, ());
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
				"⚠️ doMultiOutput returned non-array: %".format(multiOut.class).warn;
			};
			^VoxPlugMulti.new(multiOut);
		};

		processOutput = this.process(plug);

		if (processOutput.isKindOf(VoxPlug)) {
			^processOutput;
		};

		// Case 3: Default single-input, single-output
		^VoxPlug.new(
			processOutput,
			plug.metremap,
			this.label,
			plug.metadata.copy
		);
	}
}