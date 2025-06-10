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

	asString {
		^"Metre(%, %)".format(beats, divisions)
	}
}

Region {
	var tick, bar, metre;

	*new {
		arg tick, bar, metre;

		^super.newCopyArgs(tick, bar, metre)
	}

	tick { ^tick }
	bar { ^bar }
	metre { ^metre }

	set_tick { |inval| tick = inval }
	set_bar { |inval| bar = inval }
	set_metre { |inval| metre = inval }

	shift {|inval|
		this.set_tick(this.tick + inval);
	}

	asString {
		^"Region(tick %:  || bar: % || metre: %)".format(tick, bar, metre)
	}

	== {|other|
		^this.tick == other.tick
	}

	< {|other|
		^this.tick < other.tick
	}

	> {|other|
		^this.tick > other.tick
	}
}

//////////////////////////
// MetreMap
//////////////////////////
MetreMap {
	var <>entries;

	// I don't want entries adjusted outside of the class, so this should be private...

	*new {
		^super.new.init
	}

	init {
		entries = List.new;
		^this;
	}

	add {|region|

		var prior = this.whichRegion(region.tick);
		var insertionIndex = this.insertionIndex(region.tick);
		var tickOffset = 0;
		var matching;
		var remaining;
		var adjusted;

		// if there's nothing in entries already, just add it and return
		if (entries.isEmpty) {

			region.set_bar(0);
			entries.add(region);

			^this
		};

		// does it match any existing entries?
		matching = this.matchingRegion(region);

		if (matching.notNil) {
			var index = this.matchingRegionIndex(region);
			region.set_bar(matching.bar);
			entries.put(index, region);
			this.updateStarts;
			^this
		};

		// if prior region is nil, check for match on tick, add region, update starts
		if (this.isEarliest(region)) {
			region.set_bar(0);
			entries.addFirst(region);
			this.updateStarts;
			^this
		};

		// check added metre is being added on barline, record adjusted tickOffset if required
		if (this.isBarAligned(region).not) {
			this.snapToLastBarline(region);
			/*var adjusted = prior.tick + this.lastBarline(region.tick).asInteger;
			tickOffset = adjusted - region.tick;
			region.set_tick(adjusted);*/
		};

		// I need to check if the last barline matches the lastRegion, basically

		// this might be cleaner? More efficient -- come back to it.
		/*if (entries[insertionIndex - 1] == region.tick) {
			entries.put(insertionIndex - 1, region);
			this.updateBars;
			^this
		};*/

		// insert and update bars values
		entries.insert(insertionIndex, region);

		this.updateStarts;
		^this

		// get downstream regions & update start values accordingly
		// remaining = entries.select({|entry| entry.tick > region.tick});
		// if (remaining.notNil) { remaining.do(_.shift(tickOffset)) };
		//
		// this.updateBars;
	}

	sortEntries {
		entries.sort({ arg a, b; a.tick < b.tick })
	}

	updateStarts {

		if (entries.notNil) {
			entries.first.set_tick(0)
		};

		entries.size.do({
			arg i;
			var current, next, bars;
			var adjusted, tickOffset;

			next = entries[i+1];
			current = entries[i];

			if (next.notNil) {
				bars = current.metre.ticksToBars(next.tick - current.tick);

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
		^entries.select({|entry| entry.tick <= tickVal }).last
	}

	insertionIndex {|tickVal|
		var region = this.whichRegion(tickVal);
		var regionIndex = entries.detectIndex({arg entry; entry == region}) ?? -1;
		^regionIndex + 1
	}

	updateBars {
		entries.do({
			arg entry, i;
			if (entries[i+1].notNil) {
				var bars = ((entries[i+1].tick - entry.tick) / entry.metre.ticksPerBar).floor;
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
		var difference = ticks - region.tick;

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

		var prior = this.whichRegion(region.tick);

		if (region.tick == 0) { ^true };

		if (prior.notNil) {
			var difference = region.tick - prior.tick;
			^region.tick == this.lastBarline(region.tick)
		} {
			^false
		}
	}

	regionSize {|region|
		var index;
		index = entries.detectIndex({arg entry; entry.tick == region.tick});
		if (entries[index+1].notNil) {
			^entries[index+1].tick - entries[index].tick
		} { ^0 }
	}

	matchingRegion {|region|
		entries.do({
			arg entry;
			if (entry == region) { ^entry } { ^nil }
		})
	}

	matchingRegionIndex {|region|
		^entries.detectIndex({arg entry; entry == region})
	}

	isEarliest {|region|
		^region < entries.first
	}

	snapToLastBarline {|region|
		region.set_tick(this.lastBarline(region.tick).asInteger);
		^region
	}

	listEntries {
		entries.dopostln;
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