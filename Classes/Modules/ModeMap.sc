ModeMap : VoxModule {
	var <source_scale, <source_root, <target_scale, <target_root;

	*new { |source_scale, source_root, target_scale, target_root|
		^super.new.init(source_scale, source_root, target_scale, target_root)
	}

	source_scale_ { |value| source_scale = value; this.touch }
	source_root_ { |value| source_root = value; this.touch }
	target_scale_ { |value| target_scale = value; this.touch }
	target_root_ { |value| target_root = value; this.touch }

	init {|source_scale, source_root, target_scale, target_root|
		this.source_scale = source_scale ?? Scale.at(\ionian);
		this.source_root = source_root ?? 48;
		this.target_scale = target_scale ?? Scale.at(\minorPentatonic);
		this.target_root = target_root ?? 48;
		^this
	}

	mapMode {
		arg midinote;
		var coordinates, targetTone;

		coordinates = this.sourceCoordinates(midinote);
		targetTone = this.pitchForDegree(
			coordinates[\degree],
			target_scale,
			target_root
		);

		^targetTone + coordinates[\accidental]
	}

	sourceCoordinates {
		arg midinote;
		var relativeOctave, centerDegree, lowestDegree, highestDegree;
		var bestDegree, bestPitch, bestDistance;

		relativeOctave = ((midinote - source_root) / 12).floor.asInteger;
		centerDegree = relativeOctave * source_scale.size;
		lowestDegree = centerDegree - source_scale.size;
		highestDegree = centerDegree + (source_scale.size * 2);

		(lowestDegree..highestDegree).do { |degree|
			var pitch, distance;

			pitch = this.pitchForDegree(degree, source_scale, source_root);
			distance = (midinote - pitch).abs;
			if (
				bestDistance.isNil
				or: { distance < bestDistance }
				or: { (distance == bestDistance) and: { pitch < bestPitch } }
			) {
				bestDegree = degree;
				bestPitch = pitch;
				bestDistance = distance;
			}
		};

		^(
			degree: bestDegree,
			pitch: bestPitch,
			accidental: midinote - bestPitch
		)
	}

	pitchForDegree {
		arg degree, scale, root;
		var size, octave, pc;

		size = scale.degrees.size;
		octave = degree div: size;
		pc = scale.degrees.wrapAt(degree);

		^root + pc + (octave * 12)
	}

	doProcess { |vox|
		var events = vox.events.collect({ |ev|
			var newEv = ev.copy;
			newEv[\midinote] = this.mapMode(newEv[\midinote]);
			newEv
		});

		^Vox.new(
			events,
			vox.metremap,
			vox.label,
			vox.metadata.copy
		)
	}
}
