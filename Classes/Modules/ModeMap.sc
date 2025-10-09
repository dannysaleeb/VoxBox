ModeMap : VoxModule {
	var <>source_scale, <>source_root, <>target_scale, <>target_root;

	*new { |source_scale, source_root, target_scale, target_root|
		^super.new.init(source_scale, source_root, target_scale, target_root)
	}

	init {|source_scale, source_root, target_scale, target_root|
		this.source_scale = source_scale ?? Scale.at(\ionian);
		this.source_root = source_root ?? 48;
		this.target_scale = target_scale ?? Scale.at(\minorPentatonic);
		this.target_root = target_root ?? 48;
		^this
	}

	mapMode {
		arg midinote;
		var index, target_pc, octave;

		index = midinote.midi2deg(source_root, source_scale);
		target_pc = target_scale.degrees.wrapAt(index);
		octave = (midinote - source_root) div: 12;

		^target_root + (target_pc + (octave * 12));
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