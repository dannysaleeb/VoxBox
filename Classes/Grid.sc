///////////////////////////////////////////////////
// Score Position [bars, beats, divisions, tick] //
///////////////////////////////////////////////////
Pos {
    var bar, beat, division, tick;

    *new { |bar=0, beat=0, division=0, tick=0|

        ^super.new.init(bar, beat, division, tick);
    }

	init {
		arg bar, beat, division, tick;

		this.bar = bar.asInteger;
		this.beat = beat.asInteger;
		this.division = division.asInteger;
		this.tick = tick.asInteger;

		^this
	}

	bar { ^bar }
	beat { ^beat }
	division { ^division }
	tick { ^tick }

	bar_ { arg inval; bar = inval.asInteger }
	beat_ { arg inval; beat = inval.asInteger }
	division_ { arg inval; division = inval.asInteger }
	tick_ { arg inval; tick = inval.asInteger }

	copy {
		arg bar = this.bar, beat = this.beat, division = this.division, tick = this.tick;

		^Pos.new(bar, beat, division, tick)
	}


    asString {
        ^"Pos[% % % %]".format(bar, beat, division, tick);
	}
}

//////////////////////////
// Metre
//////////////////////////
Metre {
    var beats, divisions, tpqn;

    *new {
		arg beats=[1,1,1,1], divisions=[4,4,4,4], tpqn=960;

		^super.new.init(beats, divisions, tpqn);
    }

	init {
		arg inBeats, inDivisions, inTPQN;

		inDivisions.do { |val, i|
			if (val.isInteger.not or: { val <= 0 }) {
				("Invalid division at index %: must be positive integer.".format(i)).throw;
			};
		};

		if (inBeats.size != inDivisions.size) {
			inDivisions = inBeats.size.collect({ arg i; inDivisions.wrapAt(i) });
			("divisions was altered to match beats: " ++ inDivisions).warn;
		};

		beats = inBeats.copy;
		divisions = inDivisions.copy;
		tpqn = inTPQN.asInteger;
	}

	//////////////
	// GETTERS
	//////////////
	beats { ^beats.copy }
	divisions { ^divisions.copy }
	tpqn { ^tpqn }

	== {
		arg other;

		var beats = this.beats == other.beats;
		var divisions = this.divisions == other.divisions;
		var tpqn = this.tpqn == other.tpqn;

		if (other.isKindOf(Metre).not) { ^false };

		^(beats && divisions && tpqn);
	}

	asString {

		^"Metre(%, %)".format(beats, divisions)
	}
}

//////////////////////////
// MetreRegion
//////////////////////////
MetreRegion {
	var start, metre;

	*new {
		arg start, metre;

		^super.new.init(start, metre)
	}

	init {
		arg startArg, metreArg;

		if (metreArg.isKindOf(Metre).not) {
			("MetreRegion: 'metre' argument must be an instance of Metre, got: %".format(metreArg.class)).throw;
		};

		start = startArg.floor.asInteger;
		metre = metreArg;

		^this
	}

	start { ^start }
	metre { ^metre }

	copy {
		arg
		start = this.start,
		metre = this.metre;

		^MetreRegion.new(start, metre)
	}

	asString {
		^"Region(start: % || metre: %)".format(start, metre)
	}
}

//////////////////////////
// MetreMap
//////////////////////////
MetreMap {
	var <>regions, tpqn;

	*new {
		^super.new.init
	}

	init {
		regions = List.new;
		^this;
	}

	tpqn { ^tpqn }

	add {|region|
		var matchIdx, insertionIdx;

		if (tpqn.isNil) { tpqn = region.metre.tpqn };

		if (region.isKindOf(MetreRegion).not) {
			"can only add MetreRegion objects to MetreMap".warn;
			^this
		};

		if (regions.isEmpty) { regions.add(region); ^this };

		// equality check on start value
		matchIdx = this.matchingRegionStartIndex(region); // returning nil
		if (matchIdx.notNil) {
			regions.put(matchIdx, region);
			this.pushDownstream(region);
			^this
		};

		if (this.isEarliest(region)) {
			// add region as first region
			regions.addFirst(region);
			this.pushDownstream(region);
			^this
		};

		// in between or at end
		insertionIdx = this.insertionIndex(region.start);

		if (this.isBarAligned(region)) {
			regions.insert(insertionIdx, region);
			this.pushDownstream(region);
			^this
		};

		region = this.snapToLastBarline(region);

		matchIdx = this.matchingRegionStartIndex(region);
		if (matchIdx.notNil) {
			regions.put(matchIdx, region);
			this.pushDownstream(region);
			^this
		};

		regions.insert(insertionIdx, region);
		this.pushDownstream(region);
		^this
	}

	// to utils
	isEmpty {
		^regions.isEmpty
	}

	// IMPLEMENT COPY .. for VOX
}