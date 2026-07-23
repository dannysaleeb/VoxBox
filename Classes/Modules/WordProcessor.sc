WordProcessor : VoxModule {
	classvar morsedict;
	var <>mode, <>text, <>oneMult, <>oneOffset, <>zeroMult, <>zeroOffset, <>zeroReplace, <>subSeq;

	*new { |mode=\ascii, text="hello, world", oneMult=1, oneOffset=0, zeroMult=1, zeroOffset=0, zeroReplace=nil, subSeq=nil|
		^super.new.init(mode, text, oneMult, oneOffset, zeroMult, zeroOffset, zeroReplace, subSeq);
	}

	init { |mode, text, oneMult, oneOffset, zeroMult, zeroOffset, zeroReplace, subSeq|

		this.mode = mode;
		this.text = text;
		this.oneMult = oneMult;
		this.oneOffset = oneOffset;
		this.zeroMult = zeroMult;
		this.zeroOffset = zeroOffset;
		this.zeroReplace = zeroReplace;
		this.subSeq = subSeq;

		if (zeroReplace == \seq) {
			if (subSeq.isKindOf(SequenceableCollection).not) {
				"SequenceableCollection required for subSeq, got %".format(subSeq.class).warn;
				this.zeroReplace = nil;
			}
		};

		// change these to binary representation  ...
		morsedict = Dictionary.newFrom([
			\a, [\dit,\dah],
			\b, [\dah,\dit,\dit,\dit],
			\c, [\dah,\dit,\dah,\dit],
			\d, [\dah,\dit,\dit],
			\e, [\dit],
			\f, [\dit,\dit,\dah,\dit],
			\g, [\dah,\dah,\dit],
			\h, [\dit,\dit,\dit,\dit],
			\i, [\dit,\dit],
			\j, [\dit,\dah,\dah,\dah],
			\k, [\dah,\dit,\dah],
			\l, [\dit,\dah,\dit,\dit],
			\m, [\dah,\dah],
			\n, [\dah,\dit],
			\o, [\dah,\dah,\dah],
			\p, [\dit,\dah,\dah,\dit],
			\q, [\dah,\dah,\dit,\dah],
			\r, [\dit,\dah,\dit],
			\s, [\dit,\dit,\dit],
			\t, [\dah],
			\u, [\dit,\dit,\dah],
			\v, [\dit,\dit,\dit,\dah],
			\w, [\dit,\dah,\dah],
			\x, [\dah,\dit,\dit,\dah],
			\y, [\dah,\dit,\dah,\dah],
			\z, [\dah,\dah,\dit,\dit]
		]);

		^this
	}

	provenanceSpec {
		^(
			op: \wordProcess,
			params: (
				mode: mode,
				text: text,
				oneMult: oneMult,
				oneOffset: oneOffset,
				zeroMult: zeroMult,
				zeroOffset: zeroOffset,
				zeroReplace: zeroReplace,
				subSeq: subSeq
			)
		)
	}

	ascii2bin {

		if (text.isKindOf(String).not) {
			"text arg requires String, got %".format(text).warn;
			^this
		};

		^text.collectAs({
			arg char;
			char.ascii.asBinaryDigits
		}, Array).flatten;

	}

	asciiselect { |events|
		var
		bin = this.ascii2bin,
		currentTime = events[0].absTime,
		processed;

		events = events.sort({arg a, b; a.absTime < b.absTime}).groupBy(\absTime);

		processed = bin.collect({
			arg bit, i;
			if (bit == 0) {
				switch (zeroReplace)
				{nil} {
					zeroMult.collect({
						arg j;
						var newEv = events.wrapAt((i + j) + zeroOffset).deepCopy;
						var dur;
						if (newEv.isKindOf(SequenceableCollection)) {
							newEv.do({
								arg ev;
								ev.mute = true;
								ev.absTime = currentTime;
								dur.isNil.if {dur = ev.dur}
							})
						} {
							newEv.mute = true;
							newEv.absTime = currentTime;
							dur = newEv.dur;
						};

						currentTime = currentTime + dur;
						newEv
					})
				}
				{\this} {
					zeroMult.collect({
						arg j;
						var newEv = events.wrapAt((i + j) + zeroOffset).deepCopy;
						newEv.absTime = currentTime;
						currentTime = currentTime + newEv.dur;
						newEv
					})
				}
				{\seq} {
					if (subSeq.isNil) {
						"No SequenceableCollection assigned to subSeq".warn;
						^events
					};

					zeroMult.collect({
						arg j;
						var newEv = subSeq.wrapAt((i + j) + zeroOffset).deepCopy;
						var dur;
						if (newEv.isKindOf(SequenceableCollection)) {
							newEv.do({
								arg ev;
								ev.absTime = currentTime;
								dur.isNil.if {dur = ev.dur}
							})
						} {
							newEv.absTime = currentTime;
							dur = newEv.dur;
						};
						currentTime = currentTime + dur;
						newEv
					})
				}
			} {
				oneMult.collect({
					arg j;
					var newEv = events.wrapAt((i + j) + oneOffset).deepCopy;
					var dur;
					if (newEv.isKindOf(SequenceableCollection)) {
						newEv.do({
							arg ev;
							ev.absTime = currentTime;
							dur.isNil.if {dur = ev.dur}
						})
					} {
						newEv.absTime = currentTime;
						dur = newEv.dur;
					};
					currentTime = currentTime + dur;
					newEv
				})
			}
		}).flatten(2);

		^processed
	}

	morse2bin {
		if (text.isKindOf(String).not) {
			"text arg requires String, got %".format(text).warn;
			^this
		};

		^text.morse2bin
	}

	morseselect { |events|
		var
		bin = this.morse2bin,
		currentTime = events[0].absTime,
		processed;

		events = events.sort({arg a, b; a.absTime < b.absTime}).groupBy(\absTime);

		processed = bin.collect({
			arg bit, i;
			if (bit == 0) {
				switch (zeroReplace)
				{nil} {
					zeroMult.collect({
						arg j;
						var newEv = events.wrapAt((i + j) + zeroOffset).deepCopy;
						var dur;
						if (newEv.isKindOf(SequenceableCollection)) {
							newEv.do({
								arg ev;
								ev.mute = true;
								ev.absTime = currentTime;
								dur.isNil.if {dur = ev.dur}
							})
						} {
							newEv.mute = true;
							newEv.absTime = currentTime;
							dur = newEv.dur;
						};

						currentTime = currentTime + dur;
						newEv
					})
				}
				{\this} {
					zeroMult.collect({
						arg j;
						var newEv = events.wrapAt((i + j) + zeroOffset).deepCopy;
						newEv.absTime = currentTime;
						currentTime = currentTime + newEv.dur;
						newEv
					})
				}
				{\seq} {
					if (subSeq.isNil) {
						"No SequenceableCollection assigned to subSeq".warn;
						^events
					};

					zeroMult.collect({
						arg j;
						var newEv = subSeq.wrapAt((i + j) + zeroOffset).deepCopy;
						var dur;
						if (newEv.isKindOf(SequenceableCollection)) {
							newEv.do({
								arg ev;
								ev.absTime = currentTime;
								dur.isNil.if {dur = ev.dur}
							})
						} {
							newEv.absTime = currentTime;
							dur = newEv.dur;
						};
						currentTime = currentTime + dur;
						newEv
					})
				}
			} {
				oneMult.collect({
					arg j;
					var newEv = events.wrapAt((i + j) + oneOffset).deepCopy;
					var dur;
					if (newEv.isKindOf(SequenceableCollection)) {
						newEv.do({
							arg ev;
							ev.absTime = currentTime;
							dur.isNil.if {dur = ev.dur}
						})
					} {
						newEv.absTime = currentTime;
						dur = newEv.dur;
					};
					currentTime = currentTime + dur;
					newEv
				})
			}
		}).flatten(2);

		^processed
	}

	// they need to be fully alternating ... would it be a bad idea to have the outcome on two channels?
	// first, just tot up cumulative time

	doProcess { |vox|
		var events = vox.events.deepCopy;

		// Position and Metre need to be updated later ...

		switch (mode)
		{\ascii} {events = this.asciiselect(events)}
		{\morse} {events = this.morseselect(events)}
		{
			"Unrecognised mode: choose \ascii or \morse - set to ascii as default".warn;
			events = events.asciiselect(text, oneMult, oneOffset, zeroMult, zeroOffset, zeroReplace, subSeq)
		};

		^Vox.new(
			events,
			vox.metremap,
			vox.label,
			vox.metadata.copy
		)
	}
}
