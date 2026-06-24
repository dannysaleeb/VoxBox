VoxProvenance {
	*node { |op, params, input|
		var recipe = Dictionary.new;
		recipe[\op] = op;
		if (params.notNil) { recipe[\params] = this.copyTree(params) };
		if (input.notNil) { recipe[\input] = this.copyTree(input) };
		^recipe
	}

	*copyTree { |value|
		var copied;

		if (value.isKindOf(Dictionary)) {
			copied = Dictionary.new;
			value.keysValuesDo { |key, item|
				copied[key.asSymbol] = this.copyTree(item)
			};
			^copied
		};
		if (value.isKindOf(SequenceableCollection) and: { value.isString.not }) {
			^value.collect(this.copyTree(_))
		};
		^value.deepCopy
	}

	*boundary { |op, params, input|
		^this.node(op, params, input)
	}

	*provenanceOf { |object|
		var stored;

		if (object.isNil) { ^nil };
		if (object.respondsTo(\metadata) and: { object.metadata.notNil }) {
			stored = object.metadata[\provenance];
			if (stored.notNil) { ^stored.deepCopy };
		};
		^this.recipeFor(object)
	}

	*recipeFor { |object|
		var params, input, map, range, branches;

		if (object.isNil) { ^nil };
			if (object.isKindOf(Vox)) {
				^this.node(\frozenVox, (label: object.label))
			};

			if (object.isKindOf(VoxMulti)) {
				^this.node(\frozenVoxMulti, (label: object.label), object.asArray.collect { |vox|
					this.provenanceOf(vox)
				})
			};

		if (object.isKindOf(Box)) {
			^this.node(\manualBox, (label: object.label))
		};

		if (object.isKindOf(BoxMulti)) {
			^this.node(\boxMulti, (label: object.label), object.boxes.values.collect { |box|
				this.provenanceOf(box)
			})
		};

		if (object.isKindOf(VoxClipper)) {
			map = object.input.out.metremap;
			range = TimeRange.from(object.rangeArg, map);
			^this.node(\clip, (rangeTicks: range.asArray), this.provenanceOf(object.input))
		};

			if (object.isKindOf(VoxRangeModule)) {
				if (object.input.notNil) {
					map = object.input.out.metremap;
				};
				range = TimeRange.from(object.rangeArg, map);
				^this.node(
					\atRange,
					(rangeTicks: range.asArray, module: object.module.class.name),
					this.provenanceOf(object.input)
				)
			};

			if (object.isKindOf(VoxChannelSplitter)) {
				^this.node(
					\splitByChannel,
					(labelPrefix: object.labelPrefix, outputChannel: 0),
					this.provenanceOf(object.input)
				)
			};

			if (object.isKindOf(VoxSelector)) {
				^this.node(\select, (label: object.key), this.provenanceOf(object.input))
			};

			if (object.isKindOf(VoxProxy)) {
				^this.node(\routeBranch, (label: object.key), this.provenanceOf(object.input))
			};

			if (object.isKindOf(VoxRouter)) {
			branches = Dictionary.new;
			object.routes.keysValuesDo { |key, route|
				branches[key] = this.provenanceOf(route);
			};
			^this.node(
				\route,
				(allowFallback: object.allowFallback, branches: branches),
				this.provenanceOf(object.input)
			)
		};

		if (object.isKindOf(VoxArrangement)) {
			^this.node(\arrangement, (label: object.label), object.regions.collect { |region|
				this.boundary(\arrangementRegion, (
					id: region.id,
					start: region.start,
					resolvedTick: region.resolvedStart,
					anchor: region.anchor,
					mode: region.mode
				), this.provenanceOf(region.vox))
			})
		};

		input = object.respondsTo(\input).if {
			this.provenanceOf(object.input)
		} {
			nil
		};

		if (object.isKindOf(CTransposer)) {
			^this.node(\transposeChromatic, (semitones: object.semitones), input)
		};
		if (object.isKindOf(DTransposer)) {
			^this.node(\transposeDiatonic, (
				degrees: object.degrees,
				root: object.root,
				scale: this.scaleValue(object.scale)
			), input)
		};
		if (object.isKindOf(ModeMap)) {
			^this.node(\modeMap, (
				sourceRoot: object.source_root,
				sourceScale: this.scaleValue(object.source_scale),
				targetRoot: object.target_root,
				targetScale: this.scaleValue(object.target_scale)
			), input)
		};
		if (object.isKindOf(VoxCanoniser)) {
			params = Dictionary.new;
			object.namesToOffsetsDict.keysValuesDo { |key, value|
				params[key] = this.posValue(value)
			};
			^this.node(\canonise, (voices: object.numVoices, offsets: params), input)
		};
		if (object.isKindOf(RandElong)) {
			^this.node(\elongateRandom, (factorRange: object.factorRange, seed: object.seed), input)
		};
		if (object.isKindOf(Elongator)) {
			^this.node(\elongate, (factor: object.factor), input)
		};
		if (object.isKindOf(VoxGridSplitter)) {
			^this.node(\gridSplit, (unit: object.unit), input)
		};
		if (object.isKindOf(Granulator)) {
			^this.node(\granulate, (
				divisions: object.divisions,
				depth: object.depth,
				seed: object.seed,
				probabilityMapClass: object.prob_map.class.name
			), input)
		};
		if (object.isKindOf(HarmonyMask)) {
			^this.node(\harmonyMask, (
				root: object.root,
				scale: this.scaleValue(object.scale),
				window: object.window,
				seed: object.seed,
				chordBias: object.chordBias,
					voiceLeading: object.voiceLeading,
					randomness: object.randomness,
					stickiness: object.stickiness,
					preserveOnsets: object.preserveOnsets,
					memoryMode: object.memoryMode,
					chordMap: this.chordMapValue(object.chordMap)
				), input)
		};

		^this.node(\unsupportedNode, (
			class: object.class.name,
			label: object.respondsTo(\label).if { object.label } { nil }
		), input)
	}

	*stamp { |snapshot, recipe|
		var copied;

		if (snapshot.isKindOf(Vox).not and: { snapshot.isKindOf(VoxMulti).not }) {
			"VoxProvenance.stamp: expected Vox or VoxMulti.".warn;
			^snapshot
		};

		copied = snapshot.copy;
			copied.metadata[\provenance] = this.copyTree(recipe);
		^copied
	}

	*snapshot { |source, op, params|
		var snapshot, recipe;

		snapshot = source.respondsTo(\out).if { source.out } { source };
		if (snapshot.isKindOf(Vox).not and: { snapshot.isKindOf(VoxMulti).not }) {
			"VoxProvenance.snapshot: source must resolve to Vox or VoxMulti.".warn;
			^nil
		};

		recipe = this.boundary(op, params, this.provenanceOf(source));
		^this.stamp(snapshot, recipe)
	}

	*posValue { |pos|
		if (pos.isKindOf(Pos)) {
			^[pos.bar, pos.beat, pos.division, pos.tick]
		};
		^pos
	}

	*standardPath { |path|
		if (path.isNil) { ^nil };
		^PathName(path.standardizePath).absolutePath.standardizePath
	}

	*midiPath { |midifile|
		if (midifile.respondsTo(\pathName).not) { ^nil };
		^this.standardPath(midifile.pathName)
	}

	*scaleValue { |scale|
		if (scale.isNil) { ^nil };
		^(
			name: scale.name,
			degrees: scale.degrees,
			semitones: scale.semitones
		)
	}

	*chordMapValue { |map|
		if (map.isNil) { ^nil };
		^map.regions.collect { |region|
			(
				start: region.start,
				chord: this.chordValue(region.chord)
			)
		}
	}

	*chordValue { |chord|
		if (chord.isNil) { ^nil };
		^(
			root: chord.root,
			scale: this.scaleValue(chord.scale),
			degrees: chord.degrees,
			weight: chord.weight
		)
	}

	*postObject { |object|
		^this.postRecipe(this.provenanceOf(object))
	}

	*postRecipe { |recipe, depth = 0|
			var indent = "  ".dup(depth).join;

		if (recipe.isNil) {
				"% (no provenance)".format(indent).postln;
			^recipe
		};

			"% %".format(indent, recipe[\op]).postln;
			if (recipe[\params].notNil) {
				"%  %".format(indent, recipe[\params]).postln;
		};
		if (recipe[\input].isArray) {
			recipe[\input].do { |item| this.postRecipe(item, depth + 1) };
		} {
			if (recipe[\input].notNil) {
				this.postRecipe(recipe[\input], depth + 1)
			}
		};
		^recipe
	}
}

VoxArchive {
	classvar omit;

	*initClass {
		omit = Object.new
	}

	*valueAt { |dictionary, key|
		if (dictionary.isKindOf(Dictionary).not) { ^nil };
		^dictionary[key] ? dictionary[key.asString]
	}

	*encodeValue { |value, path = "value"|
		var output, encoded;

		if (value.isNil) { ^(_voxType: "nil") };
		if (value.isNumber or: { value.isKindOf(Boolean) } or: { value.isString }) {
			^value
		};
		if (value.isKindOf(Symbol)) {
			^(_voxType: "symbol", value: value.asString)
		};
		if (value.isKindOf(Pos)) {
			^(_voxType: "pos", value: VoxProvenance.posValue(value))
		};
		if (value.isKindOf(TimeRange)) {
			^(_voxType: "timeRange", value: value.asArray)
		};
		if (value.isKindOf(SequenceableCollection)) {
			^value.collect { |item, index|
				this.encodeValue(item, "%[%]".format(path, index))
			}.reject({ |item| item === omit })
		};
		if (value.isKindOf(Dictionary)) {
			output = Dictionary.new;
			value.keysValuesDo { |key, item|
				if ([\source, \clip_origin].includes(key).not) {
					encoded = this.encodeValue(item, "%.%" .format(path, key));
					if (encoded !== omit) { output[key.asString] = encoded };
				};
			};
			^output
		};

		("VoxArchive: omitting unsupported % at %.".format(value.class, path)).warn;
		^omit
	}

	*decodeValue { |value|
		var type, output;

		if (value.isArray) { ^value.collect(this.decodeValue(_)) };
		if (value.isKindOf(Dictionary).not) { ^value };

		type = this.valueAt(value, \_voxType);
		if (type == "nil") { ^nil };
		if (type == "symbol") { ^this.valueAt(value, \value).asSymbol };
		if (type == "pos") {
			value = this.valueAt(value, \value);
			^Pos.new(value[0], value[1], value[2], value[3])
		};
		if (type == "timeRange") {
			value = this.valueAt(value, \value);
			^TimeRange.fromTicks(value[0], value[1])
		};

		output = Dictionary.new;
		value.keysValuesDo { |key, item|
			output[key.asSymbol] = this.decodeValue(item)
		};
		^output
	}

	*encodeMetreMap { |map|
		^(
			regions: map.regions.collect { |region|
				(
					start: region.start,
					beats: region.metre.beats,
					divisions: region.metre.divisions,
					tpqn: region.metre.tpqn
				)
			}
		)
	}

	*decodeMetreMap { |data|
		var map = MetreMap.new;
		var regions = this.valueAt(data, \regions);

		if (regions.isArray.not or: { regions.isEmpty }) {
			"VoxArchive: malformed metre map.".warn;
			^nil
		};

		regions.do { |region|
			var start = this.valueAt(region, \start);
			var beats = this.valueAt(region, \beats);
			var divisions = this.valueAt(region, \divisions);
			var tpqn = this.valueAt(region, \tpqn);
			if (start.isNumber.not or: { beats.isArray.not } or: {
				divisions.isArray.not
			} or: { tpqn.isNumber.not }) {
				"VoxArchive: malformed metre region.".warn;
				^nil
			};
			map.add(MetreRegion.new(start, Metre.new(beats, divisions, tpqn)));
		};
		^map
	}

	*encodeEvent { |event, path|
		var output = Dictionary.new;

		event.keysValuesDo { |key, value|
			var encoded;
			if (key != \position) {
				encoded = this.encodeValue(value, "%.%" .format(path, key));
				if (encoded !== omit) { output[key.asString] = encoded };
			};
		};
		^output
	}

	*decodeEvent { |data, map|
		var event = Event.new;
		data.keysValuesDo { |key, value|
			event[key.asSymbol] = this.decodeValue(value)
		};
		if (event[\absTime].isNumber.not or: { event[\dur].isNumber.not }) {
			"VoxArchive: event requires absTime and dur.".warn;
			^nil
		};
		event[\position] = TimeConverter.ticksToPos(event[\absTime], map);
		^event
	}

	*encodeSnapshot { |snapshot, path = "snapshot"|
		if (snapshot.isKindOf(Vox)) {
			^(
				type: "Vox",
				label: this.encodeValue(snapshot.label, path ++ ".label"),
				metremap: this.encodeMetreMap(snapshot.metremap),
				metadata: this.encodeValue(snapshot.metadata, path ++ ".metadata"),
				events: snapshot.events.collect { |event, index|
					this.encodeEvent(event, "%.events[%]".format(path, index))
				}
			)
		};
		if (snapshot.isKindOf(VoxMulti)) {
			^(
				type: "VoxMulti",
				label: this.encodeValue(snapshot.label, path ++ ".label"),
				metremap: this.encodeMetreMap(snapshot.metremap),
				metadata: this.encodeValue(snapshot.metadata, path ++ ".metadata"),
				voxes: snapshot.asArray.collect { |vox, index|
					this.encodeSnapshot(vox, "%.voxes[%]".format(path, index))
				}
			)
		};
		"VoxArchive: unsupported snapshot type.".warn;
		^nil
	}

	*decodeSnapshot { |data|
		var type = this.valueAt(data, \type);
		var map = this.decodeMetreMap(this.valueAt(data, \metremap));
		var metadata, label, events, voxes;

		if (map.isNil) { ^nil };
			label = this.decodeValue(this.valueAt(data, \label));
			metadata = this.decodeValue(this.valueAt(data, \metadata)) ? Dictionary.new;
			if (metadata.isKindOf(Dictionary).not) {
				"VoxArchive: snapshot metadata must be a Dictionary.".warn;
				^nil
			};

		if (type == "Vox") {
			events = this.valueAt(data, \events);
			if (events.isArray.not) {
				"VoxArchive: Vox events must be an Array.".warn;
				^nil
			};
			events = events.collect { |event| this.decodeEvent(event, map) };
			if (events.includes(nil)) { ^nil };
			^Vox.new(events, map, label, metadata)
		};

		if (type == "VoxMulti") {
			voxes = this.valueAt(data, \voxes);
			if (voxes.isArray.not) {
				"VoxArchive: VoxMulti voices must be an Array.".warn;
				^nil
			};
			voxes = voxes.collect { |vox| this.decodeSnapshot(vox) };
			if (voxes.includes(nil)) { ^nil };
			if (voxes.collect(_.label).asSet.size != voxes.size) {
				"VoxArchive: duplicate VoxMulti voice labels.".warn;
				^nil
			};
			^VoxMulti.new(voxes, map, label, metadata)
		};

		"VoxArchive: invalid snapshot type %.".format(type).warn;
		^nil
	}

	*writeBank { |bank, path|
		var document = (
			format: "voxbox-bank",
			version: 1,
			label: this.encodeValue(bank.label, "bank.label"),
			slots: bank.keys.collect { |key, index|
				(
					key: this.encodeValue(key, "bank.slots[%].key".format(index)),
					snapshot: this.encodeSnapshot(bank.entries[key], "bank.slots[%]".format(index))
				)
			}
		);

		File.use(path.standardizePath, "w", { |file|
			file.write(JSONlib.convertToJSON(document, postWarnings: false))
		});
		^bank
	}

	*readBank { |path|
		var data, slots, bank, seen, failed = false;

		if (File.exists(path.standardizePath).not) {
			"VoxArchive: file does not exist: %.".format(path).warn;
			^nil
		};

			try {
				data = JSONlib.parseFile(path.standardizePath, useEvent: false, postWarnings: false);
			} { |error|
				"VoxArchive: could not parse %.".format(path).warn;
				data = nil
			};
			if (data.isNil) { ^nil };

		if (this.valueAt(data, \format) != "voxbox-bank") {
			"VoxArchive: unsupported archive format.".warn;
			^nil
		};
		if (this.valueAt(data, \version) != 1) {
			"VoxArchive: unsupported schema version.".warn;
			^nil
		};

		slots = this.valueAt(data, \slots);
		if (slots.isArray.not) {
			"VoxArchive: slots must be an Array.".warn;
			^nil
		};

		bank = VoxBank.new(this.decodeValue(this.valueAt(data, \label)));
		seen = Set.new;
		slots.do { |slot|
			var key = this.decodeValue(this.valueAt(slot, \key));
			var snapshot;
			if (key.isKindOf(Symbol).not or: { seen.includes(key) }) {
				"VoxArchive: invalid or duplicate bank slot label.".warn;
				failed = true;
			} {
				snapshot = this.decodeSnapshot(this.valueAt(slot, \snapshot));
				if (snapshot.isNil) {
					failed = true
				} {
					seen.add(key);
					bank.storeFrozen(key, snapshot);
				}
			}
		};

		if (failed) { ^nil };
		^bank
	}
}
