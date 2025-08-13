VoxNode {
	var <input, <>label, <>metadata;

	input_ { |node|
		input = node;
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

		if (target.isKindOf(Box) or: { target.isKindOf(BoxMulti) }) {
			target.load(this);
			^target
		};

		"❌ Cannot patch: >>= can only patch to Symbol, Box or BoxMulti. Got % >>= %."
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
		vox = this.out,
		named = Dictionary.new,
		specDict = spec.isKindOf(Array).if {
			spec.asDict;
		} {
			spec;
		};

		// this assumes array will be [\name, []]

		if (vox.isKindOf(VoxMulti).not) {
			"❌ Cannot split %: expected VoxMulti.".format(vox.class).warn;
			^this
		};

		specDict.keysValuesDo { |key, voxID|
			// check if array of voxes
			var selected = voxID.isArray.not.if
			{
				vox.at(voxID);
			} {
				voxID.collect { |id| vox.at(id) };
			};

			named[key] = (selected.isArray.not).if { selected } { VoxMulti.new(selected) };
		};

		^VoxPatcher.new(named);
	}

	// THIS IS SUPER TRICKSOME
	// Now needs to output VoxRouter I think ...
	/*>>@ { |routes|
		var vox = this.out;
		var processed = [];

		routes.do { |route|
			if (route.isKindOf(VoxRoute)) {
				var source = vox.at(route.sourceKey);

				if (source.isNil) {
					"❌ Could not find source for key % in >>@".format(route.sourceKey).warn;
				} {
					// COME BACK HERE ... work out
					source.source <<< route.chain;
					processed = processed.add(route.chain);
				};
			} {
				"❌ Expected VoxRoute in >>@ list, got %".format(route.class).warn;
			};
		};

		^VoxMulti.fromPlugMulti(VoxMulti.new(processed.collect(_.out)));
	}*/

	>>@ { |routes|
		var router = VoxRouter.new(this);
		routes.do(router.add(_));
		^router
	}

	>>* { |key|
		var vox = this.out;

		if (vox.isKindOf(VoxMulti)) {
			^vox.at(key).source;
		};

		"❌ Expected VoxMulti for selector >>*, got %"
		.format(this.class).warn;
		^this
	}

	>>/ { |range|
		var vox = this.out;

		// what should I clip??
		// clip from vox? clip from vox's source?

		if (vox.isKindOf(Vox)) {
			var start, end;
			start = TimeConverter.posToTicks(range[0], this.metremap);
			end = TimeConverter.posToTicks(range[1], this.metremap);
			^vox.source.clipRange([start, end]);
		};

		if (vox.isKindOf(VoxMulti)) {
			^VoxMulti.fromPlugMulti(vox).clip(range);
		};

		"❌ Cannot clip from %, expected Vox or VoxMulti."
		.format(vox.class).warn;

		^this
	}

	// merges VoxNode out (Vox or VoxMulti) into a Vox or VoxMulti
	>>+ { |spec|
		this.add(spec)
		^this;
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