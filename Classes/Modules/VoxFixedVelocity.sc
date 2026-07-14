VoxFixedVelocity : VoxModule {
	var <velocity;

	*new { |velocity = 63|
		^super.new.initSetVelocity(velocity)
	}

	*velocity { |value = 63|
		^this.new(value)
	}

	initSetVelocity { |velocity|
		this.velocity = velocity;
		^this
	}

	velocity_ { |value| velocity = value; this.touch }

	doProcess { |vox|
		var events = vox.events.collect({ |ev|
			var newEv = ev.copy;
			newEv[\velocity] = velocity;
			newEv
		});

		^Vox.new(
			events,
			vox.metremap,
			vox.label,
			vox.metadata.copy,
			this
		)
	}
}

VoxRandVelocityRange : VoxModule {
	var <min, <max, <seed;

	*new { |min = 0, max = 127, seed|
		^super.new.initRandVelocity(min, max, seed)
	}

	*range { |minVal = 0, maxVal = 127|
		^this.new(minVal, maxVal, nil);
	}

	initRandVelocity { |min, max, seed|
		this.min = min;
		this.max = max;
		this.seed = seed;
		^this
	}

	min_ { |value| min = value; this.touch }
	max_ { |value| max = value; this.touch }
	seed_ { |value| seed = value; this.touch }

	doProcess { |vox|
		^this.withSeed(seed, {
			var events = vox.events.collect({ |ev|
				var newEv = ev.copy;
				newEv[\velocity] = min.rrand(max);
				newEv
			});

			Vox.new(
				events,
				vox.metremap,
				vox.label,
				vox.metadata.copy,
				this
			)
		})
	}
}