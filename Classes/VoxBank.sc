VoxBank {
	var <voxes, <label;

	*new { |label = \anonBank|
		^super.new.init(label);
	}

	init { |labelArg|
		label = labelArg;
		voxes = List.new;
		^this;
	}

	add { |vox|
		if (vox.isKindOf(Vox).or(vox.isKindOf(VoxMulti))) {
			voxes.add(vox);
		} {
			"VoxBank: can only add Vox or VoxMulti.".warn;
		};
	}

	at { |indexOrLabel|
		if (indexOrLabel.isInteger) {
			^voxes[indexOrLabel];
		} {
			^voxes.detect { |v| v.label == indexOrLabel };
		}
	}

	remove { |index|
		voxes.removeAt(index);
	}

	play { |indexOrLabel|
		var vox = this.at(indexOrLabel);
		VoxPlayer.new(vox).play;
	}

	playMIDI { |indexOrLabel, midiout|
		var vox = this.at(indexOrLabel);
		VoxPlayer.new(vox).playMIDI(midiout);
	}

	asArray { ^voxes.asArray }

	size { ^voxes.size }

	clear {
		voxes.clear;
	}
}
