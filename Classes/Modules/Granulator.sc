Granulator : VoxModule {
	var <divisions, <prob_map, <depth, <seed;

	*new { |divisions, prob_map, depth=2, seed|
		^super.new.init(divisions, prob_map, depth, seed);
	}

	init { |divisions, prob_map, depth, seedArg|
		this.divisions = divisions ?? [2, 3];
		this.depth = depth;

		this.prob_map = prob_map ?? RTProbMap.new;
		this.seed = seedArg;

		^this
	}

	divisions_ { |value| divisions = value; this.touch }
	prob_map_ { |value| prob_map = value; this.touch }
	depth_ { |value| depth = value; this.touch }
	seed_ { |value| seed = value; this.touch }

	doProcess { |vox|
		^this.withSeed(seed, {
		var events = vox.events.collect { |ev|
			var currentTime = ev.absTime;
			var rt = ev.dur.rtdivide(divisions, prob_map, depth);

			if (rt.isKindOf(Array)) {
				var grainCount = rt.size;

				rt.collect({ |dur, index|
					var newEv = ev.copy;
					var grainDur = dur.value;
					var metadata = ev[\metadata].notNil.if {
						ev[\metadata].copy
					} {
						Dictionary.new
					};

					newEv.dur = grainDur;
					newEv.absTime = currentTime;
					newEv.position = TimeConverter.ticksToPos(currentTime, vox.metremap);

					metadata[\grain_origin_midinote] = ev.midinote;
					metadata[\grain_origin_absTime] = ev.absTime;
					metadata[\grain_origin_dur] = ev.dur;
					metadata[\grain_index] = index;
					metadata[\grain_count] = grainCount;
					metadata[\grain_is_onset] = index == 0;
					newEv.metadata = metadata;

					currentTime = currentTime + grainDur;

					newEv
				})
			} {
				var newEv = ev.copy;
				var metadata = ev[\metadata].notNil.if {
					ev[\metadata].copy
				} {
					Dictionary.new
				};

				metadata[\grain_origin_midinote] = ev.midinote;
				metadata[\grain_origin_absTime] = ev.absTime;
				metadata[\grain_origin_dur] = ev.dur;
				metadata[\grain_index] = 0;
				metadata[\grain_count] = 1;
				metadata[\grain_is_onset] = true;
				newEv.metadata = metadata;
				newEv.position = TimeConverter.ticksToPos(newEv.absTime, vox.metremap);

				newEv
			}
		};

		Vox.new(
			events.flatten,
			vox.metremap,
			vox.label,
			vox.metadata.copy
		)
		})
	}
}
