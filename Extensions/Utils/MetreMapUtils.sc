+ MetreMap {

	matching {|region|
		^this.regionIndex(region).notNil
	}

	pushDownstream {|region|
		var downstream, nextBarline, offset;

		downstream = regions.select({arg reg; reg.start > region.start});

		if (downstream.isEmpty.not) {
			var firstDown = downstream[0];
			nextBarline = region.metre.nextBarline(firstDown.start - region.start);
			offset = (nextBarline + region.start) - firstDown.start;

			// Shift and replace downstream entries
			downstream.do { |reg|
				var idx = regions.indexOf(reg);
				if (idx.notNil) {
					regions.put(idx, reg.shift(offset));
				}
			};
		};
	}

	sortEntries {
		regions.sort({ arg a, b; a.start < b.start })
	}

	whichRegion {|tickVal|
		^regions
		  .select({|entry| entry.start <= tickVal })
		  .sort({ |a, b| a.start < b.start })
		  .last;
	}

	regionIndex {|inRegion|
		^regions.detectIndex({ arg region; region == inRegion });
	}

	indexFromTicks {|tickVal|
		^this.regionIndex(this.whichRegion(tickVal));
	}

	insertionIndex {|tickVal|
		var idx = this.indexFromTicks(tickVal);

		if (idx.isNil) { ^0 };

		^idx + 1;
	}

	getBarOffsetTicks { |tickVal|

		var barlineTick = this.lastBarline(tickVal);

		^tickVal - barlineTick;
	}

	getBarOffset {|ticks|

		var region = this.whichRegion(ticks);
		var difference = ticks - region.start;

		if (region.isNil) {
			("No region found for tick value % in getBarOffset".format(ticks)).throw;
		};

		^(difference / region.metre.ticksPerBar).floor.asInteger;
	}

	lastBarline {|tickVal|
		var region = this.whichRegion(tickVal);

		if (region.isNil) { ^0 }

		^region.metre.lastBarline(tickVal-region.start) + region.start;
	}

	isBarAligned {|region|

		if (region.isKindOf(MetreRegion).not) {
			("isBarAligned expected MetreRegion, got %".format(region.class)).throw;
		};

		^region.start == this.lastBarline(region.start)
	}

	regionSize {|region|
		var index, current, next;

		if (region.isKindOf(MetreRegion).not) {
			("regionSize expected MetreRegion, got %".format(region.class)).throw;
		};

		index = this.regionIndex(region);
		if (index.isNil) {
			("MetreRegion not found in MetreMap").throw
		};

		^this.regionSizeFromIndex(index);
	}

	regionSizeFromIndex {|index|
		var
		current = regions[index],
		next = regions[index+1];

		if (index < 0) {
			("regionSizeFromIndex: invalid index %".format(index)).throw;
		};

		if (next.isNil) {
			"regionSizeFromIndex: no next region; size is undefined".warn;
			^nil
		}

		^next.start - current.start
	}

	regionBarsFromIndex {|index|
		var ticks = this.regionSizeFromIndex(index);
		if (ticks.notNil) {
			^regions[index].metre.ticksToBars(ticks);
		} { ^nil }
	}

	regionBeatsFromIndex {|index|
		var ticks = this.regionSizeFromIndex(index);
		if (ticks.notNil) {
			^regions[index].metre.ticksToBeats(ticks);
		} { ^nil }
	}

	regionDivisionsFromIndex {|index|
		var ticks = this.regionSizeFromIndex(index);
		if (ticks.notNil) {
			^regions[index].metre.ticksToDivisions(ticks);
		} { ^nil }
	}

	// I think this isn't fit for purpose anymore?
	matchingRegion {|region|
		regions.do({
			arg entry;
			if (entry == region) { ^entry } { ^nil }
		})
	}

	matchingMetreRegionStart {|inRegion|

		if (inRegion.isKindOf(MetreRegion).not) {
			("matchingMetreRegionStart expected MetreRegion, got %".format(inRegion.class)).throw;
		};

		^regions.detect({ |region| region.start == inRegion.start });
	}

	matchingRegionIndex {|region|

		if (region.isKindOf(MetreRegion).not) { ^nil };

		^regions.detectIndex({arg reg; reg == region})
	}

	matchingRegionStartIndex {|region|
		if (region.isKindOf(MetreRegion).not) { ^nil };

		^regions.detectIndex({arg reg; reg.start == region.start})
	}

	isEarliest {|region|
		^regions.isEmpty or: { region < regions.first }
	}


	snapToLastBarline {|region|
		var snappedStart = this.lastBarline(region.start).asInteger;

		^MetreRegion(snappedStart, region.metre);
	}

	listEntries {
		"--- MetreMap Regions ---".postln;
		regions.do { |r, i| ("[%] %".format(i, r)).postln };
	}

	ticksToBars {|ticks, offsetTicks = 0|
		var
		bars = 0, remTicks = 0,

		startTicks = offsetTicks,
		endTicks = ticks,

		startRegion, endRegion,
		startRegionIndex, endRegionIndex,
		ticksToRegionEnd,

		partialStart, partialEnd,
		counter, ticksPerBar;

		if (endTicks < startTicks) { ^nil };

		// get regions at ends of tick span
		startRegion = this.whichRegion(startTicks);
		endRegion = this.whichRegion(endTicks);

		// nil if either has no region
		if (startRegion.isNil or: { endRegion.isNil }) { ^nil };

		// this block if both start and end fall in same MetreRegion
		if (startRegion == endRegion) {
			var
			region = startRegion,  // (or end region)...
			regStartToSpanStart = region.metre.ticksToBars(startTicks - region.start),
			regStartToSpanEnd = region.metre.ticksToBars(endTicks - region.start);

			// get bars for this section
			bars = regStartToSpanEnd.bars - regStartToSpanStart.bars;

			// any remaining ticks for same section
			remTicks = regStartToSpanEnd.ticks - regStartToSpanStart.ticks;

			// in case remTicks less than 0, adjust bar and ticks counts
			if (remTicks < 0) {
				bars = bars - 1;
				remTicks = remTicks + region.metre.ticksPerBar;
			};

			^(bars: bars, ticks: remTicks);
		};

		startRegionIndex = this.indexFromTicks(startTicks);
		endRegionIndex = this.indexFromTicks(endTicks);

		[startRegionIndex, endRegionIndex].postln;

		// if span is across multiple regions
		// 1. From startTicks to end of its region
		ticksToRegionEnd = this.regionSize(startRegion) - (startTicks - startRegion.start);
		ticksToRegionEnd.postln; // 2880
		partialStart = startRegion.metre.ticksToBars(ticksToRegionEnd);
		partialStart.postln;
		bars = partialStart.bars;
		remTicks = partialStart.ticks;

		bars.postln;
		remTicks.postln;

		// 2. If intermediate full regions (no remainder ticks possible, no regions, won't run)
		counter = startRegionIndex + 1;
		while { counter < endRegionIndex } {
			var regionBars = this.regionBarsFromIndex(counter);
			// update total bars, no additional ticks as these regions are bounded
			bars = bars + regionBars.bars;
			counter = counter + 1;
		};

		// 3. From start of endRegion to endTicks
		partialEnd = endRegion.metre.ticksToBars(endTicks - endRegion.start);
		// update total bars
		bars = bars + partialEnd.bars;
		// only remaining ticks in this region are relevant for return
		remTicks = partialEnd.ticks;

		^(bars: bars, ticks: remTicks);
	}

	ticksToBeats {|ticks, offsetTicks = 0|
		var
		beats = 0, remTicks = 0,

		startTicks = offsetTicks,
		endTicks = ticks,

		startRegion, endRegion,
		startRegionIndex, endRegionIndex,
		ticksToRegionEnd,

		partialStart, partialEnd,
		counter, ticksPerBeat;

		if (endTicks < startTicks) { ^nil };

		// get regions at ends of tick span
		startRegion = this.whichRegion(startTicks);
		endRegion = this.whichRegion(endTicks);

		// nil if either has no region
		if (startRegion.isNil or: { endRegion.isNil }) { ^nil };

		// this block if both start and end fall in same MetreRegion
		if (startRegion == endRegion) {
			var
			region = startRegion,  // (or end region)...
			// actually this is fine? calculates beats and rem from regStart to start
			// and regStart to end ... taking one from the other should work...
			regStartToSpanStart = region.metre.ticksToBeats(startTicks - region.start),
			regStartToSpanEnd = region.metre.ticksToBeats(endTicks - region.start);

			// get bars for this section
			beats = regStartToSpanEnd.beats - regStartToSpanStart.beats;

			// any remaining ticks for same section
			remTicks = regStartToSpanEnd.ticks - regStartToSpanStart.ticks;

			// it's this next bit that's tricky - work out what the

			// in case remTicks less than 0, adjust bar and ticks counts
			if (remTicks < 0) {
				var idx;
				beats = beats - 1;
				idx = regStartToSpanEnd.beats % region.metre.beatsPerBar;
				remTicks = remTicks + region.metre.ticksPerBeat(idx);
				// does this account for potentially different sized beats?
			};

			^(beats: beats, ticks: remTicks);
		};

		startRegionIndex = this.indexFromTicks(startTicks);
		endRegionIndex = this.indexFromTicks(endTicks);

		// if span is across multiple regions
		// 1. From startTicks to end of its region
		ticksToRegionEnd = this.regionSize(startRegion) - (startTicks - startRegion.start);
		partialStart = startRegion.metre.ticksToBeats(ticksToRegionEnd);
		beats = partialStart.beats;

		// 2. If intermediate full regions (no remainder ticks possible, no regions, won't run)
		counter = startRegionIndex + 1;
		while { counter < endRegionIndex } {
			var regionBeats = this.regionBeatsFromIndex(counter);
			// update total bars, no additional ticks as these regions are bounded
			beats = beats + regionBeats.beats;
			counter = counter + 1;
		};

		// 3. From start of endRegion to endTicks
		partialEnd = endRegion.metre.ticksToBeats(endTicks - endRegion.start);
		// update total bars
		beats = beats + partialEnd.beats;
		// only remaining ticks in this region are relevant for return
		remTicks = partialEnd.ticks;

		^(beats: beats, ticks: remTicks);
	}

	/*// BROKEN -- needs better handling of offset
	ticksToBeats {|ticks, barOffset = 0|

		var beats = 0, counter = 0;
		var barOffsetTicks = this.barsToTicks(barOffset);
		var absTicks = ticks + barOffsetTicks;
		var remBeats, thisRegionIndex, overflow;
		var thisRegion = this.whichRegion(absTicks);
		var ticksToBeats;

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		ticksToBeats = thisRegion.metre.ticksToBeats(absTicks - thisRegion.start);
		remBeats = ticksToBeats.beats;
		overflow = ticksToBeats.ticks;

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(absTicks);

		// if index is 0, no prior regions; return beats from thisRegion
		if (thisRegionIndex == 0) {
			^(beats: remBeats - this.ticksToBeats(barOffsetTicks).beats, ticks: overflow);
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			beats = beats + this.regionBeatsFromIndex(counter).beats;
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^(beats: remBeats + beats - this.ticksToBeats(barOffsetTicks).beats, ticks: overflow);
	}*/

	// BROKEN -- needs better handling of offset
	ticksToDivisions {|ticks, barOffset = 0, beatOffset = 0|
		var divisions = 0, counter = 0;

		var barOffsetTicks = this.barsToTicks(barOffset);
		var beatOffsetTicks = this.beatsToTicks(beatOffset);
		var totalOffsetTicks = barOffsetTicks + beatOffsetTicks;

		var absTicks = ticks + totalOffsetTicks;

		var remDivisions, thisRegionIndex, overflow;
		var thisRegion = this.whichRegion(absTicks);
		var ticksToDivisions;

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		ticksToDivisions = thisRegion.metre.ticksToDivisions(absTicks - thisRegion.start);
		remDivisions = ticksToDivisions.divisions;
		overflow = ticksToDivisions.ticks;

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(absTicks);

		// if index is 0, no prior regions; return bars from thisRegion
		if (thisRegionIndex == 0) {
			^(divisions: remDivisions - this.ticksToDivisions(totalOffsetTicks).divisions, ticks: overflow);
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			divisions = divisions + this.regionDivisionsFromIndex(counter).divisions;
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^(divisions: remDivisions + divisions - this.ticksToDivisions(totalOffsetTicks).divisions, ticks: overflow);
	}

	barsToTicks {|bars|
		var ticks = 0;

		regions.do({
			arg reg, i;
			var regionBars = this.regionBarsFromIndex(i);

			if (regionBars.isNil) { ^ticks + regions[i].metre.barsToTicks(bars) };

			if ((bars - regionBars.bars).isNegative) {
				^ticks + regions[i].metre.barsToTicks(bars)
			} {
				ticks = ticks + this.regionSizeFromIndex(i);
				bars = bars - regionBars.bars;
			}
		})
	}

	barsToBeats {|bars|
		var beats = 0;

		regions.do({
			arg reg, i;
			var regionBars = this.regionBarsFromIndex(i);

			if (regionBars.isNil) { ^beats + reg.metre.barsToBeats(bars) };

			if ((bars - regionBars.bars).isNegative) {
				^beats + reg.metre.barsToBeats(bars);
			} {
				beats = beats + this.regionBeatsFromIndex(i).beats;
				bars = bars - regionBars.bars;
			}
		})
	}

	beatsToDivisions {|beats, beatOffset=0|
		var divs = 0;

		regions.do({
			arg reg, i;
			var regionBeats = this.regionBeatsFromIndex(i);

			if (regionBeats.isNil) { ^divs + reg.metre.beatsToDivisions(beats, beatOffset) };

			if ((beats - regionBeats.beats).isNegative) {
				^divs + reg.metre.beatsToDivisions(beats, beatOffset);
			} {
				divs = divs + this.regionDivisionsFromIndex(i).divisions;
				beats = beats - regionBeats.beats;
			};
		});
	}

	beatsToTicks {|beats, beatOffset=0|
		var ticks = 0;

		regions.do({
			arg reg, i;
			var regionBeats = this.regionBeatsFromIndex(i);

			if (regionBeats.isNil) { ^ticks + reg.metre.beatsToTicks(beats, beatOffset) };

			if ((beats - regionBeats.beats).isNegative) {
				^ticks + reg.metre.beatsToTicks(beats, beatOffset);
			} {
				ticks = ticks + this.regionSizeFromIndex(i);
				beats = beats - regionBeats.beats;
			}
		})
	}

	divisionsToTicks {|divs, beatOffset=0|
		var ticks = 0;

		regions.do({
			arg reg, i;
			var regionDivs = this.regionDivisionsFromIndex(i);

			if (regionDivs.isNil) { ^ticks + reg.metre.divisionsToTicks(divs, beatOffset) };

			if ((divs - regionDivs.divisions).isNegative) {
				^ticks + reg.metre.divisionsToTicks(divs, beatOffset);
			} {
				ticks = ticks + this.regionSizeFromIndex(i);
				divs = divs - regionDivs.divisions;
			}
		})
	}

}