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

	// change how this accesses voices ...
	doMultiOutput { |plug|
		var events = plug.events;
		var map = plug.metremap;
		var plugDict = Dictionary.new;

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

			var vox = Vox.new(shifted, map, key);
			var voxPlug = vox.out;

			plugDict[key] = voxPlug;
		});

		^plugDict;
	}
}

// PlugMulti should always structure plugs as label -> plug

// I'm not sure this is what Canon should return. Probably it should return VoxMulti (which produces VoxPlugMulti when .out is called on it ...), no I think it should return VoxPlugMulti