//////////////////////////
// MetreTREE
//////////////////////////
MetreTree {
    var >tree, >tpqn;

    *new {
		arg tree, tpqn=960;

		^super.new.init(tree, tpqn);
    }

	init {
		arg tree, tpqn;

		if (tree.isKindOf(Array).not) {
			("MetreTree got %: tree is not SequenceableCollection").format(tree).throw;
		};

		this.tree = tree.collect({
			arg beat;

			if (beat[0].isKindOf(Number).not) {
				("not correct format").throw
			};

			if (beat[1].isKindOf(Array)) {
				beat
			} {
				if (beat[1].isKindOf(Number)) {
					[beat[0], Array.fill(beat[1], 1)]
				}
			}
		});

		this.tpqn = tpqn;
	}

	*fromMetre {
		arg metre;

		^super.new.initFromMetre(metre)
	}

	initFromMetre {
		arg metre;
		var tree = [];

		metre.beats.do({
			arg beat, i;
			tree = tree.add([beat, metre.divisions[i]])
		});

		^MetreTree.new(tree, metre.tpqn)
	}

	//////////////
	// GETTERS
	//////////////
	tree { ^tree.copy }
	tpqn { ^tpqn }

	asString {
		^this.tree.asString;
	}
}