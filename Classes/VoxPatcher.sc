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

VoxRoute {
	var <sourceKey, <chain;

	*new { |sourceKey, chain|
		^super.newCopyArgs(sourceKey, chain)
	}
}