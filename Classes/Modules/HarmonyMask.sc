HarmonyMask : VoxModule {
	var <>root, <>scale, <>window, <>seed;

	*new { |root = 60, scale, window = 12, seed|
		^super.new.init(root, scale, window, seed);
	}

	init { |root, scale, window, seed|
		this.root = root ? 60;
		this.scale = scale ? Scale.at(\ionian);
		this.window = window ? 12;
		this.seed = seed;
		^this
	}

	isScaleMidi { |midinote|
		var pc = (((midinote - root) % 12) + 12) % 12;
		^scale.semitones.detect { |semitone|
			semitone.asInteger == pc.asInteger
		}.notNil;
	}

	candidatesFor { |center|
		var low = (center - window).ceil.asInteger;
		var high = (center + window).floor.asInteger;

		^(low..high).select { |note|
			this.isScaleMidi(note)
		}
	}

	assignPitch { |ev|
		var metadata = ev[\metadata] ? Dictionary.new;
		var center = metadata[\grain_origin_midinote] ? ev[\midinote];
		var candidates = this.candidatesFor(center);

		candidates.isEmpty.if {
			^ev[\midinote]
		};

		^candidates.choose
	}

	doProcess { |vox|
		seed.notNil.if {
			thisThread.randSeed = seed;
		};

		^Vox.new(
			vox.events.collect { |ev|
				var newEv = ev.copy;
				var metadata = ev[\metadata].notNil.if {
					ev[\metadata].copy
				} {
					Dictionary.new
				};
				var isOnset = metadata[\grain_is_onset] == true;
				var assigned = isOnset.if {
					ev[\midinote]
				} {
					this.assignPitch(ev)
				};

				metadata[\harmony_mask_origin_midinote] = ev[\midinote];
				metadata[\harmony_mask_assigned_midinote] = assigned;
				newEv[\midinote] = assigned;
				newEv[\metadata] = metadata;

				newEv
			},
			vox.metremap,
			vox.label,
			vox.metadata.copy
		)
	}
}
