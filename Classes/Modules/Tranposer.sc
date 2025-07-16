VoxTransposer : VoxModule {
	var <>semitones;

	*new { |semitones = 0, label = nil|
		^super.new(label).initTranspose(semitones);
	}

	initTranspose { |semitones|
		this.semitones = semitones;
		^this
	}

	doProcess { |events|
		^events.collect { |ev|
			var newEv = ev.copy;
			newEv[\midinote] = ev[\midinote] + semitones;
			newEv
		}
	}
}