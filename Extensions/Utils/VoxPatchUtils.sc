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

	>!> { |splitSpec|
        var plug = this.respondsTo(\out).if { this.out } { this };
        plug.isKindOf(VoxPlugMulti).if {
            ^plug.split(splitSpec);
        } {
            "Tried to split non-VoxPlugMulti".warn;
            ^nil;
        };
    }

	>=> { |pair|
        var varSymbol = pair.asSymbol;
        varSymbol.envirPut(this);
        ^this;
    }
}

+ VoxPatcher {
	>@> { |routingList|
        var outPlugs = routingList.collect { |pair|
            var key = pair.key, module = pair.value;
            var branch = this.at(key);
			var out;
			// this seems dodgy
			module.input = branch;
			("✅ Input set from >@> on %: source = %".format(module.label ? module.class, branch)).postln;
			"calling .out on % now...".format(module.label ? module.class).postln;
			"result will be: %".format(module.out).postln;
            out = module.out;
			out.isKindOf(VoxPlugMulti).if { out.asArray } { out };
        };

		"outPlugs is: %".format(outPlugs.asArray.flat).postln;

        ^VoxPlugMulti.new(outPlugs.asArray.flat);
    }
}

// I think might need a better connection design, to keep track of flow of parts, just an ordering system for voices ... sort by their voiceID or something ... 