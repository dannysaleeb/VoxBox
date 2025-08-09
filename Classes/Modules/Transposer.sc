CTransposer : VoxModule {
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

// Diatonic/microtonal transposer that preserves chromatic offsets by interpolation
DTransposer : VoxModule {
    var <>degrees, <>root, <>scale, <>steps;

    *new { |degrees = 0, root = 60, scale, steps|
        ^super.new.init(degrees, root, scale, steps);
    }

    init { |degrees, root, scale, steps|
        this.degrees = degrees;
        this.root    = root ? 60;
        this.scale   = scale ? Scale.at(\ionian);
        this.steps   = steps ? this.scale.tuning.stepsPerOctave ? 12;
        ^this
    }

	midiToDegIndex { |midi, scale, root=60, steps|
		var sc = scale ? Scale.major;
		var sp   = steps ? sc.tuning.stepsPerOctave ? 12;
		var degs = sc.degrees;                  // e.g. [0,2,4,5,7,9,11] in 12-TET
		var key12All = (midi - root) * (12 / sp);  // put MIDI into 12-TET semitone space
		var n    = (key12All / 12).floor;          // octave
		var key12 = key12All - (n * 12);           // 0..12

		// This returns integer on exact match, fractional (e.g. .5) in between
		var idx  = degs.indexInBetween(key12);
		^(n * degs.size) + idx
	}

	degIndexToMidi { |dIndex, scale, root=60, steps|
		var sc = scale ? Scale.major;
		var sp   = steps ? sc.tuning.stepsPerOctave ? 12;
		var degs = sc.degrees;
		var n    = (dIndex / degs.size).floor;
		var pos  = dIndex - (n * degs.size);
		var i    = pos.floor;
		var f    = pos - i;

		// interpolate between lower/upper anchors (handle 11 -> 0 wrap)
		var lo   = degs.wrapAt(i);
		var hi   = degs.wrapAt(i + 1);
		var gap  = ((hi - lo) % 12 + 12) % 12;     // 1 or 2 for diatonic 12-TET
		var key12 = lo + (gap * f);
		^root + (n * sp) + (key12 * (sp / 12))
	}

    doProcess { |plug|
        var events = plug.events.collect { |ev|
            var m = ev[\midinote];
			var d, d2, out, newEv;
            if(m.isNil) { ^ev }; // pass-through if no pitch

            d  = this.midiToDegIndex(m);
            d2 = d + degrees;               // modal shift (fractions allowed)
            out = this.degIndexToMidi(d2);

            newEv = ev.copy;
            newEv[\midinote] = out;            // keep float; round if needed
            newEv
        };

        ^VoxPlug.new(
            events,
            plug.metremap,
            plug.label,
            plug.metadata.deepCopy
        )
    }
}
