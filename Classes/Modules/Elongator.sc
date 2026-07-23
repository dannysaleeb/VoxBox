Elongator : VoxModule {
	var <factor;

	*new { |factor = 2|
		^super.new.init(factor);
	}

	factor_ { |value| factor = value; this.touch }

	init { |factor|
		this.factor = factor;
		^this
	}

	provenanceSpec {
		^(op: \elongate, params: (factor: factor))
	}

	doProcess { |vox|
		var events = vox.events.collect { |ev|
			var newEv = ev.copy;
				newEv[\dur] = ev[\dur] * factor;
				newEv[\absTime] = ev[\absTime] * factor;
				newEv[\position] = TimeConverter.ticksToPos(newEv[\absTime], vox.metremap);
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
	var <factorRange, <seed, <preserveRests;

	*new { |a, b, seed, preserveRests = true|
		^super.new.init(a, b, seed, preserveRests);
	}

	init { |a, b, seedArg, preserveRestsArg|

		a = a ?? 1.0;
		b = b ?? 2.0;

		this.factorRange = [a, b].asFloat.sort;
		this.seed = seedArg;
		this.preserveRests = preserveRestsArg.isNil.if { true } { preserveRestsArg };

		^this
	}

	provenanceSpec {
		^(
			op: \elongateRandom,
			params: (
				factorRange: factorRange,
				seed: seed,
				preserveRests: preserveRests
			)
		)
	}

	factorRange_ { |value| factorRange = value; this.touch }
	seed_ { |value| seed = value; this.touch }
	preserveRests_ { |value| preserveRests = value; this.touch }

	doProcess { |vox|
		if (vox.events.isEmpty) { ^vox.copy };

		^this.withSeed(seed, {
			var currentTime = vox.events.first.absTime;

			var events = vox.events.collect { |ev|
				var newEv = ev.copy;
				var factor = factorRange.first.rrand(factorRange.last);
				var newDur = ev[\dur] * factor;

				newEv[\dur] = newDur;
				if (preserveRests.not) {
					newEv[\absTime] = currentTime;
					newEv[\position] = TimeConverter.ticksToPos(currentTime, vox.metremap);
					currentTime = currentTime + newDur;
				};
				newEv
			};

			Vox.new(
				events,
				vox.metremap,
				vox.label,
				vox.metadata.copy
			)
		})
	}
}
