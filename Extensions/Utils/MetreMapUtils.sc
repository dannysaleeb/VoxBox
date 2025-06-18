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

		var index = regions.detectIndex({arg entry; entry.start == region.start});
		var current = regions[index];
		var next = regions[index+1];

		if (next.notNil) {
			^next.start - current.start
		} {
			^nil
		}
	}

	regionSizeFromIndex {|index|

		var current = regions[index];
		var next = regions[index+1];

		if (current.notNil && next.notNil) {
			^next.start - current.start
		} {
			^nil
		}

	}

	regionBarsFromIndex {|index|
		var ticks = this.regionSizeFromIndex(index);
		if (ticks.notNil) {
			^regions[index].metre.ticksToBars(this.regionSizeFromIndex(index));
		} { ^nil }
	}

	regionBeatsFromIndex {|index|
		var ticks = this.regionSizeFromIndex(index);
		if (ticks.notNil) {
			^regions[index].metre.ticksToBeats(this.regionSizeFromIndex(index));
		} { ^nil }
	}

	regionDivisionsFromIndex {|index|
		var ticks = this.regionSizeFromIndex(index);
		if (ticks.notNil) {
			^regions[index].metre.ticksToDivisions(this.regionSizeFromIndex(index));
		} { ^nil }
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

		// if index is 0, no prior regions; return beats from thisRegion
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
		var ticks = 0;

		regions.do({
			arg reg, i;
			var regionBars = this.regionBarsFromIndex(i);

			if (regionBars.isNil) { ^ticks + regions[i].metre.barsToTicks(bars) };

			if ((bars - regionBars[0]).isNegative) {
				^ticks + regions[i].metre.barsToTicks(bars)
			} {
				ticks = ticks + this.regionSizeFromIndex(i);
				bars = bars - regionBars[0];
			}
		})
	}

	beatsToTicks {|beats, beatOffset=0|
		var ticks = 0;

		regions.do({
			arg reg, i;
			var regionBeats = this.regionBeatsFromIndex(i);

			if (regionBeats.isNil) { ^ticks + reg.metre.beatsToTicks(beats, beatOffset) };

			if ((beats - regionBeats[0]).isNegative) {
				^ticks + reg.metre.beatsToTicks(beats, beatOffset);
			} {
				ticks = ticks + this.regionSizeFromIndex(i);
				beats = beats - regionBeats[0]
			}
		})
	}

	divisionsToTicks {|divs, beatOffset=0|
		var ticks = 0;

		regions.do({
			arg reg, i;
			var regionDivs = this.regionDivisionsFromIndex(i);

			if (regionDivs.isNil) { ^ticks + reg.metre.divisionsToTicks(divs, beatOffset) };

			if ((divs - regionDivs[0]).isNegative) {
				^ticks + reg.metre.divisionsToTicks(divs, beatOffset);
			} {
				ticks = ticks + this.regionSizeFromIndex(i);
				divs = divs - regionDivs[0]
			}
		})
	}

}