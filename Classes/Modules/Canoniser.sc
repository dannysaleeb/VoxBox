VoxCanoniser : VoxModule {
	var <>numVoices, <>entryOffsets;

	*new { |numVoices = 3, entryOffsets = nil, label|
		^super.new(label).initCanon(numVoices, entryOffsets);
	}

	initCanon { |nv, eo|
		numVoices = nv;
		entryOffsets = eo ?? Array.fill(nv, { |i| Pos(0, i * 2) });
		^this
	}

	doMultiOutput { |plug|
		var events = plug.events;
		var map = plug.metremap;

		^Array.fill(numVoices, { |i|
			var offsetPos = entryOffsets.wrapAt(i);
			var offsetTicks = TimeConverter.posToTicks(offsetPos, map);

			var shifted = events.collect { |ev|
				var newAbs = ev[\absTime] + offsetTicks;
				ev.copy.putAll([
					\absTime: newAbs,
					\position: TimeConverter.ticksToPos(newAbs, map)
				])
			};

			VoxPlug.new(shifted, map, "%_voice_%".format(label, i), plug.metadata.copy)
		});
	}
}