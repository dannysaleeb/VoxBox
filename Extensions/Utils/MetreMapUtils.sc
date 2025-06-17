+ MetreMap {

	matching {|region|
		^this.matchingRegionIndex(region).notNil
	}

	pushDownstream {|region|
		var downstream, nextBarline, offset;

		downstream = regions.select({arg reg; reg.start > region.start});

		if (downstream.isEmpty.not) {
			nextBarline = region.metre.nextBarline(downstream[0].start);
			offset = nextBarline - downstream[0].start;

			downstream.do(_.shift(offset + region.start));
		};
	}

	shiftDownstream {|index, ticks|
		regions.select({ arg reg; reg.start > regions[index].start }).do({
			arg reg;
			reg.shift(ticks)
		})
	}

	sortEntries {
		regions.sort({ arg a, b; a.start < b.start })
	}

	whichRegion {|tickVal|
		^regions.select({|entry| entry.start <= tickVal }).last
	}

	regionIndex {|region|
		^regions.detectIndex({arg entry; entry.start == region.start})
	}

	indexFromTicks {|tickVal|
		^this.regionIndex(this.whichRegion(tickVal))
	}

	insertionIndex {|tickVal|
		^this.indexFromTicks(tickVal)+1
	}

	getBarOffsetTicks {|tickVal|
		var last = this.lastFromTick;
		var lastMetre = last[1];

		^tickVal - last[0];
	}

	getBarOffset {|ticks|

		var region = this.whichRegion(ticks);
		var difference = ticks - region.start;

		^(difference / region.metre.ticksPerBar).floor
	}

	lastBarline {|tickVal|
		var region = this.whichRegion(tickVal);

		// if we're in a region
		if (region.notNil) {
			// get last barline relative to region.metre, add region.start to
			// get last viable barline relative to full metremap
			^region.metre.lastBarline(tickVal-region.start) + region.start;
		} {
			^0 + region.start
		}
	}

	isBarAligned {|region|

		var prior = this.whichRegion(region.start);

		if (region.start == 0) { ^true };

		if (prior.notNil) {
			var difference = region.start - prior.start;
			^region.start == this.lastBarline(region.start)
		} {
			^false
		}
	}

	regionSize {|region|
		var index;
		index = regions.detectIndex({arg entry; entry.start == region.start});
		if (regions[index+1].notNil) {
			^regions[index+1].start - regions[index].start
		} { ^0 }
	}

	regionSizeFromIndex {|index|
		if (regions[index+1].notNil) {
			^regions[index+1].start - regions[index].start
		} { ^0 }
	}

	regionBarsFromIndex {|index|
		^regions[index].metre.ticksToBars(this.regionSizeFromIndex(index));
	}

	regionBeatsFromIndex {|index|
		^regions[index].metre.ticksToBeats(this.regionSizeFromIndex(index));
	}

	regionDivisionsFromIndex {|index|
		^regions[index].metre.ticksToDivisions(this.regionSizeFromIndex(index));
	}

	matchingRegion {|region|
		regions.do({
			arg entry;
			if (entry == region) { ^entry } { ^nil }
		})
	}

	matchingRegionIndex {|region|
		^regions.detectIndex({arg reg; reg == region})
	}

	isEarliest {|region|
		^region < regions.first
	}

	snapToLastBarline {|region|
		region.set_start(this.lastBarline(region.start).asInteger);

		^region
	}

	listEntries {
		regions.dopostln;
	}

	ticksToBars {|ticks|
		var bars = 0, counter = 0;
		var remBars, thisRegionIndex, overflow;
		var thisRegion = this.whichRegion(ticks);

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		// get number of bars to ticks from thisRegion
		#remBars, overflow = thisRegion.metre.ticksToBars(ticks - thisRegion.start);

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(ticks);

		// if index is 0, no prior regions; return bars from thisRegion
		if (thisRegionIndex == 0) {
			^[remBars, overflow]
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			bars = bars + this.regionBarsFromIndex(counter)[0];
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^[remBars + bars, overflow]
	}

	ticksToBeats {|ticks|
		var beats = 0, counter = 0;
		var remBeats, thisRegionIndex, overflow;
		var thisRegion = this.whichRegion(ticks);

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		// get number of bars to ticks from thisRegion
		#remBeats, overflow = thisRegion.metre.ticksToBeats(ticks - thisRegion.start);

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(ticks);

		// if index is 0, no prior regions; return bars from thisRegion
		if (thisRegionIndex == 0) {
			^[remBeats, overflow]
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			beats = beats + this.regionBeatsFromIndex(counter)[0]; // this.regionBeatsFromIndex?
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^[remBeats + beats, overflow]
	}

	ticksToDivisions {|ticks|
		var divisions = 0, counter = 0;
		var remDivisions, thisRegionIndex, overflow;
		var thisRegion = this.whichRegion(ticks);

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		// get number of bars to ticks from thisRegion
		#remDivisions, overflow = thisRegion.metre.ticksToDivisions(ticks - thisRegion.start);

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(ticks);

		// if index is 0, no prior regions; return bars from thisRegion
		if (thisRegionIndex == 0) {
			^[remDivisions, overflow]
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			divisions = divisions + this.regionDivisionsFromIndex(counter)[0]; // this.regionBeatsFromIndex?
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^[remDivisions + divisions, overflow]
	}

	barsToTicks {|bars|
		// got to subtract number of bars with each passing region until no regions left ...

		// work out how many bars in one region, get ticks allocated ...

		// I'll work this one out ... it's just going to take some thought.
		// Once it's done, I should be able to get quite a lot done.
	}

	beatsToTicks {|beats, beatOffset=0|

	}

	divisionsToTicks {|divs, beatOffset=0|

	}



	// FROM METRE
	// to ticks
	barsToTi {|bars|
		^bars * this.ticksPerBar
	}

	beatsToTi {|beats, beatOffset=0|
		^beats.collect({|beat| this.ticksPerBeat.wrapAt(beat + beatOffset)}).sum
	}

	divisionsToTi {|divs, beatOffset=0|
		var
		ticksPerDivision = this.ticksPerDivision,
		allDivisionTicks = this.allDivisionTicks,
		divisionOffset = beatOffset.collect{arg i; this.divisions[i]}.sum;

		^divs.collect({|division| allDivisionTicks.wrapAt(division + divisionOffset)}).sum
	}

}