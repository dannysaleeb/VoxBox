+ Symbol {

	<<< { |chain|
		^VoxRoute.new(this, chain);
	}

}

+ Integer {

	<<< { |chain|
		^VoxRoute.new(this.chain);
	}

}

// I think might need a better connection design, to keep track of flow of parts, just an ordering system for voices ... sort by their voiceID or something ... 