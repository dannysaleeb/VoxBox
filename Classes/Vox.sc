Vox {
	var <events, <metremap, <label, <metadata, <>source;

	*new {
		arg events, metremap, label, metadata, source;

		^super.new.init(events, metremap, label, metadata, source);
	}

	init {
		arg eventsArg, metremapArg, labelArg, metadataArg, sourceArg;

		events = eventsArg.deepCopy ? [];
		metremap = metremapArg.deepCopy ? MetreMap.new;
		label = labelArg.copy ? \anonyVox;
		metadata = metadataArg.deepCopy ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	copy {
		^Vox.new(events.deepCopy, metremap.deepCopy, label.copy, metadata.deepCopy, source);
	}
}

VoxMulti {
    var <voxes, <metremap, <label, <metadata, <>source;

    *new {
		arg voxes, metremap, label, metadata, source;

        ^super.new.init(voxes, metremap, label, metadata, source);
    }

	init { |voxesArg, metremapArg, labelArg, metadataArg, sourceArg|

		voxesArg.notNil.if {
			var labels;
			labels = voxesArg.collect(_.label);
			voxes = Dictionary.newFrom([labels, voxesArg].lace);
		} {
			voxes = Dictionary.new;
		};

		metremap = metremapArg ? MetreMap.new;
		label = labelArg ? \anonyVoxMulti;
		metadata = metadataArg ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	*fromDict {
		arg voxesDict, metremap, label, metadata, source;

		^super.new.initFromDict(voxesDict, metremap, label, metadata, source)
	}

	initFromDict {
		arg voxesDictArg, metremapArg, labelArg, metadataArg, sourceArg;

		voxes = voxesDictArg ? Dictionary.new;
		metremap = metremapArg ? MetreMap.new;
		label = labelArg ? \anonyVoxMulti;
		metadata = metadataArg ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	// SO THE ID CHECK IS POINTLESS NOW
    at { |key|
		if (key.isInteger) {
			var vox = voxes.values.detect { |p| p.source.id == key };
			if (vox.notNil) {
				^vox
			} {
				"❌ No vox found with index ID %".format(key).warn;
				^nil
			}
		} {
			var vox = voxes[key];
			if (vox.notNil) {
				^vox
			}{
				"❌ No vox found with label %".format(key).warn;
				^nil
			};
		}
    }

    size {
        ^voxes.size
    }

    do { |func|
        this.asArray.do(func);
    }

    asArray {
		// I think this can just be voxes.values;
        ^voxes.values;
    }

	keys {
		^voxes.keys
	}
}