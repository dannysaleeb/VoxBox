CTransposer : VoxModule {
	var <>semitones;

	*new { |semitones = 0|
		^super.new.initTranspose(semitones);
	}

	initTranspose { |semitones|
		this.semitones = semitones;
		^this
	}

	doProcess { |vox|
		var events = vox.events.collect { |ev|
			var newEv = ev.copy;
			newEv[\midinote] = ev[\midinote] + semitones;
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

// Diatonic/microtonal transposer that preserves chromatic offsets by interpolation
DTransposer : VoxModule {
    var <>degrees, <>root, <>scale;

    *new { |degrees = 0, root = 60, scale|
        ^super.new.init(degrees, root, scale);
    }

    init { |degrees, root, scale|
        this.degrees = degrees;
        this.root    = root ? 60;
        this.scale   = scale ? Scale.at(\ionian);
        ^this
    }

	midiToDegree { |midinote|
		var d, oct, pc, idx;
		d = midinote - this.root;
		oct = (d / 12).floor;
		pc = ((d % 12) + 12) % 12;
		idx = this.scale.semitones.indexInBetween(pc.asFloat);
		^(oct * this.scale.size) + idx
	}

    doProcess { |vox|
        var events = vox.events.collect { |ev|
            var midinote = ev[\midinote];
			var relativeDegree, transposedRelativeDegree;
			var base, frac, transposedMidi;
			var newEv;

			relativeDegree = this.midiToDegree(midinote);
			transposedRelativeDegree = relativeDegree + this.degrees;

			base = transposedRelativeDegree.floor;
			frac = transposedRelativeDegree - base;

			transposedMidi = this.root + base.degreeToKey(this.scale);

			if (frac == 0.5) {
				transposedMidi = transposedMidi + 1;
			};

            newEv = ev.copy;
            newEv[\midinote] = transposedMidi;            // keep float; round if needed
            newEv
        };

        ^Vox.new(
            events,
            vox.metremap,
            vox.label,
            vox.metadata.deepCopy
        )
    }
}
