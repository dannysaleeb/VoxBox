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

	process { |events|
		^active.if {
			this.doProcess(events)
		} {
			events
		}
	}

	doProcess { |events|
		"VoxModule subclass must implement doProcess".warn;
		^events
	}

	out {
		var plug, inEvents;

		plug = input.tryPerform(\out);

		if (plug.isNil) {
			"VoxModule input is not connected or does not support `.out`".warn;
			^VoxPlug.new([], nil, this.label, ());
		};

		inEvents = plug.events;

		^VoxPlug.new(
			this.process(inEvents),
			plug.metremap,
			this.label,
			plug.metadata.copy
		);
	}
}


