VoxNode {
	var <input, <>label, <>metadata;

	input_ {
		"😬 input_ not implemented for this node (%).".format(this.class).warn;
		^this
	}

	headNode {
		var node = this;

		while {
			node.input.notNil and: { node.input.isKindOf(VoxNode) }
		} {
			node = node.input;
		};

		^node;
	}

	out {
		if (input.notNil) {
			^input.out
		} {
			^nil
		}
	}

	>>> { |target|

		target.isKindOf(VoxNode).if {
			target.input = this;
		} {
			"❌ Cannot patch: Only VoxNodes may be chained using >>>. Got % >>> %."
			.format(this.class, target.class).warn;
		};

		^target;
	}

	>>= { |target|

		if (target.isKindOf(Symbol)) {
			target.envirPut(this);
			^this
		};

		if (target.isKindOf(Vox) or: { target.isKindOf(VoxMulti) }) {
			target.load(this);
			^target
		};

		"❌ Cannot patch: >>= can only patch to Symbol, Vox or VoxMulti. Got % >>= %."
		.format(this.class, target.class).warn;
		^target
	}

	>>==> { |target|
		target.forceload(this);
		^target
	}

	<<< { |target|

		if (target.isKindOf(VoxNode)) {
			target.headNode.input = this;
		} {
			"❌ Cannot patch: Only VoxNodes may be chained using <<<. Got % <<< %."
			.format(this.class, target.class).warn;
		}

		^target
	}

	>>< { |spec|
		this.split(spec);
	}

	split { |spec|
		var
		multiPlug = this.out,
		named = Dictionary.new,
		specDict = spec.isKindOf(Array).if {
			spec.asDict;
		} {
			spec;
		};

		// this assumes array will be [\name, []]

		if (multiPlug.isKindOf(VoxPlugMulti).not) {
			"❌ Cannot split %: expected VoxPlugMulti.".format(multiPlug.class).warn;
			^this
		};

		specDict.keysValuesDo { |key, voxID|
			// check if array of voxes
			var selected = voxID.isArray.not.if
			{
				multiPlug.at(voxID);
			} {
				voxID.collect { |id| multiPlug.at(id) };
			};

			named[key] = (selected.isArray.not).if { selected } { VoxMulti.new(selected) };
		};

		^VoxPatcher.new(named);
	}

	// THIS IS SUPER TRICKSOME
	>>@ { |routes|
		var basePlug = this.out;
		var processed = [];

		routes.do { |route|
			if (route.isKindOf(VoxRoute)) {
				var source = basePlug.at(route.sourceKey);

				if (source.isNil) {
					"❌ Could not find source for key % in >>@".format(route.sourceKey).warn;
				} {
					Vox.fromPlug(source) <<< route.chain;
					processed = processed.add(route.chain);
				};
			} {
				"❌ Expected VoxRoute in >>@ list, got %".format(route.class).warn;
			};
		};

		^VoxMulti.fromPlugMulti(VoxPlugMulti.new(processed.collect(_.out)));
	}

	>>* { |key|
		var plug = this.out;

		if (plug.isKindOf(VoxPlugMulti)) {
			^plug.at(key).source;
		};

		"❌ Expected VoxPlugMulti for selector >>*, got %"
		.format(this.class).warn;
		^this
	}

	>>/ { |range|
		var plug = this.out;

		if (plug.isKindOf(VoxPlug)) {
			plug.source.highlight(range[0], range[1]);
			^plug.source.clip;
		};

		if (plug.isKindOf(VoxPlugMulti)) {
			^VoxMulti.fromPlugMulti(plug).clip(range);
		};

		"❌ Cannot clip from %, expected VoxPlug or VoxPlugMulti."
		.format(plug.class).warn;

		^this
	}

	// merges VoxNode out (VoxPlug or VoxPlugMulti) into a Vox or VoxMulti
	>>+ { |target|
		^target.merge(this);
	}

	>>+= { |target|
		// implement: not sure what it should do
		// I think this is merge and assign to symbol...?
		// mutates target?
	}

	>>& { |vox|
		// IMPLEMENT METHOD ON VOX (CONSIDER VOXMULTI SITUATION)
		// this will be able to load empty Vox with events from Pbind e.g.
		vox.loadFromEvents(this);
		^vox
	}
}