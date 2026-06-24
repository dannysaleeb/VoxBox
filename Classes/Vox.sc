Vox {
	var <events, <metremap, <label, <metadata, <>source;

	*new {
		arg events, metremap, label, metadata, source;

		^super.new.init(events, metremap, label, metadata, source);
	}

	init {
		arg eventsArg, metremapArg, labelArg, metadataArg, sourceArg;

		events = eventsArg.deepCopy ? [];
		metremap = metremapArg.deepCopy ? MetreMap.new;
		label = labelArg.copy ? \anonyVox;
		metadata = metadataArg.deepCopy ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	copy {
		^Vox.new(events.deepCopy, metremap.deepCopy, label.copy, metadata.deepCopy, source);
	}

	splitByChannel { |labelPrefix|
		var channels, splitVoxes, multi, prefix;

		prefix = labelPrefix ? label;
		channels = events.collect { |event| event[\channel] ? 0 }.asSet.asArray.sort;

		splitVoxes = channels.collect { |channel, index|
			var splitEvents, splitLabel, splitMetadata;

			splitEvents = events.select { |event|
				(event[\channel] ? 0) == channel
			}.deepCopy;

			splitEvents.do { |event|
				event[\channel] = 0;
			};

			splitLabel = ("%_%".format(prefix, index + 1)).asSymbol;
			splitMetadata = metadata.deepCopy;
			splitMetadata[\sourceChannel] = channel;
			splitMetadata[\provenance] = VoxProvenance.boundary(
				\splitByChannel,
				(label: splitLabel, sourceChannel: channel, outputChannel: 0),
				this.provenance
			);

			Vox.new(splitEvents, metremap, splitLabel, splitMetadata, this)
		};

		multi = VoxMulti.new(splitVoxes, metremap, label, metadata, this);
		multi.metadata[\provenance] = VoxProvenance.boundary(
			\splitByChannel,
			(labels: splitVoxes.collect(_.label), outputChannel: 0),
			this.provenance
		);
		^multi
	}

	provenance {
		^VoxProvenance.provenanceOf(this)
	}

	postProvenance {
		^VoxProvenance.postObject(this)
	}

	audition { |clock, quant|
		^this.play(clock, quant)
	}

	play { |clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.play(quant);
		^player
	}

	loop { |clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.loop(quant);
		^player
	}

	playMIDI { |midiout, clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.playMIDI(midiout, quant);
		^player
	}

	loopMIDI { |midiout, clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.loopMIDI(midiout, quant);
		^player
	}

	>>= { |target|
		if (target.respondsTo(\storeSnapshot)) {
			^target.storeSnapshot(this)
		};

		"Vox: >>= requires a snapshot slot.".warn;
		^target
	}
}

VoxMulti {
    var <voxes, <metremap, <label, <metadata, <>source;

    *new {
		arg voxes, metremap, label, metadata, source;

        ^super.new.init(voxes, metremap, label, metadata, source);
    }

	init { |voxesArg, metremapArg, labelArg, metadataArg, sourceArg|

			voxesArg.notNil.if {
				var labels;
				labels = voxesArg.collect(_.label);
				voxes = Dictionary.new;
				voxesArg.do { |vox|
					if (voxes[vox.label].notNil) {
						"VoxMulti: duplicate label % rejected.".format(vox.label).warn;
					} {
						voxes[vox.label] = vox.copy;
					}
				};
			} {
				voxes = Dictionary.new;
			};

			metremap = metremapArg.deepCopy ? MetreMap.new;
			label = labelArg.copy ? \anonyVoxMulti;
			metadata = metadataArg.deepCopy ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	*fromDict {
		arg voxesDict, metremap, label, metadata, source;

		^super.new.initFromDict(voxesDict, metremap, label, metadata, source)
	}

	initFromDict {
		arg voxesDictArg, metremapArg, labelArg, metadataArg, sourceArg;

			voxes = Dictionary.new;
			if (voxesDictArg.notNil) {
				voxesDictArg.keysValuesDo { |key, vox|
					voxes[key] = vox.copy;
				}
			};
			metremap = metremapArg.deepCopy ? MetreMap.new;
			label = labelArg.copy ? \anonyVoxMulti;
			metadata = metadataArg.deepCopy ? Dictionary.new;

		// ensure metremap has a metre
		metremap.isEmpty.if {
			metremap.add(MetreRegion(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};

		source = sourceArg;

		^this
	}

	// SO THE ID CHECK IS POINTLESS NOW
    at { |key|
		if (key.isInteger) {
			var vox = voxes.values.detect { |p|
				p.source.notNil and: { p.source.respondsTo(\id) and: { p.source.id == key } }
			};
			if (vox.notNil) {
				^vox
			} {
				"❌ No vox found with index ID %".format(key).warn;
				^nil
			}
		} {
			var vox = voxes[key];
			if (vox.notNil) {
				^vox
			}{
				"❌ No vox found with label %".format(key).warn;
				^nil
			};
		}
    }

    size {
        ^voxes.size
    }

    do { |func|
        this.asArray.do(func);
    }

    asArray {
		// I think this can just be voxes.values;
        ^voxes.values;
    }

	keys {
		^voxes.keys
	}

	copy {
			^VoxMulti.fromDict(voxes, metremap, label, metadata, source);
	}

	splitByChannel { |labelPrefix|
		var splitVoxes, multi;

		splitVoxes = this.asArray.collect { |vox|
			var prefix = labelPrefix.notNil.if {
				("%_%".format(labelPrefix, vox.label)).asSymbol
			} {
				nil
			};
			vox.splitByChannel(prefix).asArray
		}.flatten;

		multi = VoxMulti.new(splitVoxes, metremap, label, metadata, this);
		multi.metadata[\provenance] = VoxProvenance.boundary(
			\splitByChannel,
			(labels: splitVoxes.collect(_.label), outputChannel: 0),
			this.provenance
		);
		^multi
	}

	provenance {
		^VoxProvenance.provenanceOf(this)
	}

	postProvenance {
		^VoxProvenance.postObject(this)
	}

	audition { |clock, quant|
		^this.play(clock, quant)
	}

	play { |clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.play(quant);
		^player
	}

	loop { |clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.loop(quant);
		^player
	}

	playMIDI { |midiout, clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.playMIDI(midiout, quant);
		^player
	}

	loopMIDI { |midiout, clock, quant|
		var player = VoxPlayer.new(this, clock);
		player.loopMIDI(midiout, quant);
		^player
	}

	>>= { |target|
		if (target.respondsTo(\storeSnapshot)) {
			^target.storeSnapshot(this)
		};

		"VoxMulti: >>= requires a snapshot slot.".warn;
		^target
	}
}
