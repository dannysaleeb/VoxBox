VoxCanoniser : VoxModule {
	var <>numVoices, <>namesToOffsetsDict;

	*new { |numVoices = 3, voxNames, entryOffsets, label = "Anon_Canon"|
		^super.new(label).initCanon(numVoices, voxNames, entryOffsets);
	}

	initCanon { |numVoicesArg, voxNamesArg, entryOffsetsArg, labelArg|
		var voxnames, offsets;

		numVoices = numVoicesArg;
		voxnames = voxNamesArg ?? this.getNames(numVoicesArg);
		offsets = entryOffsetsArg ?? Array.fill(numVoicesArg, { |i| Pos(0, i * 2) });

		if (voxnames.size != offsets.size) {
			"😬 VoxCanoniser: Mismatch between voice names and entry offsets.".warn;
		};
		namesToOffsetsDict = [voxnames, offsets].lace.asDict;

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

			var box = Box.new(shifted, map, key);
			var vox = box.out;

			voxDict[key] = vox;
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