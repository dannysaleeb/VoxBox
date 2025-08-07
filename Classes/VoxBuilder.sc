VoxProxy : VoxNode {
    var <key, <transform;

    *new { |key, transform|
        ^super.new.init(key, transform)
    }

    init { |k, t|
        key = k;
        transform = t;  // optional function: Vox → Vox
        ^this
    }

    out {
        var plug = this.input.notNil.if {
            this.input.out[key]
        } {
            "VoxBuilder: no input set".warn;
			nil;
        };

        plug.notNil.if {
            transform.notNil.if {
                ^VoxPlug.new(
                    transform.value(plug),
                    plug.label,
                    plug.origin
                )
            } {
                ^plug
            }
        }
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

		if (spec.isKindOf(Symbol)) {
			key = spec;
			proxy = VoxProxy.new(key);
			chain = proxy;
		} {
			key = spec.sourceKey;
			chain = spec.chain;

			// Create a VoxBuilder for the key
			proxy = VoxProxy.new(key);

			// Wire it into the first node in the chain
			proxy >>> chain.headNode;  // Must define or require `.headNode`
		};

		// Wire input into the builder (so it extracts the correct voice)
		input >>> proxy;

		// Store the final chain output
		routes[key] = chain;
	}

    addFromUpstream { |keys|
        keys.do { |key|
            this.add(key);
        }
    }

    out {
        var sourceOut = input.out;
        var plugs = [];

        // Get union of routed + source keys
        var allKeys = routes.keys ++ sourceOut.keys;
        allKeys.do { |key|
            var route = routes[key];
			var plug;

			route.postln;

			route.notNil.if {
				plug = route.out
			};

            if (route.isNil and: { allowFallback }) {
                plug = sourceOut[key];
            };

            if (plug.notNil) {
                plugs = plugs.add(plug);
            };
        };

		plugs.postln;

        ^VoxPlugMulti.new(plugs, sourceOut.metremap, sourceOut.label);
    }
}
