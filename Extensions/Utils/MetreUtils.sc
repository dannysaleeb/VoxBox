+ Metre {
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
		ticksPerDivision = this.ticksPerDivision, // get array of ticksPerDivision
		allDivisionTicks = this.allDivisionTicks, // get extended array of ticks for each division
		divisionOffset = beatOffset.collect{arg i; this.divisions[i]}.sum; // get total number of divisions to offset

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
}