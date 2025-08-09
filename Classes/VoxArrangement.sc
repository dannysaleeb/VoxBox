VoxArrangement : VoxNode {
	var <regions, <metremap;

	*new { |metremap|
		^super.new.init(metremap)
	}

	init { |metremapArg|

		metremap = metremapArg ? MetreMap.new;
		regions = [];

		metremap.isEmpty.if {
			metremap.add(MetreRegion.new(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])))
		};

		^this;
	}

	trackNames {
		^regions.collect({ |region| region.vox.label }).asSet.asArray;
	}

	sortRegions {
		regions.sort({ arg a, b; a.start < b.start })
	}

	add { |region, mode = \replace, metreHandling = \local|
		regions = regions.add(region);
		this.sortRegions;
		^this
	}

	out {
		// grab all tracks as unique labels, create IdenitityDict for each, which has relevant info for plug creation
		// for each region:
		//     get vox or voxmulti
		//     add events to relevant track
		//     metremap is handled on adding (all tracks will share the VoxArrangement metremap, so give VoxMulti a copy at end)
		//     add metadata as needed ...
		var labels = this.trackNames;
		var tracks = Dictionary.newFrom([labels, labels.size.collect(Dictionary.new)].lace);
		var returnVoxes = [];

		// for each vox, copy events to the right track
		regions.do({ |v|
			var track = tracks[v.label];
			track[\events].notNil.if {
				track[\events] = track[\events] ++ v.events;
				track[\events].sort({ arg a, b; a.absTime < b.absTime });
			} {
				track[\events] = v.events
			}
		});

		// for each track name, add a vox with the relevant events to returnVoxes
		labels.do({
			arg label;
			returnVoxes = returnVoxes.add(Vox.new(tracks[label][\events], label: label));
		});

		^VoxMulti.new(returnVoxes, this.metremap, this.label)
	}

	history {}
}

VoxRegion {
	var <start, <vox;

	*new { |start, vox|
		^super.newCopyArgs(start, vox)
	}
}