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