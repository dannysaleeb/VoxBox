VoxPlug {
	var <events, <metremap, <label, <metadata, <>source;

	*new {
		arg events, metremap, label=\anonymous, metadata = Dictionary.new, source;

		^super.newCopyArgs(events.deepCopy, metremap.deepCopy, label.copy, metadata.deepCopy, source);
	}

	copy {
		^VoxPlug.new(events.deepCopy, metremap.deepCopy, label.copy, metadata.deepCopy, source);
	}
}

VoxPlugMulti {
    var <plugs;

    *new { |plugs|
        ^super.new.init(plugs);
    }

	init { |plugsArg|
		plugs = plugsArg;
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