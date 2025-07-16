+ Metre {
	////////////////////////
	// CONVERSION HELPERS //
	////////////////////////
	ticksPerBar {
		^this.ticksPerBeat.sum.asInteger
    }

	ticksPerBeat {
		^beats.collect({ |beat| (beat * tpqn).round.asInteger })
	}

	ticksPerDivision {
		^beats.collect({
			arg beat, i;
			var beatTicks = (beat * tpqn).round.asInteger;
			(beatTicks / divisions[i]).round.asInteger
		})
    }

	divisionsPerBar {
		^divisions.sum.asInteger
	}

	divisionsPerBeat {
		^divisions.copy
	}

	allDivisionTicks {
		var ticksPerDivision = this.ticksPerDivision;
		^divisions.collect({|divs, i| ticksPerDivision[i].dup(divs) }).flat;
	}

	beatsPerBar {
		^beats.size
	}

	whichBeat {|ticks|
		var beats = this.ticksToBeats(ticks).beats;
		^beats % this.beatsPerBar;
	}

	// to ticks
	barsToTicks {|bars|
		^(bars * this.ticksPerBar).round.asInteger
	}

	// allow float output in case of fractional bars input
	barsToBeats {|bars|
		^bars * this.beatsPerBar
	}

	beatsToTicks {|numBeats, offset=0|

		var
		fullBeats = numBeats.floor.asInteger,
		partial = numBeats - fullBeats,
		ticksPerBeat = this.ticksPerBeat;

		var fullTicks = fullBeats.collect({ |i|
			ticksPerBeat.wrapAt(i + offset)
		}).sum; // will always be integer

		// round and cast as integer to force integer output
		var partialTicks = (partial * ticksPerBeat.wrapAt(fullBeats + offset)).round.asInteger;

		^fullTicks + partialTicks
	}

	beatsToDivisions {|numBeats, offset=0|

		var
		fullBeats = numBeats.floor.asInteger,
		partial = numBeats - fullBeats,
		ticksPerBeat = this.ticksPerBeat;

		var fullDivisions = fullBeats.collect({ |i|
			divisions.wrapAt(i + offset)
		}).sum;

		var partialDivisions = (partial * divisions.wrapAt(fullBeats + offset)).round.asInteger;

		^fullDivisions + partialDivisions
	}

	divisionsToTicks {|divs, beatOffset=0|
		var
		ticksPerDivision = this.ticksPerDivision,
		allDivisionTicks = this.allDivisionTicks,
		// ensure beatOffset is valid integer index
		beatOffsetInt = beatOffset.floor.asInteger,
		divisionOffset = beatOffsetInt.collect{arg i; this.divisions.wrapAt(i)}.sum;

		if (divs.isInteger.not) {
			("Invalid input [%]: divs must be positive integer".format(divs)).throw;
		}

		^divs.collect({|division| allDivisionTicks.wrapAt(division + divisionOffset)}).sum
	}

	// from ticks
	ticksToBars {|ticks|
		var ticksPerBar = this.ticksPerBar;
		^(bars: ticks div: ticksPerBar, ticks: (ticks % ticksPerBar).round.asInteger)
	}

	ticksToBeats {|totalTicks|
		var
		remainingTicks = totalTicks.round.asInteger,
		ticksPerBeat = this.ticksPerBeat,
		i = 0,
		counter = 0;

		// it would be easier if this accepted a beat offset ...
		// this starts on beat 1

		while { (remainingTicks - ticksPerBeat.wrapAt(i)).isNegative.not } {
			remainingTicks = remainingTicks - ticksPerBeat.wrapAt(i);
			counter = counter + 1;
			i = i + 1;
		};

		^(beats: counter, ticks: remainingTicks.round.asInteger)
	}

	ticksToDivisions {|totalTicks, beatOffset=0|
		var
		remainingTicks = totalTicks.round.asInteger,
		count = 0,
		allDivisionTicks = this.allDivisionTicks,
		// whatever beat offset index is, collect lots of divisions up to that index and sum
		divisionOffset = beatOffset.collect{arg i; divisions[i]}.sum;

		loop {
			allDivisionTicks.size.do({ // all individual numbers of ticks per division for full bar
				arg i;
				var ticks = allDivisionTicks.wrapAt(i + divisionOffset);

				if (remainingTicks >= ticks) { // if enough ticks
					remainingTicks = remainingTicks - ticks;
					count = count + 1;
				} { ^(divisions: count, ticks: remainingTicks) }
			})
		}
	}

	// other helpers
	lastBarline {|tick|
		tick = tick.round.asInteger;
		^tick - (tick % this.ticksPerBar)
	}

	nextBarline {|tick|
		var ticksPerBar = this.ticksPerBar;
		var rem;

		tick = tick.round.asInteger;
		rem = tick % ticksPerBar;

		if (rem == 0) { ^tick };

		^tick + (ticksPerBar - rem)
	}

	nearestBarline {|tick|
		var ticksPerBar = this.ticksPerBar;
		var rem, diff;

		tick = tick.round.asInteger;

		rem = tick % ticksPerBar;

		if (rem == 0) { ^tick };

		diff = ticksPerBar - rem;
		if (diff < rem) { ^tick + diff } { ^tick - rem }
	}
}