VoxOut : VoxNode {
	var <name, <mode, <midiout, <offset, clock, <shouldLoop, <audible, <channelMap;
	var <destinationMap, <defaultDestination;

	*new { |name = \out|
		^super.new.init(name, \midi, nil, nil, true, nil)
	}

	*named { |name = \out|
		^this.new(name)
	}

	*channels { |... spec|
		^this.named.channels(spec)
	}

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

	*playMultiMIDI { |name, destinationMap, clock, defaultDestination, offset|
		^super.new.init(name, \multiMIDI, nil, clock, false, offset)
			.destinationMap_(destinationMap)
			.defaultDestination_(defaultDestination)
	}

	*loopMultiMIDI { |name, destinationMap, clock, defaultDestination, offset|
		^super.new.init(name, \multiMIDI, nil, clock, true, offset)
			.destinationMap_(destinationMap)
			.defaultDestination_(defaultDestination)
	}

	init { |nameArg, modeArg, midioutArg, clockArg, shouldLoopArg, offsetArg|
		name = nameArg;
		mode = modeArg ? \synth;
		midiout = midioutArg;
		clock = clockArg;
		shouldLoop = shouldLoopArg ? false;
		offset = offsetArg;
		audible = true;
		channelMap = nil;
		destinationMap = nil;
		defaultDestination = nil;
		label = nameArg;
		metadata = Dictionary.new;
		^this
	}

	activateIfPatched {
		if (input.notNil) {
			this.activateInSession(VoxSession.current);
		};
		^this
	}

	normaliseChannelMap { |spec|
		var map = Dictionary.new;

		if (spec.isNil) { ^nil };

		if (spec.isKindOf(Dictionary)) {
			^spec.copy
		};

		if (spec.isKindOf(Association)) {
			map[spec.key] = spec.value;
			^map
		};

		if (spec.isKindOf(SequenceableCollection)) {
			if (spec.size == 1) {
				^this.normaliseChannelMap(spec[0])
			};

			spec.do { |item|
				if (item.isKindOf(Association)) {
					map[item.key] = item.value;
				} {
					if (item.isKindOf(SequenceableCollection) and: { item.size == 2 }) {
						map[item[0]] = item[1];
					}
				}
			};
			^map
		};

		"VoxOut.channels: expected associations, pairs, an array or a Dictionary.".warn;
		^nil
	}

	channelMap_ { |map|
		channelMap = this.normaliseChannelMap(map);
		this.touch;
		this.activateIfPatched;
		^this
	}

	channels { |... spec|
		^this.channelMap_(spec)
	}

	clock { |value|
		if (value.isNil) { ^clock };
		clock = value;
		this.touch;
		this.activateIfPatched;
		^this
	}

	clock_ { |value|
		^this.clock(value)
	}

	midiout_ { |value|
		midiout = value;
		this.touch;
		this.activateIfPatched;
		^this
	}

	destinationMap_ { |value|
		destinationMap = value;
		this.touch;
		this.activateIfPatched;
		^this
	}

	defaultDestination_ { |value|
		defaultDestination = value;
		this.touch;
		this.activateIfPatched;
		^this
	}

	offset_ { |value|
		offset = value;
		this.touch;
		this.activateIfPatched;
		^this
	}

	transport { |modeArg = \midi, shouldLoopArg = true, midioutArg, clockArg, offsetArg|
		mode = modeArg ? mode ? \midi;
		shouldLoop = shouldLoopArg ? shouldLoop ? true;

		if (midioutArg.isKindOf(Pos)) {
			offsetArg = midioutArg;
			midioutArg = nil;
		};
		if (clockArg.isKindOf(Pos)) {
			offsetArg = clockArg;
			clockArg = nil;
		};

		if (midioutArg.notNil) { midiout = midioutArg };
		if (clockArg.notNil) { clock = clockArg };
		if (offsetArg.notNil) { offset = offsetArg };
		this.touch;
		this.activateIfPatched;
		^this
	}

	play { |clockArg, offsetArg|
		^this.transport(\synth, false, nil, clockArg, offsetArg)
	}

	loop { |clockArg, offsetArg|
		^this.transport(\synth, true, nil, clockArg, offsetArg)
	}

	playMIDI { |midioutArg, clockArg, offsetArg|
		^this.transport(\midi, false, midioutArg, clockArg, offsetArg)
	}

	loopMIDI { |midioutArg, clockArg, offsetArg|
		^this.transport(\midi, true, midioutArg, clockArg, offsetArg)
	}

	playMultiMIDI { |destinationMapArg, clockArg, defaultDestinationArg, offsetArg|
		mode = \multiMIDI;
		shouldLoop = false;
		if (destinationMapArg.notNil) { destinationMap = destinationMapArg };
		if (clockArg.notNil) { clock = clockArg };
		if (defaultDestinationArg.notNil) { defaultDestination = defaultDestinationArg };
		if (offsetArg.notNil) { offset = offsetArg };
		this.touch;
		this.activateIfPatched;
		^this
	}

	loopMultiMIDI { |destinationMapArg, clockArg, defaultDestinationArg, offsetArg|
		mode = \multiMIDI;
		shouldLoop = true;
		if (destinationMapArg.notNil) { destinationMap = destinationMapArg };
		if (clockArg.notNil) { clock = clockArg };
		if (defaultDestinationArg.notNil) { defaultDestination = defaultDestinationArg };
		if (offsetArg.notNil) { offset = offsetArg };
		this.touch;
		this.activateIfPatched;
		^this
	}

	on {
		audible = true;
		this.touch;
		this.activateIfPatched;
		^this
	}

	off {
		audible = false;
		this.touch;
		this.activateIfPatched;
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

		^session.registerOutput(
			name, input, mode, midiout, clock, shouldLoop, audible, offset, channelMap,
			destinationMap, defaultDestination
		)
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
