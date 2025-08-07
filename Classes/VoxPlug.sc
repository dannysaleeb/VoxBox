VoxPlug {
	var <events, <metremap, <label, <metadata, <>source;

	*new {
		arg events, metremap, label, metadata, source;

		^super.new.init(events, metremap, label, metadata, source);
	}

	init {
		arg eventsArg, metremapArg, labelArg, metadataArg, sourceArg;

		events = eventsArg.deepCopy ? [];
		metremap = metremapArg.deepCopy ? MetreMap.new;
		label = labelArg.copy ? \anonyPlug;
		metadata = metadataArg.deepCopy ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	copy {
		^VoxPlug.new(events.deepCopy, metremap.deepCopy, label.copy, metadata.deepCopy, source);
	}
}

VoxPlugMulti {
    var <plugs, <metremap, <label, <metadata, <>source;

    *new {
		arg plugs, metremap, label, metadata, source;

        ^super.new.init(plugs, metremap, label, metadata, source);
    }

	init { |plugsArg, metremapArg, labelArg, metadataArg, sourceArg|

		plugsArg.notNil.if {
			var labels;
			labels = plugsArg.collect(_.label);
			plugs = Dictionary.newFrom([labels, plugsArg].lace);
		} {
			plugs = Dictionary.new;
		};

		metremap = metremapArg ? MetreMap.new;
		label = labelArg ? \anonyPlugMulti;
		metadata = metadataArg ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	*fromDict {
		arg plugsDict, metremap, label, metadata, source;

		^super.new.initFromDict(plugsDict, metremap, label, metadata, source)
	}

	initFromDict {
		arg plugsDictArg, metremapArg, labelArg, metadataArg, sourceArg;

		plugs = plugsDictArg ? Dictionary.new;
		metremap = metremapArg ? MetreMap.new;
		label = labelArg ? \anonyPlugMulti;
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
			var plug = plugs.values.detect { |p| p.source.id == key };
			if (plug.notNil) {
				^plug
			} {
				"❌ No plug found with index ID %".format(key).warn;
				^nil
			}
		} {
			var plug = plugs[key];
			if (plug.notNil) {
				^plug
			}{
				"❌ No plug found with label %".format(key).warn;
				^nil
			};
		}
    }

    size {
        ^plugs.size
    }

    do { |func|
        this.asArray.do(func);
    }

    asArray {
		// I think this can just be plugs.values;
        ^plugs.values;
    }

	keys {
		^plugs.keys
	}
}