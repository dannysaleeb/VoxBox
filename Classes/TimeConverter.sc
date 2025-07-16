///////////////////////////////////
// Time Conversion Utility Class //
///////////////////////////////////
TimeConverter {
	// posToTicksMetre
	*posToTicksMetre {|pos, metre|
		var
		bars = metre.barsToTicks(pos.bar),
		beats = metre.beatsToTicks(pos.beat),
		divisions = metre.divisionsToTicks(pos.division, pos.beat);

		^[bars, beats, divisions, pos.tick].sum
	}

	// ticksToPosMetre
	*ticksToPosMetre {|ticks, metre|
		var bar, remainder, beat, division, tick;
		var barsResult, beatsResult, divisionsResult;

		barsResult = metre.ticksToBars(ticks);
		bar = barsResult.bars;
		remainder = barsResult.ticks;

		beatsResult = metre.ticksToBeats(remainder);
		beat = beatsResult.beats;
		remainder = beatsResult.ticks;

		divisionsResult = metre.ticksToDivisions(remainder, beat);
		division = divisionsResult.divisions;
		tick = divisionsResult.ticks;

		^Pos(bar, beat, division, tick)
	}

	*posToTicks {|pos, metremap|
		var
		beats = metremap.barsToBeats(pos.bar) + pos.beat,
		divs = metremap.beatsToDivisions(beats) + pos.division;

		^metremap.divisionsToTicks(divs) + pos.tick
	}

	*ticksToPos {|ticks, metremap|
		var
		barInfo = metremap.ticksToBars(ticks),
		bars, ticksLeft, metre,
		beats = 0, divisions = 0, finalTicks = 0,
		ticksPerBeat, divisionsPerBeat,
		divSize,
		i = 0;

		if (barInfo.isNil) { ^nil };

		bars = barInfo.bars;
		ticksLeft = barInfo.ticks;
		metre = metremap.whichRegion(ticks).metre;

		ticksPerBeat = metre.ticksPerBeat;
		divisionsPerBeat = metre.divisionsPerBeat;

		// Resolve into beats
		while { ticksLeft >= ticksPerBeat.wrapAt(i) } {
			ticksLeft = ticksLeft - ticksPerBeat.wrapAt(i);
			beats = beats + 1;
			i = i + 1;
		};

		// Resolve into divisions
		divSize = (ticksPerBeat.wrapAt(i) / divisionsPerBeat.wrapAt(i)).asInteger;
		divisions = ticksLeft div: divSize;
		finalTicks = ticksLeft % divSize;

		^Pos(bars, beats, divisions, finalTicks)
	}

	*normaliseMetre {
		arg pos, metre;

		var totalTicks = this.posToTicksMetre(pos, metre);

		^this.ticksToPosMetre(totalTicks, metre)
	}

	*normalise {
		arg pos, metremap;

		var totalTicks = this.posToTicks(pos, metremap);

		^this.ticksToPos(totalTicks, metremap)
	}

	// needs another look, could be made simpler?
	*midiTimeSigToMetre { |sigArray, tpqn, preferCompound=true, grouping=nil|
		var nn, dd, cc, bb;
		var baseDur, beats, divisions;

		#nn, dd, cc, bb = sigArray;

		baseDur = 1 / (2 ** dd);  // e.g. 1/8 = 0.5 quarter notes

		if (grouping.notNil) {
			// e.g. grouping = [3,2,2] (implied eighths)
			beats = grouping.collect { |g| (baseDur * g) * 4 };
			divisions = grouping.collect { |g| g * 2 };  // 2 divisions per eighth
		} {
			if (preferCompound and: { (nn % 3 == 0) and: (nn > 3) }) {
				var groupCount = nn / 3;
				var beatDur = (baseDur * 3) * 4;
				beats = Array.fill(groupCount, { beatDur });
				divisions = Array.fill(groupCount, { 6 });
			} {
				beats = Array.fill(nn, { baseDur * 4 });
				divisions = Array.fill(nn, { 4 });
			}
		};

		^Metre.new(beats, divisions, tpqn);
	}
}