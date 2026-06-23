VoxOut : VoxNode {
	var <name, <mode, <midiout, <offset, <clock, <shouldLoop, <enabled, <muted;

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
		enabled = false;
		muted = false;
		label = nameArg;
		metadata = Dictionary.new;
		^this
	}

	on {
		enabled = true;
		muted = false;
		this.touch;
		if (input.notNil) {
			this.activateInSession(VoxSession.current);
		};
		^this
	}

	off {
		enabled = false;
		this.touch;
		VoxSession.current.stopOutput(name);
		^this
	}

	enabled_ { |value|
		value.if { this.on } { this.off };
		^this
	}

	mute {
		muted = true;
		this.touch;
		if (input.notNil) {
			VoxSession.current.muteOutput(name);
		};
		^this
	}

	unmute {
		muted = false;
		this.touch;
		if (input.notNil) {
			VoxSession.current.unmuteOutput(name);
		};
		^this
	}

	muted_ { |value|
		value.if { this.mute } { this.unmute };
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

		^session.registerOutput(name, input, mode, midiout, clock, shouldLoop, enabled, muted, offset)
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
