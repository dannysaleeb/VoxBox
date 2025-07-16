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

	ticksToBars {|ticks|
		var bars = 0, remTicks = 0;
		var region = this.whichRegion(ticks);
		var index, rel;

		if (region.isNil) { ^nil };

		index = this.indexFromTicks(ticks);

		// Add all full regions before this one
		// doesn't run if index is 0
		index.do { |i|
			var regionBars = this.regionBarsFromIndex(i);
			bars = bars + regionBars.bars;
		};

		// Add bars + ticks within this region
		rel = region.metre.ticksToBars(ticks - region.start);
		bars = bars + rel.bars;
		remTicks = rel.ticks;

		^(bars: bars, ticks: remTicks);
	}

	ticksToBeats {|ticks|
		var beats = 0, counter = 0;
		var remBeats, thisRegionIndex, overflow;
		var thisRegion = this.whichRegion(ticks);
		var thisRegionBeats;

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		// get number of beats to ticks from thisRegion
		thisRegionBeats = thisRegion.metre.ticksToBeats(ticks - thisRegion.start);
		remBeats = thisRegionBeats.beats;
		overflow = thisRegionBeats.ticks;

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(ticks);

		// if index is 0, no prior regions; return beats from thisRegion
		if (thisRegionIndex == 0) {
			^(beats: remBeats, ticks: overflow);
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			beats = beats + this.regionBeatsFromIndex(counter).beats;
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^(beats: remBeats + beats, ticks: overflow);
	}

	ticksToDivisions {|ticks|
		var divisions = 0, counter = 0;
		var remDivisions, thisRegionIndex, overflow;
		var thisRegion = this.whichRegion(ticks);
		var thisRegionDivisions;

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		// get number of bars to ticks from thisRegion
		thisRegionDivisions = thisRegion.metre.ticksToDivisions(ticks - thisRegion.start);
		remDivisions = thisRegionDivisions.divisions;
		overflow = thisRegionDivisions.ticks;

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(ticks);

		// if index is 0, no prior regions; return bars from thisRegion
		if (thisRegionIndex == 0) {
			^(divisions: remDivisions, ticks: overflow);
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			divisions = divisions + this.regionDivisionsFromIndex(counter)[0]; // this.regionBeatsFromIndex?
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^(divisions: remDivisions + divisions, ticks: overflow);
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

	deltaBeats { |startTick, endTick|
		var totalBeats = 0;
		var pos = startTick;

		while { pos < endTick } {
			var region = this.whichRegion(pos);
			var regionEnd = this.regionSize(region) ?? endTick;
			var ticksPerBeat = region.metre.ticksPerBeat;

			var chunkEnd = endTick.min(regionEnd), tickDelta;
			chunkEnd.postln;

			tickDelta = chunkEnd - pos;

			totalBeats = totalBeats + (tickDelta / ticksPerBeat);
			pos = chunkEnd;
		};

		^totalBeats;
	}

}