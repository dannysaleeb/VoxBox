VoxArrangement {
	var <>metremap;
	var <>tpqn;
	var timelineDict;  // Dict of trackLabel -> List[ {start, vox, metadata} ]

	*new { |metremap|
		^super.new.init(metremap)
	}

	init { |metremap|
		this.metremap = metremap;
		this.tpqn = metremap.tpqn;
		timelineDict = Dictionary.new;
	}

	addToTrack { |trackLabel, vox, startPos, metadata = ()|
		var entry = (
			start: startPos,
			vox: vox,
			metadata: metadata
		);

		timelineDict[trackLabel] = timelineDict[trackLabel] ?? { List.new };
		timelineDict[trackLabel].add(entry);
	}

	flatten {  // Collect everything into a single VoxMulti
		var allVoxes = [];

		timelineDict.do { |label, events|
			events.do { |entry|
				var shifted = entry[\vox].copyWithShift(entry[\start], metremap);
				shifted.metadata = shifted.metadata.put(\trackLabel, label);
				allVoxes = allVoxes.add(shifted);
			}
		};

		^VoxMulti.new(allVoxes, metremap, \flattened);
	}

	/*asMIDI { |path|
	this.flatten().asMIDIFile(path);  // You’d need to define `asMIDIFile` on VoxMulti
	}*/
}