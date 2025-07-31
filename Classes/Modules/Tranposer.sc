VoxTransposer : VoxModule {
	var <>semitones;

	*new { |semitones = 0|
		^super.new.initTranspose(semitones);
	}

	initTranspose { |semitones|
		this.semitones = semitones;
		^this
	}

	doProcess { |plug|
		var events = plug.events.collect { |ev|
			var newEv = ev.copy;
			newEv[\midinote] = ev[\midinote] + semitones;
			newEv
		};

		^VoxPlug.new(
			events,
			plug.metremap,
			plug.label,
			plug.metadata.copy
		)
	}
}
