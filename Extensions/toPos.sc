+ Integer {

	toPos {
		arg map;
		var
		absTicks = this,
		region = map.findRegion(absTicks),
		metreStartTick = region[0],
		metre = region[1],
		barOffset = region[2],
		ticksPosInRegion = absTicks - metreStartTick,

		ticksPerBar = metre.ticksPerBar,
		ticksPerBeat = metre.ticksPerBeat,
		ticksPerDivision = metre.ticksPerDivision,

		bar, beat, division, ticks, overflow;

		// calculate position relative to most recent metre
		bar = ticksPosInRegion div: ticksPerBar;
		overflow = ticksPosInRegion % ticksPerBar;

		beat = overflow div: ticksPerBeat;
		overflow = overflow % ticksPerBeat;

		division = overflow div: ticksPerDivision;
		ticks = overflow % ticksPerDivision;

		^Pos(barOffset + bar, beat, division, ticks);
	}

}