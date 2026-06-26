VoxRandChannels : VoxModule {
	var <channels, <probabilities, <channelsToProbDict;

	*new { |channelsToProbDict|
		^super.new.init(channelsToProbDict)
	}

	init {|channelsToProbDictArg|
		this.channelsToProbDict = channelsToProbDictArg ?? Dictionary.newFrom([0, 1]); // default everything channel 0
		this.setChannelsAndProbs(channelsToProbDict);
		^this
	}

	channelsToProbDict_ { |value|
		channelsToProbDict = value ? Dictionary.newFrom([0, 1]);
		this.setChannelsAndProbs(value);
		this.touch
	}

	setChannelsAndProbs { |dict|
		var total;

		channels = dict.keys.asArray.sort;
		total = dict.values.sum;

		probabilities = channels.collect { |chan|
			dict[chan] / total
		};
	}

	doProcess { |vox|
		var events = vox.events.collect({ |ev|
			var newEv = ev.copy;
			newEv[\channel] = this.channels.wchoose(probabilities); // choose from channels with prob map
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
