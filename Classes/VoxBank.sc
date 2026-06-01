VoxSnapshotTarget {
	*resolveSnapshot { |source|
		var snapshot;

		if (source.isNil) {
			"Snapshot deposit: source is nil.".warn;
			^nil
		};

		snapshot = source.respondsTo(\out).if({
			source.out
		}, {
			source
		});

		if (snapshot.isKindOf(Vox).or({ snapshot.isKindOf(VoxMulti) })) {
			^snapshot.copy
		};

		"Snapshot deposit: source must resolve to Vox or VoxMulti.".warn;
		^nil
	}
}

VoxBankSlot : VoxSnapshotTarget {
	var <bank, <key, <replaceExisting;

	*new { |bank, key, replaceExisting = false|
		^super.new.init(bank, key, replaceExisting)
	}

	init { |bankArg, keyArg, replaceArg|
		bank = bankArg;
		key = keyArg;
		replaceExisting = replaceArg;
		^this
	}

	storeSnapshot { |source|
		if (replaceExisting) {
			^bank.replace(key, source)
		};
		^bank.add(key, source)
	}
}

VoxBank {
	var <entries, <order, <label;

	*new { |label = \anonBank|
		^super.new.init(label)
	}

	init { |labelArg|
		label = labelArg;
		entries = IdentityDictionary.new;
		order = List.new;
		^this
	}

	slot { |key|
		^VoxBankSlot.new(this, key)
	}

	replaceSlot { |key|
		^VoxBankSlot.new(this, key, true)
	}

	add { |key, source|
		var snapshot;

		if (key.isKindOf(Symbol).not) {
			"VoxBank: slot labels must be Symbols.".warn;
			^this
		};

		if (entries.includesKey(key)) {
			("VoxBank: slot % already exists; use replace.".format(key)).warn;
			^this
		};

		snapshot = VoxSnapshotTarget.resolveSnapshot(source);
		if (snapshot.isNil) { ^this };

		entries[key] = snapshot;
		order.add(key);
		^this
	}

	replace { |key, source|
		var snapshot;

		if (entries.includesKey(key).not) {
			("VoxBank: cannot replace missing slot %.".format(key)).warn;
			^this
		};

		snapshot = VoxSnapshotTarget.resolveSnapshot(source);
		if (snapshot.isNil) { ^this };

		entries[key] = snapshot;
		^this
	}

	keyAt { |indexOrKey|
		if (indexOrKey.isKindOf(Integer)) {
			if ((indexOrKey < 0).or({ indexOrKey >= order.size })) {
				("VoxBank: index % is out of range.".format(indexOrKey)).warn;
				^nil
			};
			^order[indexOrKey]
		};

		if (indexOrKey.isKindOf(Symbol)) {
			^indexOrKey
		};

		"VoxBank: lookup must use a Symbol or Integer index.".warn;
		^nil
	}

	at { |indexOrKey|
		var key, snapshot;

		key = this.keyAt(indexOrKey);
		if (key.isNil) { ^nil };

		snapshot = entries[key];
		if (snapshot.isNil) {
			("VoxBank: slot % does not exist.".format(key)).warn;
			^nil
		};

		^snapshot.copy
	}

	keys {
		^order.copy
	}

	remove { |indexOrKey|
		var key;

		key = this.keyAt(indexOrKey);
		if (key.isNil) { ^this };

		if (entries.includesKey(key).not) {
			("VoxBank: slot % does not exist.".format(key)).warn;
			^this
		};

		entries.removeAt(key);
		order.remove(key);
		^this
	}

	clear {
		entries.clear;
		order.clear;
		^this
	}

	asArray {
		^order.collect({ |key| entries[key].copy })
	}

	size {
		^order.size
	}

	play { |indexOrKey, clock|
		var snapshot;

		snapshot = this.at(indexOrKey);
		if (snapshot.isNil) { ^nil };
		^VoxPlayer.new(snapshot, clock).play
	}

	playMIDI { |indexOrKey, midiout, clock|
		var snapshot;

		snapshot = this.at(indexOrKey);
		if (snapshot.isNil) { ^nil };
		^VoxPlayer.new(snapshot, clock).playMIDI(midiout)
	}
}
