VoxRandChannelBlock : VoxModule {
	var <blocks, <weights, <sourceChannels, <seed;

	*new { |blocks, weights, sourceChannels, seed|
		^super.new.initRandChannelBlock(blocks, weights, sourceChannels, seed)
	}

	initRandChannelBlock { |blocksArg, weightsArg, sourceChannelsArg, seedArg|
		blocks = blocksArg ? [[0]];
		weights = weightsArg ? Array.fill(blocks.size, 1);
		sourceChannels = sourceChannelsArg;
		seed = seedArg;
		^this
	}

	blocks_ { |value| blocks = value; this.touch }
	weights_ { |value| weights = value; this.touch }
	sourceChannels_ { |value| sourceChannels = value; this.touch }
	seed_ { |value| seed = value; this.touch }

	inputChannelsFor { |voxes|
		if (sourceChannels.notNil) { ^sourceChannels.asArray };
		^voxes.collect { |vox|
			vox.events.collect { |event| event[\channel] ? 0 }
		}.flatten.asSet.asArray.sort
	}

	validSpecFor { |inputChannels|
		var validChannels, validWeights;

		if (inputChannels.isEmpty) { ^true };
		if (blocks.isNil or: { blocks.isEmpty }) {
			"VoxRandChannelBlock: expected at least one destination block.".warn;
			^false
		};
		if (weights.isNil or: { weights.size != blocks.size }) {
			"VoxRandChannelBlock: weights must match the number of destination blocks.".warn;
			^false
		};

		validChannels = inputChannels.every { |channel|
			channel.isKindOf(Integer) and: { channel >= 0 and: { channel <= 15 } }
		} and: {
			blocks.every { |block|
				block.size == inputChannels.size and: {
					block.every { |channel|
						channel.isKindOf(Integer) and: { channel >= 0 and: { channel <= 15 } }
					}
				}
			}
		};
		if (validChannels.not) {
			"VoxRandChannelBlock: source and destination blocks must have equal sizes and contain MIDI channels from 0 to 15.".warn;
			^false
		};

		validWeights = weights.every { |weight| weight.isNumber and: { weight >= 0 } };
		if (validWeights.not or: { weights.sum <= 0 }) {
			"VoxRandChannelBlock: weights must be non-negative and at least one must be greater than zero.".warn;
			^false
		};
		^true
	}

	chooseMapping { |inputChannels|
		var probabilities, chosen, mapping;

		if (this.validSpecFor(inputChannels).not) { ^nil };
		if (inputChannels.isEmpty) { ^Dictionary.new };
		probabilities = weights / weights.sum;
		chosen = blocks.wchoose(probabilities);
		mapping = Dictionary.new;
		inputChannels.do { |channel, index| mapping[channel] = chosen[index] };
		^mapping
	}

	remapVox { |vox, mapping|
		var events;

		events = vox.events.collect { |event|
			var newEvent, mapped;
			newEvent = event.copy;
			mapped = mapping[event[\channel] ? 0];
			if (mapped.notNil) { newEvent[\channel] = mapped };
			newEvent
		};
		^Vox.new(events, vox.metremap, vox.label, vox.metadata.copy, this)
	}

	doProcess { |vox|
		^this.withSeed(seed, {
			var mapping;
			mapping = this.chooseMapping(this.inputChannelsFor([vox]));
			mapping.isNil.if { vox.copy } { this.remapVox(vox, mapping) }
		})
	}

	doMultiProcess { |voxMulti|
		^this.withSeed(seed, {
			var voxes, mapping, processed;
			voxes = voxMulti.asArray;
			mapping = this.chooseMapping(this.inputChannelsFor(voxes));
			if (mapping.isNil) { ^voxMulti.copy };
			processed = voxes.collect { |vox| this.remapVox(vox, mapping) };
			VoxMulti.new(processed, voxMulti.metremap, voxMulti.label, voxMulti.metadata, this)
		})
	}
}
