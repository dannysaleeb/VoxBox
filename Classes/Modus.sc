//   .--..--..--..--..--..--..--..--..--..--..--..--..--..--..--.
//  / .. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \
//  \ \/\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ \/ /
//   \/ /`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'\/ /
//   / /\                                                    / /\
//  / /\ \    __  __           _           _     _ _        / /\ \
//  \ \/ /   |  \/  | ___   __| |_   _ ___| |   (_) |__     \ \/ /
//   \/ /    | |\/| |/ _ \ / _` | | | / __| |   | | '_ \     \/ /
//   / /\    | |  | | (_) | (_| | |_| \__ \ |___| | |_) |    / /\
//  / /\ \   |_|  |_|\___/ \__,_|\__,_|___/_____|_|_.__/    / /\ \
//  \ \/ /                                                  \ \/ /
//   \/ /                                                    \/ /
//   / /\.--..--..--..--..--..--..--..--..--..--..--..--..--./ /\
//  / /\ \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \/\ \
//  \ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `' /
//   `--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'

// A modular MIDI timing and score utility library for SuperCollider.
// Provides conversion, structure, and extensible clip-processing tools for music programming.


//////////////////////////////////////////////////
// Score Position [bars, beats, divisions, tick]
//////////////////////////////////////////////////
Pos {
    var bar, beat, division, tick;

    *new { |bar=0, beat=0, division=0, tick=0|

        ^super.newCopyArgs(bar, beat, division, tick);
    }

	//////////////
	// GETTERS
	//////////////
	bar { ^bar }
	beat { ^beat }
	division { ^division }
	tick { ^tick }

	//////////////
	// SETTERS
	//////////////
	set_bar { arg inval; bar = inval }
	set_beat { arg inval; beat = inval }
	set_division { arg inval; division = inval }
	set_tick { arg inval; tick = inval }

	// there's probably no reason to individually set these? Or maybe if doing newFrom? not sure.

	subtractTicks {|ticks, metre|
		^TimeConverter.normalize(Pos(bar, beat, division, tick - ticks), metre)
	}

    isEqualTo { |other, metre|
		var first = this.normalize(metre);
		other = other.normalize(metre);

		^[first.bar, first.beat, first.division, first.tick]
		== [other.bar, other.beat, other.division, other.tick]
	}

    asString {
        ^"Pos[% % % %]".format(bar.asInteger, beat.asInteger, division.asInteger, tick.asInteger);
    }
}

TimeConverter {

	*posToTicks {|pos, metre|
		var
		bars = metre.barsToTicks(pos.bar),
		beats = metre.beatsToTicks(pos.beat),
		divisions = metre.divisionsToTicks(pos.division, pos.beat);

		^[bars, beats, divisions, pos.tick].sum
	}

	/**posToTicks {|pos, metreMap|
		//

	}*/

	// posMinusTicks {ticks}

	*ticksToPos {|ticks, metre|
		var bar, remainder, beat, division, tick;

		#bar, remainder = metre.ticksToBars(ticks);
		#beat, remainder = metre.ticksToBeats(remainder);
		#division, tick = metre.ticksToDivisions(remainder, beat);

		^Pos(bar, beat, division, tick)
	}

	*normalize {
		arg pos, metre;

		var totalTicks = this.posToTicks(pos, metre);

		^this.ticksToPos(totalTicks, metre)
	}
}

//////////////////////////
// Metre
//////////////////////////
Metre {
    var beats, divisions, tpqn;

    *new {
		arg beats=[1,1,1,1], divisions=[4,4,4,4], tpqn=960;

		// this needs a check to make sure beats & divisions are same size

        ^super.newCopyArgs(beats, divisions, tpqn);
    }

	//////////////
	// GETTERS
	//////////////
	beats { ^beats }
	divisions { ^divisions }
	tpqn { ^tpqn }

	//////////////
	// SETTERS
	//////////////
	set_beats {|inval| beats = inval }
	set_divisions {|inval| divisions = inval }
	set_tpqn {|inval| tpqn = inval }

	// could add validation checks to setters ...

	////////////////////////
	// CONVERSION HELPERS //
	////////////////////////
	ticksPerBar {
		^this.ticksPerBeat.sum
    }

	ticksPerBeat {
		^beats.collect({|beat|
			beat*tpqn
		})
    }

	ticksPerDivision {
		^beats.collect({
			arg beat, i;
			(beat*tpqn) / divisions[i].asInteger
		})
    }

	divisionsPerBar {
		^divisions.sum
	}

	divisionsPerBeat {
		^divisions
	}

	allDivisionTicks {
		var ticksPerDivision = this.ticksPerDivision;
		^divisions.collect({|divs, i| ticksPerDivision[i].dup(divs) }).flat;
	}

	beatsPerBar {
		^beats.size
	}

	// to ticks
	barsToTicks {|bars|
		^bars * this.ticksPerBar
	}

	beatsToTicks {|beats, beatOffset=0|
		^beats.collect({|beat| this.ticksPerBeat.wrapAt(beat + beatOffset)}).sum
	}

	divisionsToTicks {|divs, beatOffset=0|
		var
		ticksPerDivision = this.ticksPerDivision,
		allDivisionTicks = this.allDivisionTicks,
		divisionOffset = beatOffset.collect{arg i; this.divisions[i]}.sum;

		^divs.collect({|division| allDivisionTicks.wrapAt(division + divisionOffset)}).sum
	}

	// from ticks
	ticksToBars {|ticks|
		var ticksPerBar = this.ticksPerBar;
		^[ticks div: ticksPerBar, ticks % ticksPerBar]
	}

	ticksToBeats {|totalTicks|
		var ticksPerBeat = this.ticksPerBeat;
		var iterator = 0;
		var counter = 0;

		while { (totalTicks - ticksPerBeat.wrapAt(iterator)).isNegative.not } {
			totalTicks = totalTicks - ticksPerBeat.wrapAt(iterator);
			counter = counter + 1;
			iterator = iterator + 1;
		};

		^[counter, totalTicks]
	}

	ticksToDivisions {|totalTicks, beatOffset=0|
		var
		divisions = this.divisions,
		count=0,
		allDivisionTicks = this.allDivisionTicks,
		divisionOffset = beatOffset.collect{arg i; divisions[i]}.sum;

		loop {
			allDivisionTicks.size.do({
				arg i;
				var ticks = allDivisionTicks[i + divisionOffset];

				if (totalTicks >= ticks) {
					totalTicks = totalTicks - ticks;
					count = count + 1;
				} { ^[count, totalTicks] }
			})
		}
	}

	// other helpers
	lastBarline {|tick|
		^tick - (tick % this.ticksPerBar)
	}

	nextBarline {|tick|
		var ticksPerBar = this.ticksPerBar;
		var rem = tick % ticksPerBar;

		if (rem == 0) { ^tick };

		^tick + (ticksPerBar - rem)
	}

	nearestBarline {|tick|
		var ticksPerBar = this.ticksPerBar;
		var rem, diff;

		rem = tick % ticksPerBar;

		if (rem == 0) { ^tick };

		diff = ticksPerBar - rem;
		if (diff < rem) { ^(tick+diff).asInteger } { ^(tick-rem).asInteger }
	}

	asString {
		^"Metre(%, %)".format(beats, divisions)
	}
}

Region {
	var start, metre;

	*new {
		arg start, metre;

		^super.newCopyArgs(start, metre)
	}

	start { ^start }
	metre { ^metre }

	set_start { |inval| start = inval }
	set_metre { |inval| metre = inval }

	shift {|inval|
		this.set_start(this.start + inval);
	}

	asString {
		^"Region(start: % || metre: %)".format(start, metre)
	}

	== {|other|
		^this.start == other.start
	}

	< {|other|
		^this.start < other.start
	}

	> {|other|
		^this.start > other.start
	}
}

//////////////////////////
// MetreMap
//////////////////////////
MetreMap {
	var regions;

	// I don't want regions adjusted outside of the class, so this should be private...

	*new {
		^super.new.init
	}

	init {
		regions = List.new;
		^this;
	}

	add {|region|
		var matchIndex;
		// region goes in before others, if doesn't align, all downstream regions snap to next barline
		// region goes in between, if not bar aligned, snaps to last barline, check for match, else all downstream regions snap to next
		// region goes at end, if not aligned, snaps back ...
		// region goes in empty, nothing else to do
		if (regions.isEmpty) { regions.add(region); ^this };

		// check for a raw match
		matchIndex = this.matchingRegionIndex(region);
		if (matchIndex.notNil) {
			var nextBarline, offset;
			// replace matching region
			regions.put(matchIndex, region);

			// if final region in regions, return this
			if (regions[matchIndex + 1].isNil) { ^this };

			// else get nextBarline for next region
			nextBarline = regions[matchIndex].metre.nextBarline(regions[matchIndex + 1].start);
			offset = (nextBarline - regions[matchIndex + 1].start) + regions[matchIndex].start;

			// move them all forward by offset
			this.shiftDownstream(matchIndex, offset);
		};

		if (this.isEarliest(region)) {
			var nextBarline, offset;

			// add region as first region
			regions.addFirst(region);

			// check for nextBarline (which will be unchanged if already a barline)
			nextBarline = regions[0].metre.nextBarline(regions[1].start);
			offset = (nextBarline - regions[1].start) + regions[0].start;

			// shift all downstream by offset
			this.shiftDownstream(0, offset);
		};

		// do in between insertion
		// need to check for matches after any snapto barline

		// add region at end, snap back and check for matches if needed

		// WRITE methods for shiftDownstreamRegions(index, ticks) and matching one?

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

	updateStarts {

		if (regions.notNil) {
			regions.first.set_start(0)
		};

		regions.size.do({
			arg i;
			var current, next, bars;
			var adjusted, tickOffset;

			next = regions[i+1];
			current = regions[i];

			if (next.notNil) {
				bars = current.metre.ticksToBars(next.tick - current.tick);

				// must be at least 1 bar
				if (bars[0] == 0) {
					adjusted = current.tick + current.metre.barsToTicks(1);
					tickOffset = adjusted - next.tick;
					next.set_bar(1 + current.bar)
				};

				adjusted = current.metre.barsToTicks(bars[0]);

				tickOffset = adjusted - next.tick;

				// set new bar value
				next.set_bar(bars[0] + current.bar);
				// set new tick value
				next.shift(tickOffset);
			}
		});
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

	updateBars {
		regions.do({
			arg entry, i;
			if (regions[i+1].notNil) {
				var bars = ((regions[i+1].start - entry.start) / entry.metre.ticksPerBar).floor;
				entry.set_bars(bars)
			}
		})
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

		if (region.notNil) {
			^this.getBarOffset(tickVal) * region.metre.ticksPerBar
		} {
			^0
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
		var bars;
		bars = regions[index].metre.ticksToBars(this.regionSizeFromIndex(index));
		^bars
	}

	matchingRegion {|region|
		regions.do({
			arg entry;
			if (entry == region) { ^entry } { ^nil }
		})
	}

	matchingRegionIndex {|region|
		^regions.detectIndex({arg entry; entry == region})
	}

	isEarliest {|region|
		^region < regions.first
	}

	snapToLastBarline {|region|
		region.set_tick(this.lastBarline(region.start).asInteger);
		^region
	}

	listEntries {
		regions.dopostln;
	}

	ticksToBars {|ticks|
		var bars = 0, counter = 0;
		var remBars, thisRegionIndex;
		var thisRegion = this.whichRegion(ticks);

		// return nil if no regions
		if (thisRegion.isNil) { ^nil };

		// get number of bars to ticks from thisRegion
		remBars = thisRegion.metre.ticksToBars(ticks - thisRegion.start)[0];

		// get region index from ticks
		thisRegionIndex = this.indexFromTicks(ticks);

		// if index is 0, no prior regions; return bars from thisRegion
		if (thisRegionIndex == 0) {
			^remBars
		};

		// else calculate sum of bars in all prior regions
		while { counter < thisRegionIndex } {
			bars = bars + this.regionBarsFromIndex(counter)[0];
			counter = counter + 1;
		};

		// add prior regions to thisRegion bars and return
		^remBars + bars
	}

}

//////////////////////////
// Vox
//////////////////////////
Vox {
	// it gets a noteSustainEvents array and a metre
	var <>events, <>metreMap, <>range;
	var <>index = 0;

	*new {
		arg noteSustainEvents, metreMap=MetreMap.new;
		var events, range;

		if (noteSustainEvents.isEmpty) {
			^super.newCopyArgs([], metreMap);
		};

		// create events
		events = noteSustainEvents.collect({
			arg nse;
			var event;

			event = Event.newFrom([[\track, \absTime, \midicmd, \channel, \midinote, \velocity, \dur, \upVelo], nse].lace);

		});

		// make sure they're sorted
		events.sortBy(\absTime);

		// add position information
		events.do({
			arg event;
			event[\position] = event[\absTime].toPos(metreMap);
		});

		// set highlight to span the entire voice.
		if (events.isEmpty) { nil }
		{
			var
			first = events[0],
			last = events[events.size-1];

			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
		};

		^super.newCopyArgs(events, metreMap, range)
	}

	*newFromEventsArray {
		arg events, metreMap;
		var range;

		// make sure they're sorted
		events.sortBy(\absTime);

		// set range
		if (events.isEmpty) { nil }
		{
			var
			first = events[0],
			last = events[events.size-1];

			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
		};

		^super.newCopyArgs(events, metreMap, range);

	}

	asTask {
		arg instrument=\default;

		^Task {
			var i=0, clip, synth, event;

			clip = this.clip(this.range);

			clip.do {
				arg event;
				synth = Synth(instrument, [\freq, event[\midinote].midicps]);
				i = i+1;
				// need to adapt for new metre format
				// will need to store position info on events, so can check divisions in beat
				(event[\dur] / this.metre.ticksPerBeat).wait;
				synth.release(2)
			}
		}
	}

	asLoop {
		arg instrument=\default;
		var clip;

		clip = this.clip(this.range);

		^Task {
			var i=0, synth;
			loop {
				var event;
				event = clip.wrapAt(i);
				synth = Synth(instrument, [\freq, event[\midinote].midicps]);
				i = i+1;
				// need to adapt for new metre format
				(event[\dur] / this.metre.ticksPerBeat).wait;
				synth.release(2);
			}
		}
	}

	midiTask {
		arg midiout;

		^Task {
			var clip;

			clip = this.clip(this.range);

			clip.do {
				arg event;
				midiout.noteOn(event.channel, event.midinote, event.velocity);
				// need to adapt for new Metre format
				(event.dur / this.metre.ticksPerBeat).wait;
				midiout.noteOff(event.channel, event.midinote, event.velocity);
			}
		}
	}

	midiLoop {
		arg midiout;
		var clip;

		clip = this.clip(this.range);

		^Task {
			var i=0;

			loop {
				var event;
				event = clip.wrapAt(i);
				midiout.noteOn(event.channel, event.midinote, event.velocity);
				// need to adapt for new Metre format
				(event[\dur] / this.metre.ticksPerBeat).wait;
				midiout.noteOff(event.channel, event.midinote, event.velocity);
				i = i+1;
			}
		}
	}

	highlight { |startPos, endPos|

		^range = [metreMap.absTicks(startPos).asInteger, metreMap.absTicks(endPos).asInteger];
	}

	highlighted {
		^"% --> %".format(range[0].toPos(metreMap), range[1].toPos(metreMap))
	}

	clip {
		var rangeStart = range[0];
        var rangeEnd = range[1];
		var events, return = [];

		// copy events
		events = this.events.deepCopy;

		return = events.select({
			arg event;

			var eventStart = event[\absTime]; // gets events tick position
			var eventEnd = eventStart + event[\dur];

			(eventStart < rangeEnd) and: (eventEnd > rangeStart);

		}).do({
			arg event;

			var eventStart = event[\absTime]; // gets events tick position
			var eventEnd = eventStart + event[\dur];

			if ((eventStart < rangeStart) && (eventEnd > rangeStart)) {
				event[\dur] = eventEnd - rangeStart;
				event[\absTime] = rangeStart;
			};

			if ((eventStart < rangeEnd) && (eventEnd > rangeEnd)) {
				event[\dur] = rangeEnd - eventStart;
			}
		});

		^Vox.newFromEventsArray(return, metreMap);
		// maybe problematic if I'm clipping, it would need to be a specific metre map...
    }

}