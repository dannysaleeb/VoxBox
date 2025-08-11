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
		^regions.collect({ |region| region.box.label }).asSet.asArray;
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
		// grab all tracks as unique labels, create IdenitityDict for each, which has relevant info for vox creation
		// for each region:
		//     get box or boxmulti
		//     add events to relevant track
		//     metremap is handled on adding (all tracks will share the VoxArrangement metremap, so give BoxMulti a copy at end)
		//     add metadata as needed ...
		var labels = this.trackNames;
		var tracks = Dictionary.newFrom([labels, labels.size.collect(Dictionary.new)].lace);
		var returnBoxes = [];

		// for each box, copy events to the right track
		regions.do({ |v|
			var track = tracks[v.label];
			track[\events].notNil.if {
				track[\events] = track[\events] ++ v.events;
				track[\events].sort({ arg a, b; a.absTime < b.absTime });
			} {
				track[\events] = v.events
			}
		});

		// for each track name, add a box with the relevant events to returnBoxes
		labels.do({
			arg label;
			returnBoxes = returnBoxes.add(Box.new(tracks[label][\events], label: label));
		});

		^BoxMulti.new(returnBoxes, this.metremap, this.label)
	}

	history {}
}

VoxRegion {
	var <start, <box;

	*new { |start, box|
		^super.newCopyArgs(start, box)
	}
}