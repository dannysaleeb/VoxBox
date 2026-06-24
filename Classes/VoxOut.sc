VoxOut : VoxNode {
	var <name, <mode, <midiout, <offset, <clock, <shouldLoop, <audible;

	*play { |name, clock, offset|
		^super.new.init(name, \synth, nil, clock, false, offset)
	}

	*loop { |name, clock, offset|
		^super.new.init(name, \synth, nil, clock, true, offset)
	}

	*playMIDI { |name, midiout, clock, offset|
		if (midiout.isKindOf(Pos)) {
			offset = midiout;
			midiout = nil;
		};
		if (clock.isKindOf(Pos)) {
			offset = clock;
			clock = nil;
		};
		^super.new.init(name, \midi, midiout, clock, false, offset)
	}

	*loopMIDI { |name, midiout, clock, offset|
		if (midiout.isKindOf(Pos)) {
			offset = midiout;
			midiout = nil;
		};
		if (clock.isKindOf(Pos)) {
			offset = clock;
			clock = nil;
		};
		^super.new.init(name, \midi, midiout, clock, true, offset)
	}

	init { |nameArg, modeArg, midioutArg, clockArg, shouldLoopArg, offsetArg|
		name = nameArg;
		mode = modeArg ? \synth;
		midiout = midioutArg;
		clock = clockArg;
		shouldLoop = shouldLoopArg ? false;
		offset = offsetArg;
		audible = true;
		label = nameArg;
		metadata = Dictionary.new;
		^this
	}

	on {
		audible = true;
		this.touch;
		if (input.notNil) {
			this.activateInSession(VoxSession.current);
		};
		^this
	}

	off {
		audible = false;
		this.touch;
		if (input.notNil) {
			this.activateInSession(VoxSession.current);
		};
		^this
	}

	audible_ { |value|
		value.if { this.on } { this.off };
		^this
	}

	player {
		^VoxSession.current.out(name)
	}

	activateInSession { |session|
		if (input.isNil) {
			"VoxOut: no input set.".warn;
			^nil
		};

		^session.registerOutput(name, input, mode, midiout, clock, shouldLoop, audible, offset)
	}

	out {
		if (input.isNil) {
			"VoxOut: no input set.".warn;
			^nil
		};

		this.activateInSession(VoxSession.current);
		^input.out
	}
}
