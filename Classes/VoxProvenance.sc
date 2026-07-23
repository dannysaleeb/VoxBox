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
		var input, map, range, branches, spec;

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

		if (object.isKindOf(VoxOut)) {
			^this.provenanceOf(object.input)
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

		if (object.isKindOf(VoxFork)) {
			branches = Dictionary.new;
			object.branches.keysValuesDo { |key, branch|
				branches[key] = this.provenanceOf(branch.chain);
			};
			^this.node(
				\fork,
				(branches: branches),
				this.provenanceOf(object.input)
			)
		};

		if (object.isKindOf(VoxMask)) {
			^this.node(
				\mask,
				(ranges: object.rangeArg, scope: object.scopeArg),
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

		if (object.respondsTo(\provenanceSpec)) {
			spec = object.provenanceSpec;
			if (spec.isNil) { ^input };
			if (spec[\op] != \unsupportedNode) {
				^this.node(spec[\op], spec[\params], input)
			}
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

	*recipeFromSpec { |spec, input|
		if (spec.isNil) { ^this.copyTree(input) };
		^this.node(
			spec[\op] ? \unsupportedNode,
			spec[\params],
			input
		)
	}

	*stampModuleOutput { |snapshot, module, inputSnapshot|
		var spec, recipe, metadata, children;

		if (snapshot.isKindOf(Vox).not and: { snapshot.isKindOf(VoxMulti).not }) {
			^snapshot
		};

		spec = module.provenanceSpec;
		if (spec.isNil) { ^snapshot.copy };
		recipe = this.recipeFromSpec(spec, this.provenanceOf(inputSnapshot));

		if (snapshot.isKindOf(Vox)) {
			^this.stamp(snapshot, recipe)
		};

		children = snapshot.asArray.collect { |vox|
			var childInput, childRecipe;

			childInput = inputSnapshot.isKindOf(VoxMulti).if {
				inputSnapshot.voxes[vox.label]
			} {
				inputSnapshot
			};
			childRecipe = this.recipeFromSpec(
				spec,
				this.provenanceOf(childInput ? inputSnapshot)
			);
			this.stamp(vox, childRecipe)
		};

		metadata = snapshot.metadata.deepCopy;
		metadata[\provenance] = recipe;
		^VoxMulti.new(
			children,
			snapshot.metremap,
			snapshot.label,
			metadata,
			snapshot.source
		)
	}

	*stampTree { |snapshot, recipe|
		var metadata, children;

		if (snapshot.isKindOf(Vox)) {
			^this.stamp(snapshot, recipe)
		};
		if (snapshot.isKindOf(VoxMulti).not) { ^snapshot };

		children = snapshot.asArray.collect { |vox|
			this.stamp(vox, recipe)
		};
		metadata = snapshot.metadata.deepCopy;
		metadata[\provenance] = this.copyTree(recipe);
		^VoxMulti.new(
			children,
			snapshot.metremap,
			snapshot.label,
			metadata,
			snapshot.source
		)
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

	*probabilityMapValue { |map|
		var value;

		if (map.isNil) { ^nil };
		value = (class: map.class.name);
		if (map.respondsTo(\orig_probs)) {
			value[\probabilities] = map.orig_probs.deepCopy
		};
		if (map.respondsTo(\change_factors)) {
			value[\changeFactors] = map.change_factors.deepCopy
		};
		if (value[\probabilities].isNil and: { map.respondsTo(\probs) }) {
			value[\probabilities] = map.probs.deepCopy
		};
		^value
	}

	*postObject { |object|
		^this.postRecipe(this.provenanceOf(object))
	}

	*formatValue { |value|
		var keys;

		if (value.isNil) { ^"nil" };
		if (value.isKindOf(Symbol)) { ^value.asCompileString };
		if (value.isString) { ^value.asCompileString };
		if (value.isKindOf(Dictionary)) {
			keys = value.keys.asArray.sort { |left, right|
				left.asString < right.asString
			};
			^"(%)".format(keys.collect { |key|
				"%: %".format(key, this.formatValue(value[key]))
			}.join(", "))
		};
		if (value.isKindOf(SequenceableCollection)) {
			^"[%]".format(value.collect { |item|
				this.formatValue(item)
			}.join(", "))
		};
		^value.asCompileString
	}

	*formatParams { |params, excluded|
		var keys;

		if (params.isKindOf(Dictionary).not) { ^"" };
		keys = params.keys.asArray.reject { |key|
			(excluded ? []).includes(key)
		}.sort { |left, right|
			left.asString < right.asString
		};
		if (keys.isEmpty) { ^"" };
		^keys.collect { |key|
			"%: %".format(key, this.formatValue(params[key]))
		}.join(", ")
	}

	*sameRecipe { |left, right|
		if (left.isNil or: { right.isNil }) { ^left.isNil and: { right.isNil } };
		^this.formatValue(left) == this.formatValue(right)
	}

	*withoutSharedInput { |recipe, sharedInput|
		var input, result, copied;

		if (recipe.isKindOf(Dictionary).not) {
			^(found: false, recipe: recipe)
		};
		if (this.sameRecipe(recipe, sharedInput)) {
			^(found: true, recipe: nil)
		};

		input = recipe[\input];
		if (input.isKindOf(Dictionary)) {
			result = this.withoutSharedInput(input, sharedInput);
			if (result[\found]) {
				copied = this.copyTree(recipe);
				if (result[\recipe].isNil) {
					copied.removeAt(\input)
				} {
					copied[\input] = result[\recipe]
				};
				^(found: true, recipe: copied)
			}
		};
		^(found: false, recipe: recipe)
	}

	*summaryLines { |recipe, depth = 0|
		var lines = List.new;
		var indent = "  ".dup(depth).join;
		var input, params, branches, detail;

		if (recipe.isNil) {
			lines.add("%(no provenance)".format(indent));
			^lines
		};

		input = recipe[\input];
		if (input.isKindOf(Dictionary) and: { input[\op].notNil }) {
			lines.addAll(this.summaryLines(input, depth))
		};
		if (input.isArray) {
			input.do { |item, index|
				lines.add("%input %:".format(indent, index));
				lines.addAll(this.summaryLines(item, depth + 1))
			}
		};

		params = recipe[\params];
		detail = this.formatParams(params, [\branches]);
		if (recipe[\op] != \routeBranch) {
			lines.add(
				detail.isEmpty.if {
					"%→ %".format(indent, recipe[\op])
				} {
					"%→ %(%)".format(indent, recipe[\op], detail)
				}
			)
		};

		branches = params.isKindOf(Dictionary).if { params[\branches] } { nil };
		if (branches.isKindOf(Dictionary)) {
			branches.keys.asArray.sort { |left, right|
				left.asString < right.asString
			}.do { |key|
				var branchResult, branchLines;

				branchResult = this.withoutSharedInput(branches[key], input);
				branchLines = (
					branchResult[\found] and: { branchResult[\recipe].isNil }
				).if {
					List.new
				} {
					this.summaryLines(
						branchResult[\found].if {
							branchResult[\recipe]
						} {
							branches[key]
						},
						depth + 2
					)
				};
				lines.add("%  %:".format(indent, key));
				if (branchLines.isEmpty) {
					lines.add("%    → unchanged".format(indent))
				} {
					lines.addAll(branchLines)
				}
			}
		};
		^lines
	}

	*summaryOf { |object|
		^this.summaryLines(this.provenanceOf(object)).join("\n")
	}

	*postRecipe { |recipe, depth = 0|
			var indent = "  ".dup(depth).join;
			var params, keys, branches;

		if (recipe.isNil) {
			"% (no provenance)".format(indent).postln;
			^recipe
		};

			"% %".format(indent, recipe[\op]).postln;
			params = recipe[\params];
			if (params.isKindOf(Dictionary)) {
				keys = params.keys.asArray.reject { |key|
					key == \branches
				}.sort { |left, right|
					left.asString < right.asString
				};
				keys.do { |key|
					"%  %: %".format(
						indent,
						key,
						this.formatValue(params[key])
					).postln
				}
			};
		branches = params.isKindOf(Dictionary).if { params[\branches] } { nil };
		if (branches.isKindOf(Dictionary)) {
			"%  branches:".format(indent).postln;
			branches.keys.asArray.sort { |left, right|
				left.asString < right.asString
			}.do { |key|
				"%    %:".format(indent, key).postln;
				this.postRecipe(branches[key], depth + 3)
			}
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

	*writeDocument { |document, path|
		var target, temporary, json, moved = false;

		if (path.isString.not or: { path.isEmpty }) {
			"VoxArchive: path must be a non-empty String.".warn;
			^false
		};

		target = path.standardizePath;
		temporary = target ++ ".tmp-" ++
			((Main.elapsedTime * 1000000).asInteger.abs.asString);

		try {
			json = JSONlib.convertToJSON(document, postWarnings: false);
			File.use(temporary, "w", { |file| file.write(json) });
			JSONlib.parseFile(temporary, useEvent: false, postWarnings: false);
		} { |error|
			"VoxArchive: could not serialize %.".format(path).warn;
			if (File.exists(temporary)) { File.delete(temporary) };
			^false
		};

		if (File.exists("/bin/mv")) {
			["/bin/mv", "-f", temporary, target].unixCmdGetStdOut;
			moved = File.exists(temporary).not and: { File.exists(target) };
		} {
			if (File.exists(target).not) {
				try {
					File.copy(temporary, target);
					moved = File.exists(target);
				} { |error|
					moved = false
				}
			}
		};

		if (File.exists(temporary)) { File.delete(temporary) };
		if (moved.not) {
			"VoxArchive: could not safely replace %; any existing file was preserved."
				.format(path).warn;
		};
		^moved
	}

	*writeVox { |source, path|
		var snapshot, document;

		snapshot = source.respondsTo(\out).if { source.out } { source };
		if (snapshot.isKindOf(Vox).not and: { snapshot.isKindOf(VoxMulti).not }) {
			"VoxArchive.writeVox: source must resolve to Vox or VoxMulti.".warn;
			^nil
		};

		if (source.isKindOf(Vox).not and: { source.isKindOf(VoxMulti).not }) {
			snapshot = VoxProvenance.stampTree(
				snapshot,
				VoxProvenance.provenanceOf(source)
			)
		};

		document = (
			format: "voxbox-vox",
			version: 1,
			snapshot: this.encodeSnapshot(snapshot)
		);

		if (this.writeDocument(document, path).not) { ^nil };
		^snapshot.copy
	}

	*readVox { |path|
		var data, snapshot;

		if (path.isString.not or: { File.exists(path.standardizePath).not }) {
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

		if (this.valueAt(data, \format) != "voxbox-vox") {
			"VoxArchive: unsupported archive format.".warn;
			^nil
		};
		if (this.valueAt(data, \version) != 1) {
			"VoxArchive: unsupported schema version.".warn;
			^nil
		};

		snapshot = this.decodeSnapshot(this.valueAt(data, \snapshot));
		if (snapshot.isNil) { ^nil };
		^snapshot
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

		if (this.writeDocument(document, path).not) { ^nil };
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
