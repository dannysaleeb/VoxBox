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
    var <plugs;

    *new { |plugs|
        ^super.newCopyArgs(plugs.deepCopy);
    }

    at { |index|
        ^plugs[index]
    }

    size {
        ^plugs.size
    }

    do { |func|
        plugs.do(func)
    }

    asArray {
        ^plugs
    }
}