VoxSession {
	classvar currentSession;
	var <chains, <players, <outputSpecs, <>midiout, <>clock;

	*current {
		currentSession = currentSession ?? { this.new };
		^currentSession
	}

	*reset {
		currentSession = this.new;
		^currentSession
	}

	*new {
		^super.new.init
	}

	init {
		chains = Dictionary.new;
		players = Dictionary.new;
		outputSpecs = Dictionary.new;
		^this
	}

	at { |name|
		^chains[name]
	}

	out { |name|
		^players[name]
	}

	indexHandledNodes { |chain|
		var result = Dictionary.new;

		if (chain.isNil or: { chain.respondsTo(\nodes).not }) { ^result };

		chain.nodes.do { |node|
			if (node.handle.notNil) {
				result[node.handle] = node;
			}
		};

		^result
	}

	copyCompatibleParams { |source, target|
		var excluded = Set.newFrom([
			\input, \label, \metadata, \handle, \revision,
			\cachedRevision, \cachedOutput
		]);

		source.class.instVarNames.do { |name|
			var setter, value;
			if (excluded.includes(name).not) {
				setter = (name.asString ++ "_").asSymbol;
				if (source.respondsTo(name) and: { target.respondsTo(setter) }) {
					value = source.perform(name);
					target.perform(setter, value.deepCopy);
				}
			}
		};

		^target
	}

	replaceNode { |root, newNode, stableNode|
		stableNode.input = newNode.input;

		if (root === newNode) {
			^stableNode
		};

		root.nodes.do { |node|
			if (node.input === newNode) {
				node.input = stableNode;
			}
		};

		^root
	}

	reconcile { |name, chain|
		var previous = chains[name];
		var previousByHandle, root;

		if (previous.isNil or: { chain.respondsTo(\nodes).not }) {
			^chain
		};

		previousByHandle = this.indexHandledNodes(previous);
		root = chain;

		chain.nodes.do { |newNode|
			var oldNode;
			if (newNode.handle.notNil) {
				oldNode = previousByHandle[newNode.handle];
				if (oldNode.notNil and: { oldNode.class == newNode.class }) {
					this.copyCompatibleParams(newNode, oldNode);
					root = this.replaceNode(root, newNode, oldNode);
				}
			}
		};

		^root
	}

	bind { |name, chain|
		var bound;

		bound = this.reconcile(name, chain);
		chains[name] = bound;
		this.activateOutputsIn(bound);
		^bound
	}

	registerPlayer { |name, player|
		players[name] = player;
		^player
	}

	tapNodesIn { |node, seen|
		var taps = List.new;

		seen = seen ?? { IdentitySet.new };

		if (node.isNil or: { seen.includes(node) }) { ^taps.asArray };
		seen.add(node);

		if (node.isKindOf(VoxOut)) {
			taps.add(node);
		};

		if (node.isKindOf(VoxFork)) {
			node.branches.values.do { |branch|
				taps.addAll(this.tapNodesIn(branch, seen));
			};
			^taps.asArray
		};

		if (node.isKindOf(VoxForkBranch)) {
			taps.addAll(this.tapNodesIn(node.chain, seen));
			^taps.asArray
		};

		if (node.respondsTo(\nodes)) {
			node.nodes.do { |candidate|
				if (seen.includes(candidate).not) {
					taps.addAll(this.tapNodesIn(candidate, seen));
				}
			}
		};

		^taps.asArray
	}

	activateOutputsIn { |chain|
		this.tapNodesIn(chain).do { |tap|
			tap.activateInSession(this);
		};
		^this
	}

	targetNames { |target|
		if (target == \all) {
			^outputSpecs.keys.asArray
		};
		^[target]
	}

	registerOutput { |name, source, mode, midioutArg, clockArg, shouldLoop, audible, offset, channelMap|
		var player = players[name];
		var spec = outputSpecs[name];
		var needsNewPlayer = player.isNil;
		var resolvedMIDIOut = midioutArg ? midiout;
		var resolvedClock = clockArg ? clock ? TempoClock.default;

		if (spec.notNil) {
			if (spec[\mode] != mode or: {
				spec[\midiout] !== resolvedMIDIOut or: {
					spec[\clock] !== resolvedClock
				}
			}) {
				player.stop;
				needsNewPlayer = true;
			}
		};

		if (needsNewPlayer) {
			player = VoxPlayer.new(source, resolvedClock);
			players[name] = player;
			spec = (transport: \stopped);
		} {
			player.source = source;
			spec = spec.copy;
		};

		spec[\mode] = mode;
		spec[\midiout] = resolvedMIDIOut;
		spec[\clock] = resolvedClock;
		spec[\shouldLoop] = shouldLoop;
		spec[\offset] = offset;
		spec[\audible] = audible ? true;
		spec[\channelMap] = channelMap.notNil.if { channelMap.copy } { nil };
		outputSpecs[name] = spec;

		player.channelMap = channelMap;
		this.startOutput(name);
		this.setOutputAudible(name, audible ? true);

		^player
	}

	startOutput { |name|
		var player = players[name];
		var spec = outputSpecs[name];

		if (name == \all) {
			^this.targetNames(name).collect { |target| this.startOutput(target) }
		};

		if (player.isNil or: { spec.isNil }) { ^nil };
		if (spec[\transport] == \running) { ^player };

		if (spec[\offset].notNil) {
			"VoxOut: offset is stored but delayed start is not implemented yet.".warn;
		};

		if (spec[\mode] == \midi and: { spec[\midiout].isNil }) {
			"VoxOut: MIDI output % has no midiout; registered but not started.".format(name).warn;
			^player
		};

		if (spec[\mode] == \midi) {
			if (spec[\shouldLoop]) {
				player.loopMIDI(spec[\midiout])
			} {
				player.playMIDI(spec[\midiout])
			}
		} {
			if (spec[\shouldLoop]) {
				player.loop
			} {
				player.play
			}
		};

		spec[\transport] = \running;
		^player
	}

	stopOutput { |name|
		var player = players[name];
		var spec = outputSpecs[name];

		if (name == \all) {
			^this.targetNames(name).collect { |target| this.stopOutput(target) }
		};

		if (player.notNil) {
			player.stop;
		};
		if (spec.notNil) {
			spec[\transport] = \stopped;
		};
		^player
	}

	pauseOutput { |name|
		var player = players[name];
		var spec = outputSpecs[name];

		if (name == \all) {
			^this.targetNames(name).collect { |target| this.pauseOutput(target) }
		};

		if (player.notNil) {
			player.pause;
		};
		if (spec.notNil) {
			spec[\transport] = \paused;
		};
		^player
	}

	resumeOutput { |name|
		var player = players[name];
		var spec = outputSpecs[name];

		if (name == \all) {
			^this.targetNames(name).collect { |target| this.resumeOutput(target) }
		};

		if (player.notNil) {
			player.resume;
		};
		if (spec.notNil) {
			spec[\transport] = \running;
		};
		^player
	}

	restartOutput { |name|
		var player = players[name];
		var spec = outputSpecs[name];

		if (name == \all) {
			^this.targetNames(name).collect { |target| this.restartOutput(target) }
		};

		if (player.notNil) {
			player.restart;
		};
		if (spec.notNil) {
			spec[\transport] = \running;
		};
		^player
	}

	setOutputAudible { |name, audible = true|
		var player = players[name];
		var spec = outputSpecs[name];

		if (name == \all) {
			^this.targetNames(name).collect { |target|
				this.setOutputAudible(target, audible)
			}
		};

		if (spec.notNil) {
			spec[\audible] = audible;
		};
		if (player.notNil) {
			player.audible = audible;
		};
		^player
	}

	stopAllOutputs {
		this.stopOutput(\all);
		^this
	}
}
