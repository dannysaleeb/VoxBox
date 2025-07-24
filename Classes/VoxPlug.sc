VoxPlug {
	var <events, <metremap, <label, <metadata, <>source;

	*new {
		arg events, metremap, label=\anonymous, metadata = Dictionary.new, source;

		^super.newCopyArgs(events.deepCopy, metremap.copy, label, metadata.deepCopy, source);
	}

	copy {
		^VoxPlug.new(events.deepCopy, metremap.copy, label, metadata.copy, source);
	}
}

VoxPlugMulti {
    var <plugsDict;

    *new { |plugsDict|
        ^super.newCopyArgs(plugsDict.deepCopy);
    }

    at { |key|
		if (key.isInteger) {
			var plug = plugsDict.values.detect { |p| p.source.id == key };
			if (plug.notNil) {
				^plug
			} {
				"❌ No plug found with index ID %".format(key).warn;
				^nil
			}
		} {
			var plug = plugsDict[key];
			if (plug.notNil) {
				^plug
			}{
				"❌ No plug found with label %".format(key).warn;
				^nil
			};
		}
    }

    size {
        ^plugsDict.size
    }

    do { |func|
        this.asArray.do(func);
    }

    asArray {
        ^plugsDict.keys.collect({arg k; plugsDict[k]});
    }
}