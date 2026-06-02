VoxProxy : VoxNode {
    var <key, <transform;

    *new { |key, transform|
        ^super.new.init(key, transform)
    }

    init { |k, t|
        key = k;
        transform = t;  // optional function: Box -> Box
        ^this
    }

    out {
        var vox = this.input.notNil.if {
            this.input.out.at(key)
        } {
            "VoxProxy: no input set".warn;
			nil;
        };

        vox.notNil.if {
            transform.notNil.if {
                ^transform.value(vox)
            } {
                ^vox
            }
        }
    }
}

VoxSelector : VoxNode {
	var <key;

	*new { |source, key|
		^super.new.init(source, key)
	}

	init { |source, keyArg|
		input = source;
		key = keyArg;
		^this
	}

	out {
		var vox = input.out;

		if (vox.isKindOf(VoxMulti)) {
			^vox.at(key)
		};

		"VoxSelector: expected VoxMulti, got %".format(vox.class).warn;
		^nil
	}
}

VoxClipper : VoxNode {
	var <rangeArg;

	*new { |source, range|
		^super.new.init(source, range)
	}

	init { |source, range|
		input = source;
		rangeArg = range;
		^this
	}

	range_ { |range|
		rangeArg = range;
		this.touch;
	}

	out {
		var vox = input.out;
		var range;

		if (vox.isKindOf(Vox)) {
			range = TimeRange.from(rangeArg, vox.metremap);
			^Vox.new(
				Box.clippedEvents(vox.events, range),
				vox.metremap,
				vox.label,
				vox.metadata,
				this
			)
		};

		if (vox.isKindOf(VoxMulti)) {
			range = TimeRange.from(rangeArg, vox.metremap);
			^VoxMulti.new(
				vox.asArray.collect { |part|
					Vox.new(
						Box.clippedEvents(part.events, range),
						part.metremap,
						part.label,
						part.metadata,
						this
					)
				},
				vox.metremap,
				vox.label,
				vox.metadata,
				this
			)
		};

		"VoxClipper: expected Vox or VoxMulti, got %".format(vox.class).warn;
		^nil
	}
}

VoxRouter : VoxNode {
    var <routes, <allowFallback = true;

    *new { |source|
        ^super.new.init(source)
    }

    init { |sourceNode|
        routes = IdentityDictionary.new;
        input = sourceNode;
        ^this
    }

	    add { |spec|
		var key, chain, proxy;

		if (spec.isKindOf(Symbol) or: { spec.isKindOf(String) }) {
			key = spec;
			proxy = VoxProxy.new(key);
			chain = proxy;
		} {
			key = spec.sourceKey;
			chain = spec.chain;

			// Create a VoxProxy for the key
			proxy = VoxProxy.new(key);

			// Wire it into the first node in the chain
			proxy >>> chain.headNode;  // Must define or require `.headNode`
		};

		// Wire input into the proxy (so it extracts the correct voice)
		input >>> proxy;

		// Store the final chain output
			routes[key] = chain;
			this.touch;
		}

	effectiveRevision {
		^[
			super.effectiveRevision,
			routes.values.collect { |route| route.effectiveRevision }.sort
		].hash
	}

    addFromUpstream { |keys|
        keys.do { |key|
            this.add(key);
        }
    }

    out {
        var sourceOut = input.out;
        var voxes = [];
		var multis, voxesFromMultis;

        // Get union of routed + source keys
        var allKeys = (routes.keys ++ sourceOut.keys).asSet.asArray;
        allKeys.do { |key|
            var route = routes[key];
			var vox;

			route.notNil.if {
				vox = route.out
			};

            if (route.isNil and: { allowFallback }) {
                vox = sourceOut.at(key);
            };

            if (vox.notNil) {
                voxes = voxes.add(vox);
            };
        };

		multis = voxes.select({
			arg vox;
			vox.isKindOf(VoxMulti);
		});

		voxesFromMultis = multis.collect({
			arg multi;
			multi.voxes.values
		});

		voxes = voxes.reject { arg vox; vox.isKindOf(VoxMulti) } ++ voxesFromMultis.flatten;
        ^VoxMulti.new(voxes, sourceOut.metremap, sourceOut.label, sourceOut.metadata, this);
    }
}
