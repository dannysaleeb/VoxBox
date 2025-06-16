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
	var <>regions;
	// can this be private?

	*new {
		^super.new.init
	}

	init {
		regions = List.new;
		^this;
	}

	add {|region|
		var matchIndex, insertionIndex;

		if (regions.isEmpty) { regions.add(region); ^this };

		// matching
		matchIndex = this.matchingRegionIndex(region);
		if (matchIndex.notNil) {
			regions.put(matchIndex, region);
			this.pushDownstream(region);
			^this
		};

		if (this.isEarliest(region)) {
			var nextBarline, offset;
			// add region as first region
			regions.addFirst(region);
			this.pushDownstream(region);
			^this
		};

		// in between or at end
		insertionIndex = this.insertionIndex(region.start);

		if (this.isBarAligned(region)) {
			regions.put(insertionIndex, region);
			this.pushDownstream(region);
			^this
		};

		this.snapToLastBarline(region);
		matchIndex = this.matchingRegionIndex(region);
		if (matchIndex.notNil) {
			regions.put(matchIndex, region);
			this.pushDownstream(region);
			^this
		};

		regions.insert(insertionIndex, region);
		this.pushDownstream(region);
		^this

	}

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
			^remBars
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