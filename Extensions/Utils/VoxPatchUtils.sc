+ Object {

	isPlug { ^this.isKindOf(VoxPlug) or: { this.isKindOf(VoxPlugMulti) } }

    >>> { |target|
		// check RH has input setter (VoxModule, Vox, VoxPlayer)
		// if it's a plug, warn ...
        target.respondsTo(\input_).if {
			// first check target has input
			this.respondsTo(\out).if {
				// if this has out, good
				target.input = this;
			} {
				// else check if plug
				if (this.isPlug) {
					"😬 Plug from % directly connected to %; live update in playback might be affected.".format(this.tryPerform(\label) ? this.class, target.label).warn;
					target.input = this;
				} {
					// if neither, invalid, error
					"❌ Cannot patch % into %; invalid input."
					.format(this.class, target.tryPerform(\label) ? target.class).warn;
				}
			};
        } {
			// no input, warn (or error?)
            "❌ Cannot patch into %, it has no .input_ method.".format(target.class).warn;
        };

		// returns RH after assigning input, so can do .out ... or chain onwards
        ^target
    }

	>>< { |splitSpec|
		// if respond to .out, great
        var input = this.respondsTo(\out).if {
			this
		} {
			// if a plug, warn about liveness
			if (this.isPlug) {
				"😬 >>< (.split) called directly on plug from %; live update in playback might be affected.".format(this.tryPerform(\label) ? this.class).warn;
				this
			} {
				// if neither, invalid, error
				"❌ Cannot split %; invalid type."
				.format(this.class).warn;
				^nil;
			}
		};

		^input.split(splitSpec) // implement .split on VoxMulti, VoxModule ...
    }

	>>= { |pair|
        var varSymbol = pair.asSymbol;
        varSymbol.envirPut(this);
        ^this;
    }

	>>* {
		// implement selecting from VoxMulti as well as VoxPatcher (either by autoID or label)
		// implement as VoxPlug (VoxPlugMulti) .select method?
	}

	>>/ {
		// implement clipping also from a plug ...
		// VoxPlug .clip
	}

	>>+ {
		// combine LHS VoxPlug with RHS Vox or VoxMulti or VoxPlug (yields VoxPlugMulti whatever happens, which can feed into new VoxMulti if required)
		// so implement as VoxPlug .merge
	}
}

+ VoxPatcher {
	>>@ { |routingList|
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