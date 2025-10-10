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

RandElong : VoxModule {
	var <>factorRange;

	*new { |a, b|
		^super.new.init(a, b);
	}

	init { |a, b|

		a = a ?? 1.0;
		b = b ?? 2.0;

		this.factorRange = [a, b].asFloat.sort;

		^this
	}

	doProcess { |vox|

		var currentTime = vox.events.first.absTime;

		var events = vox.events.collect { |ev|
			var newEv = ev.copy;
			var factor = factorRange.first.rrand(factorRange.last);
			var newDur = ev[\dur] * factor;

			newEv[\dur] = newDur;
			newEv[\absTime] = currentTime;
			currentTime = currentTime + newDur;
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