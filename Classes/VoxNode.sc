VoxNode {
	var <input, <>label, <>metadata, <revision = 0;

	input_ { |node|
		input = node;
		this.touch;
		^this
	}

	touch {
		revision = revision + 1;
		^this
	}

	effectiveRevision {
		^[
			revision,
			input.notNil.if {
				input.respondsTo(\effectiveRevision).if {
					input.effectiveRevision
				} {
					input.hash
				}
			} {
				nil
			}
		].hash
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

	provenance {
		^VoxProvenance.provenanceOf(this)
	}

	postProvenance {
		^VoxProvenance.postObject(this)
	}

	>>> { |target|

		target.isKindOf(VoxNode).if {
			if (target.isKindOf(Box) or: { target.isKindOf(BoxMulti) }) {
				target.forceload(VoxProvenance.snapshot(this, \gather, (target: target.class.name)));
				^target
			};

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
			target.load(VoxProvenance.snapshot(this, \gather, (target: target.class.name)));
			^target
		};

		if (target.respondsTo(\storeSnapshot)) {
			^target.storeSnapshot(this)
		};

		"❌ Cannot patch: >>= requires a Symbol, Box, BoxMulti or snapshot slot. Got % >>= %."
		.format(this.class, target.class).warn;
		^target
	}

	>>==> { |target|
		target.forceload(VoxProvenance.snapshot(this, \gather, (target: target.class.name)));
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
		^VoxSelector.new(this, key)
	}

	>>/ { |range|
		^VoxClipper.new(this, range)
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
