//////////////////////////
// ScorePosition
//////////////////////////
Pos {
    var <>bar, <>beat, <>division, <>ticks;

    *new { |bar=0, beat=0, division=0, ticks=0|

        ^super.newCopyArgs(bar, beat, division, ticks);
    }

	toTicks { |metre|

        var ticksPerBeat = metre.ticksPerBeat;
        var ticksPerDivision = metre.ticksPerDivision;

        ^((bar * metre.beatsPerBar + beat) * ticksPerBeat)
        + (division * ticksPerDivision)
        + ticks;
    }

    toBeats { |metre|

        ^this.toTicks(metre) / metre.ticksPerBeat;
    }

    normalize { |metre|

        var totalTicks = this.toTicks(metre);
        var ticksPerBar = metre.ticksPerBar;
        var ticksPerBeat = metre.ticksPerBeat;
        var ticksPerDivision = metre.ticksPerDivision;

		var bar, rem, beat, division, ticks;

        bar = (totalTicks / ticksPerBar).floor;
        rem = totalTicks % ticksPerBar;

        beat = (rem / ticksPerBeat).floor;
        rem = rem % ticksPerBeat;

        division = (rem / ticksPerDivision).floor;
        ticks = (rem % ticksPerDivision).round;

        ^Pos(bar, beat, division, ticks);
    }

    == { |other|
		^[bar, beat, division, ticks] == [other.bar, other.beat, other.division, other.ticks]
	}

	+ {
		arg other;

		^Pos(
			this.bar + other.bar,
			this.beat + other.beat,
			this.division + other.division,
			this.ticks + other.ticks
		)
	}

	- {
		arg other;

		^Pos(
			this.bar - other.bar,
			this.beat - other.beat,
			this.division - other.division,
			this.ticks - other.ticks
		)
	}

    asString {
        ^"Pos[% % % %]".format(bar.asInteger, beat.asInteger, division.asInteger, ticks.asInteger);
    }
}

//////////////////////////
// Metre
//////////////////////////
Metre {
    var <>tpqn, <>beatLengthQN, <>beatsPerBar, <>divisionsPerBeat;

    *new {
		arg tpqn=960, beatLengthQN=1.0, beatsPerBar=4, divisionsPerBeat=4;

        ^super.newCopyArgs(tpqn, beatLengthQN, beatsPerBar, divisionsPerBeat);
    }

    ticksPerBeat {
        ^this.tpqn * this.beatLengthQN;
    }

    ticksPerDivision {
        ^this.ticksPerBeat / this.divisionsPerBeat;
    }

    ticksPerBar {
        ^this.ticksPerBeat * this.beatsPerBar;
    }

	asString {
		^"A Metre: % beats / % divisions".format(this.beatsPerBar, this.divisionsPerBeat)
	}

    // Optional swing offset array or divisionOffsets could go here
}

//////////////////////////
// MetreMap
//////////////////////////
MetreMap {
	var <>entries; // array of [absTicks start, metre, barOffset]

	*new {
		^super.new.init
	}

	init {
		entries = List.new;
		^this;
	}

	/*add {
		arg absTicks, metre; // position in absTicks, and a Metre
		var last = entries.last; // the most recent metre entry
		var barOffset = 0; // how many bars so far?

		if (last.notNil) { // as long as there is an entry already
			var lastStart = last[0];
			var lastMetre = last[1];
			var lastBars = ((absTicks - lastStart) / lastMetre.ticksPerBar).floor;
			barOffset = last[2] + lastBars; // calculates number of bars in last region
		};

		entries.add([absTicks, metre, barOffset]);
		entries = entries.sortBy(0);
	}

	findRegion { |absTicks|
        ^entries.select({ |e| e[0] <= absTicks }).last;
    }*/

	add { |absTicks, metre|

		var
		insertIndex = entries.detectIndex({ |entry| absTicks < entry[0] }) ?? entries.size,
		barOffset = 0,

		last, lastStart, lastMetre, lastOffset, barsSinceLast, expectedBarline, next,

		cumulativeBars, i;

		if (insertIndex > 0) {
			// get last entry
			last = entries[insertIndex - 1];
			// and unpack it
			#lastStart, lastMetre, lastOffset = last;

			// additional bars up to point of new metre entry
			barsSinceLast = ((absTicks - lastStart) / lastMetre.ticksPerBar).floor;

			// calculate the expected barline in ticks, to check bar alignment
			expectedBarline = (barsSinceLast * lastMetre.ticksPerBar) + lastOffset;

			if (expectedBarline != absTicks) {
				"Metre change at % was snapped to start of bar at %".format(absTicks, expectedBarline).warn
			};

			// change absTicks value to last valid barline position
			absTicks = expectedBarline;

			// calculate total cumulative offset for this metre
			barOffset = lastOffset + barsSinceLast;
		};

		// insert new entry
		entries.insert(insertIndex, [absTicks, metre, barOffset]);

		// check if new region adds up w
		next = entries[insertIndex + 1];
		if (next.notNil) {
			var overflow = (entries[insertIndex + 1][0] - absTicks) % metre.ticksPerBar;

			if (overflow != 0) {
				// got to check how this accesses Vox ...
			}
		};

		// recalculate offsets for any downstream entries
		cumulativeBars = barOffset;
		i = insertIndex;

		while { i < entries.size - 1 } {
			var
			current = entries[i],
			next = entries[i+1],

			currentStart = current[0],
			currentMetre = current[1],
			nextStart = next[0],

			barsInRegion = ((nextStart - currentStart) / currentMetre.ticksPerBar ).floor;
			cumulativeBars = cumulativeBars + barsInRegion;

			entries[i+1] = [nextStart, next[1], cumulativeBars];

			i = i+1;
		}

		// need to check bars add up, too.
	}

	absTicks { |pos|

		var
		region = entries.reverse.detect({|e| pos.bar >= e[2] }),
		regionStartTick = region[0],
		metre = region[1],
		regionBarOffset = region[2],

		localBar = pos.bar - regionBarOffset,
		localPos = Pos(localBar, pos.beat, pos.division, pos.ticks);

		^regionStartTick + localPos.toTicks(metre);

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

	quantise {}
	// quantise can be a module

}