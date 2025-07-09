+ MetreRegion {

	bars {
		var regionTicks = this.metre.regionSize(this);

		^this.metre.ticksToBars(regionTicks)
	}

	shift {
		arg inval;

		^MetreRegion(this.start + inval, this.metre);
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

