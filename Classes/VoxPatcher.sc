VoxPatcher {
    var <branches;

    *new { |branchesDict|
        ^super.new.init(branchesDict)
    }

    init { |branchesDict|
        branches = branchesDict;
        ^this
    }

    at { |key| ^branches[key] }
	keys { ^branches.keys }
    asDict { ^branches.copy }
}

+ VoxPlugMulti {

	split { |spec|

		"😬 .split called on frozen VoxPlugMulti (%); live update will not propagate."
		.format(this.label ? this.class).warn;

		^VoxMulti.fromPlugMulti(this).split(spec);
	}

}

+ VoxMulti {

	// this fine, although I want to access voxes in voxmulti with labels ...
	split { |spec|
		var specDict, named;

		specDict = spec.isKindOf(Array).if {
			spec.asDict;
		} {
			spec;
		};

		named = Dictionary.new;
		// from cgpt
		specDict.keysValuesDo { |key, indices|
			// check if array of voxes
			var selected = indices.isArray.not.if
			{
				this.voxes.at(indices)
			} {
				indices.collect { |i| this.voxes.at(i) };
			};

			named[key] = (selected.isArray.not).if { selected } { VoxMulti.new(selected)};
		};

		// old
		specDict.keysValuesDo { |key, indices|
			var selected = indices.collect { |i| this.voxes.at(i) };
			named[key] = (selected.size == 1).if { selected[0] } { VoxMulti.new(selected) };
		};

		^VoxPatcher.new(named);
	}
}