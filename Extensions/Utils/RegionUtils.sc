+ Region {
	shift {|inval|
		this.set_start(this.start + inval);
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

