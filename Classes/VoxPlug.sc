VoxPlug {
	var <events, <metremap, <label, <metadata, <>source;

	*new {
		arg events, metremap, label=\anonyPlug, metadata = Dictionary.new, source;

		^super.newCopyArgs(events.deepCopy, metremap.deepCopy, label.copy, metadata.deepCopy, source);
	}

	copy {
		^VoxPlug.new(events.deepCopy, metremap.deepCopy, label.copy, metadata.deepCopy, source);
	}
}

VoxPlugMulti {
    var <plugs, <metremap, <label, <metadata, <>source;

    *new {
		arg plugs, metremap, label=\anonyPlugMulti, metadata = Dictionary.new, source;
        ^super.new.init(plugs, metremap, label, source);
    }

	// is it easiest to take dict or array? Probably dict, given VoxMulti is dict?
	// Maybe never directly construct plugs from module, only a VoxMulti etc...
	init { |plugsArg, metremapArg, labelArg, sourceArg|
		plugs = plugsArg;
		metremap = metremapArg ?? MetreMap.new;
		label = labelArg;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg ? nil;

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
        ^plugs.keys.collect({arg k; plugs[k]});
    }
}