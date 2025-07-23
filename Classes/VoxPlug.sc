VoxPlug {
	var <events, <metremap, <label, <metadata;

	*new {
		arg events, metremap, label=\anonymous, metadata = Dictionary.new;

		^super.newCopyArgs(events.deepCopy, metremap.copy, label, metadata.deepCopy);
	}

	copy {
		^VoxPlug.new(events.deepCopy, metremap.copy, label, metadata.copy);
	}
}

VoxPlugMulti {
    var <plugsDict;

    *new { |plugsDict|
        ^super.newCopyArgs(plugsDict.deepCopy);
    }

    at { |key|
        ^plugsDict[key]
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