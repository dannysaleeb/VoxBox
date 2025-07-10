+ MetreRegion {

	bars {
		var regionTicks = this.metre.regionSize(this);

		^this.metre.ticksToBars(regionTicks)
	}

	shift {
		arg inval;

		^MetreRegion(this.start + inval, this.metre);
	}

	== {
		arg other;

		var startEquality = this.start == other.start;
		var metreEquality = this.metre == other.metre;

		if (other.isKindOf(MetreRegion).not) { ^false }

		^(startEquality && metreEquality)
	}

	< {|other|
		^this.start < other.start
	}

	> {|other|
		^this.start > other.start
	}
}

