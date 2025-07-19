+ Object {
    >>> { |other|
		// check RH has input setter (VoxModule, Vox, VoxPlayer)
        other.respondsTo(\input_).if {
            other.input = this;
        } {
            "Cannot patch into %, it has no .input_ method.".format(other.class).warn;
        };

		// returns RH after assigning input, so can do .out ... or chain onwards
        ^other
    }
}

// check if 