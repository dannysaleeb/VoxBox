Granulator : VoxModule {
	var <>divisions, <>prob_map, <>depth;

	*new { |divisions, prob_map, depth=2|
		^super.new.init(divisions, prob_map, depth);
	}

	init { |divisions, prob_map, depth|
		this.divisions = divisions ?? [2, 3];
		this.depth = depth;

		this.prob_map = prob_map ?? RTProbMap.new;

		^this
	}

	doProcess { |vox|
		var events = vox.events.collect { |ev|
			var currentTime = ev.absTime;
			var rt = ev.dur.rtdivide(divisions, prob_map, depth);
			var divided;

			if (rt.isKindOf(Array)) {
				rt.collect({
					arg dur;
					var newEv = ev.copy;

					newEv.dur = dur.value;
					newEv.absTime = currentTime;
					newEv.metadata = Dictionary.newFrom([\midinote_origin, ev.midinote]);
					currentTime = currentTime + dur.value;

					newEv.postln;

					newEv
				})
			} {
				ev.copy
			}
		};

		^Vox.new(
			events.flatten,
			vox.metremap,
			vox.label,
			vox.metadata.copy
		)
	}
}