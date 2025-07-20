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
    split { |specDict|
        var named = Dictionary.new;
        specDict.keysValuesDo { |key, indices|
            named[key] = VoxPlugMulti.new(indices.collect { |i| this.at(i) });
        };
        ^VoxPatcher.new(named)
    }
}