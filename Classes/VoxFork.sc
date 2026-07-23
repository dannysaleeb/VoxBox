VoxForkBranch : VoxNode {
	var <key, <chain;

	*new { |key, chain|
		^super.new.init(key, chain)
	}

	init { |keyArg, chainArg|
		key = keyArg;
		chain = chainArg;
		label = keyArg;
		metadata = Dictionary.new;
		^this
	}

	input_ { |source|
		input = source;
		if (chain.notNil) {
			source >>> chain.headNode;
		};
		this.touch;
		^this
	}

	node { |handleArg|
		^chain.node(handleArg)
	}

	at { |handleArg|
		^this.node(handleArg)
	}

	handles {
		^chain.handles
	}

	mask { |ranges|
		if (chain.isKindOf(VoxMask)) {
			chain.mask(ranges)
		} {
			chain = VoxMask.new(chain, ranges)
		};
		this.touch;
		^this
	}

	playSections { |ranges|
		^this.mask(ranges)
	}

	clearMask {
		if (chain.isKindOf(VoxMask)) {
			chain = chain.input;
			this.touch;
		};
		^this
	}

	effectiveRevision {
		^[
			super.effectiveRevision,
			chain.notNil.if { chain.effectiveRevision } { nil }
		].hash
	}

	out {
		^chain.out
	}
}

VoxFork : VoxNode {
	var <branches;

	*new { |source, spec|
		^super.new.init(source, spec)
	}

	init { |source, spec|
		branches = Dictionary.new;
		input = source;
		label = \fork;
		metadata = Dictionary.new;
		this.addAll(spec);
		^this
	}

	specDict { |spec|
		^spec.isKindOf(Array).if {
			spec.asDict
		} {
			spec
		}
	}

	addAll { |spec|
		this.specDict(spec).keysValuesDo { |key, branchSpec|
			this.add(key, branchSpec)
		};
		^this
	}

	add { |key, branchSpec|
		var branch;

		branch = branchSpec.isKindOf(VoxForkBranch).if {
			branchSpec
		} {
			VoxForkBranch.new(key, branchSpec)
		};

		branch.input = input;
		branches[key] = branch;
		this.touch;
		^branch
	}

	branch { |key|
		^branches[key]
	}

	at { |key|
		^this.branch(key)
	}

	keys {
		^branches.keys
	}

	fragmentSelector { |selector|
		if (selector.isNil) { ^Fragments(\eighth) };
		if (selector.isKindOf(Symbol)) { ^Fragments(selector) };
		if (selector.isNumber) { ^Fragments(selector) };
		^selector
	}

	applyRoundRobinMasks { |branchKeys, selector|
		var sourceOut = input.out;
		var metremap = sourceOut.metremap;
		var fragment = this.fragmentSelector(selector);
		var span = fragment.sourceSpan(sourceOut);
		var cellTicks = fragment.unitTicks(metremap);
		var cellCount = ((span[1] - span[0]) / cellTicks).ceil.asInteger.max(0);

		branchKeys.do { |key, branchIndex|
			var indices = [];
			cellCount.do { |cellIndex|
				if ((cellIndex % branchKeys.size) == branchIndex) {
					indices = indices.add(cellIndex);
				}
			};
			branches[key].mask(fragment.select(indices));
		};

		this.touch;
		^this
	}

	hocket { |a, b, c, d, e, f, by|
		var branchKeys = [a, b, c, d, e, f].reject(_.isNil);
		branchKeys.isEmpty.if {
			branchKeys = this.keys.asArray.sort { |left, right|
				left.asString < right.asString
			}
		};
		^this.applyRoundRobinMasks(branchKeys, by ? Fragments(\eighth))
	}

	distribute { |mode = \roundRobin, every = \eighth|
		if (mode != \roundRobin) {
			"VoxFork.distribute: only \\roundRobin is implemented; using roundRobin.".warn;
		};
		^this.applyRoundRobinMasks(
			this.keys.asArray.sort { |left, right| left.asString < right.asString },
			this.fragmentSelector(every)
		)
	}

	effectiveRevision {
		^[
			super.effectiveRevision,
			branches.values.collect(_.effectiveRevision).sort
		].hash
	}

	out {
		var rendered = List.new;
		var sourceOut = input.out;

		branches.values.do { |branch|
			var branchOut = branch.out;
			if (branchOut.isKindOf(Vox)) {
				rendered.add(branchOut);
			};
			if (branchOut.isKindOf(VoxMulti)) {
				rendered.addAll(branchOut.asArray);
			};
		};

		^VoxProvenance.stampTree(
			VoxMulti.new(rendered.asArray, sourceOut.metremap, label, metadata, this),
			this.provenance
		)
	}
}
