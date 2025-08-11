VoxHistory {
	var <history, <pointer;

    *new { ^super.new.init }

    init {
        history = List.new;
        pointer = -1;
        ^this
    }

	log {
		history.do({ |entry, i|
			" ------------ version % ------------".format(i).postln;
			"%: %".format(entry.label, entry.time).postln;
		});
	}

    commit { |vox, label=nil|
        // Trim any undone future
        if (pointer < (history.size - 1)) {
            history = history.copyRange(0, pointer);
        };

        history.add((vox: vox.deepCopy, label: label, time: Date.getDate));
        pointer = history.size - 1;
    }

    undo {
        if (pointer > 0) { pointer = pointer - 1 };
        ^history[pointer]
    }

    redo {
        if (pointer < (history.size - 1)) { pointer = pointer + 1 };
        ^history[pointer]
    }

    current { ^history[pointer] }
}