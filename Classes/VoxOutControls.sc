VoxOutControls {
	var <session, <mappings;

	*new { |session|
		^super.new.init(session)
	}

	init { |sessionArg|
		session = sessionArg ? VoxSession.current;
		mappings = Dictionary.new;
		^this
	}

	pause { |target = \all|
		^session.pauseOutput(target)
	}

	resume { |target = \all|
		^session.resumeOutput(target)
	}

	start { |target = \all|
		^session.startOutput(target)
	}

	stop { |target = \all|
		^session.stopOutput(target)
	}

	restart { |target = \all|
		^session.restartOutput(target)
	}

	on { |target = \all|
		^session.setOutputAudible(target, true)
	}

	off { |target = \all|
		^session.setOutputAudible(target, false)
	}

	map { |control, target, action|
		if (this.respondsTo(action).not) {
			"VoxOutControls: unsupported action %.".format(action).warn;
			^this
		};

		mappings[control] = (target: target, action: action);
		^this
	}

	trigger { |control|
		var mapping = mappings[control];

		if (mapping.isNil) {
			"VoxOutControls: no mapping for %.".format(control).warn;
			^nil
		};

		^this.perform(mapping[\action], mapping[\target])
	}

	unmap { |control|
		mappings.removeAt(control);
		^this
	}

	clear {
		mappings.clear;
		^this
	}
}
