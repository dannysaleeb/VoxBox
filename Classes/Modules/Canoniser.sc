VoxCanoniser : VoxModule {
	var <numVoices, <voiceNames, <entryOffsets, <namesToOffsetsDict;

	*new { |numVoices = 3, voxNames, entryOffsets, label = "Anon_Canon"|
		^super.new(label).initCanon(numVoices, voxNames, entryOffsets);
	}

	*voices { |value = 3|
		^this.new(value)
	}

	updateNamesToOffsets {
		if (voiceNames.size != entryOffsets.size) {
			"VoxCanoniser: mismatch between voice names and entry offsets.".warn;
		};
		namesToOffsetsDict = [voiceNames, entryOffsets].lace.asDict;
		^this
	}

	numVoices_ { |value|
		numVoices = value;
		this.touch
	}

	voices { |value = 3|
		numVoices = value;
		voiceNames = this.getNames(numVoices);
		entryOffsets = Array.fill(numVoices, { |i| Pos(0, i * 2) });
		this.updateNamesToOffsets;
		this.touch;
		^this
	}

	names { |... values|
		voiceNames = values.flat;
		numVoices = voiceNames.size;
		if (entryOffsets.isNil or: { entryOffsets.size != numVoices }) {
			entryOffsets = Array.fill(numVoices, { |i| Pos(0, i * 2) });
		};
		this.updateNamesToOffsets;
		this.touch;
		^this
	}

	offsets { |... values|
		entryOffsets = values.flat;
		if (voiceNames.isNil or: { voiceNames.size != entryOffsets.size }) {
			numVoices = entryOffsets.size;
			voiceNames = this.getNames(numVoices);
		};
		this.updateNamesToOffsets;
		this.touch;
		^this
	}

	namesToOffsetsDict_ { |value|
		namesToOffsetsDict = value;
		voiceNames = namesToOffsetsDict.keys.asArray;
		entryOffsets = voiceNames.collect { |key| namesToOffsetsDict[key] };
		numVoices = voiceNames.size;
		this.touch
	}

	initCanon { |numVoicesArg, voxNamesArg, entryOffsetsArg, labelArg|
		numVoices = numVoicesArg;
		voiceNames = voxNamesArg ?? this.getNames(numVoicesArg);
		entryOffsets = entryOffsetsArg ?? Array.fill(numVoicesArg, { |i| Pos(0, i * 2) });

		if (voiceNames.size != entryOffsets.size) {
			"😬 VoxCanoniser: Mismatch between voice names and entry offsets.".warn;
		};
		namesToOffsetsDict = [voiceNames, entryOffsets].lace.asDict;

		^this
	}

	getNames { |nv|
		var namesArr = Array.new(nv);
		nv.do({ |i| namesArr.add("%_vox-%".format(this.label, i)) });
		^namesArr
	}

	// need a switcher based on single vox or voxmulti
	doMultiOutput { |vox|
		var events = vox.events;
		var map = vox.metremap;
		var voxDict = Dictionary.new;

		// I want to take each of namesToOffsets and
		// do process of shifting absTime on each event
		//
		namesToOffsetsDict.keys.do({
			arg key;
			var offsetPos = namesToOffsetsDict[key];
			var offsetTicks = TimeConverter.posToTicks(offsetPos, map);

			var shifted = events.collect({
				arg ev;
				var newAbsTime = ev[\absTime] + offsetTicks;
				ev.copy.putAll([
					\absTime: newAbsTime,
					\position: TimeConverter.ticksToPos(newAbsTime, map)
				]);
			});

			voxDict[key] = Vox.new(shifted, map, key, vox.metadata, this);
		});

		^VoxMulti.fromDict(voxDict, map, label, source: this);

		// in the case of VoxMulti input,
		// apply absTime shift to all voxes within each VoxMulti in turn
		// merge VoxMultis into bigger VoxMulti, what is this? It would need to be broken down i think ...
		// flattening must occur ... VoxMulti in has ... 3 voxes each labelled ... resulting multis have an overall label,
		// new labels would need to be created in this situation ...

		// OR just do it with multiple instances of Canoniser ... canonise the individual voices ...
	}
}
