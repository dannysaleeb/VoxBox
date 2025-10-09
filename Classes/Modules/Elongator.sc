Elongator : VoxModule {
	var <>factor;

	*new { |factor = 2|
		^super.new.init(factor);
	}

	init { |factor|
		this.factor = factor;
		^this
	}

	doProcess { |vox|
		var events = vox.events.collect { |ev|
			var newEv = ev.copy;
			newEv[\dur] = ev[\dur] * factor;
			newEv[\absTime] = ev[\absTime] * factor;
			newEv
		};

		^Vox.new(
			events,
			vox.metremap,
			vox.label,
			vox.metadata.copy
		)
	}
}
