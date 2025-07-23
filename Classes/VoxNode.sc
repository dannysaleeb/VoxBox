VoxNode {
	var <>in, <>label;

	headNode {
		var node = this;
		while {
			node.in.notNil and: { node.in.isKindOf(VoxNode) }
		} {
			node = node.in;
		};
		^node;
	}

	out {
		"😬 .out not implemented on %".format(this.class).warn;
		^nil
	}
}