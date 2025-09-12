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
            this.input.out[key]
        } {
            "VoxBuilder: no input set".warn;
			nil;
        };

        vox.notNil.if {
            transform.notNil.if {
                ^Vox.new(
                    transform.value(vox),
                    vox.label,
                    vox.origin
                )
            } {
                ^vox
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

		if (spec.isKindOf(Symbol) or: { spec.isKindOf(String) }) {
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
        var voxes = [];
		var multis, voxesFromMultis;

        // Get union of routed + source keys
        var allKeys = routes.keys ++ sourceOut.keys;
        allKeys.do { |key|
            var route = routes[key];
			var vox;

			route.notNil.if {
				vox = route.out
			};

            if (route.isNil and: { allowFallback }) {
                vox = sourceOut[key];
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

		voxes.postln;


        ^VoxMulti.new(voxes, sourceOut.metremap, sourceOut.label);
    }
}
