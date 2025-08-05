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
        ^plugs.keys.collect({arg k; plugs[k]});
    }
}