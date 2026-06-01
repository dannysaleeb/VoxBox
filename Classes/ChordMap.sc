ChordSpec {
	var <>root, <>scale, <>degrees, <>weight;

	*new { |root = 60, scale, degrees = #[0, 2, 4], weight = 1.0|
		^super.new.init(root, scale, degrees, weight)
	}

	init { |rootArg, scaleArg, degreesArg, weightArg|
		root = rootArg ? 60;
		scale = scaleArg ? Scale.at(\ionian);
		degrees = degreesArg ? #[0, 2, 4];
		weight = weightArg ? 1.0;
		^this
	}

	scalePcForDegree { |degree|
		^degree.degreeToKey(scale).asInteger % 12
	}

	isScaleMidi { |midinote|
		var pc = (((midinote - root) % 12) + 12) % 12;
		^scale.semitones.detect { |semitone|
			semitone.asInteger == pc.asInteger
		}.notNil
	}

	isChordTone { |midinote|
		var pc = (((midinote - root) % 12) + 12) % 12;
		^degrees.detect { |degree|
			this.scalePcForDegree(degree) == pc.asInteger
		}.notNil
	}

	candidatesAround { |center, window = 12|
		var low = (center - window).ceil.asInteger;
		var high = (center + window).floor.asInteger;

		^(low..high).select { |note|
			this.isScaleMidi(note)
		}
	}

	chordToneCandidatesAround { |center, window = 12|
		^this.candidatesAround(center, window).select { |note|
			this.isChordTone(note)
		}
	}
}

ChordRegion {
	var <start, <chord;

	*new { |start = 0, chord|
		^super.new.init(start, chord)
	}

	init { |startArg, chordArg|
		start = (startArg ? 0).floor.asInteger;
		chord = chordArg;
		^this
	}

	asString {
		^"ChordRegion(start: % || chord: %)".format(start, chord)
	}
}

ChordMap {
	var <regions, <metremap;

	*new { |metremap|
		^super.new.init(metremap)
	}

	init { |metremapArg|
		metremap = metremapArg;
		regions = List.new;
		^this
	}

	ticksForStart { |start|
		if (start.isKindOf(Pos)) {
			if (metremap.isNil) {
				"ChordMap.add: Pos starts require a MetreMap.".warn;
				^0
			};
			^TimeConverter.posToTicks(start, metremap)
		};

		^start
	}

	add { |start, chord|
		var tick = this.ticksForStart(start);

		regions.add(ChordRegion(tick, chord));
		regions.sort({ |a, b| a.start < b.start });

		^this
	}

	atTick { |tick|
		var matches;

		matches = regions.select { |region|
			region.start <= tick
		};

		^matches.isEmpty.if {
			nil
		} {
			matches.last.chord
		}
	}

	atPos { |pos|
		if (metremap.isNil) {
			"ChordMap.atPos requires a MetreMap.".warn;
			^nil
		};

		^this.atTick(TimeConverter.posToTicks(pos, metremap))
	}
}
