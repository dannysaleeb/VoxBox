HarmonyMask : VoxModule {
	var <root, <scale, <window, <seed;
	var <chordMap, <chordBias, <voiceLeading, <randomness, <stickiness;
	var <preserveOnsets, <memoryMode;
	var warnedMemoryMode;

	*new {
		|root = 60, scale, window = 12, seed,
		chordMap, chordBias = 1.5, voiceLeading = \balanced,
		randomness = 0.15, stickiness = 0.7, preserveOnsets = true,
		memoryMode = \origin|

		^super.new.init(
			root, scale, window, seed, chordMap, chordBias, voiceLeading,
			randomness, stickiness, preserveOnsets, memoryMode
		);
	}

	root_ { |value| root = value; this.touch }
	scale_ { |value| scale = value; this.touch }
	window_ { |value| window = value; this.touch }
	seed_ { |value| seed = value; this.touch }
	chordMap_ { |value| chordMap = value; this.touch }
	chordBias_ { |value| chordBias = value; this.touch }
	voiceLeading_ { |value| voiceLeading = value; this.touch }
	randomness_ { |value| randomness = value; this.touch }
	stickiness_ { |value| stickiness = value; this.touch }
	preserveOnsets_ { |value| preserveOnsets = value; this.touch }
	memoryMode_ { |value| memoryMode = value; this.touch }

	provenanceSpec {
		^(
			op: \harmonyMask,
			params: (
				root: root,
				scale: VoxProvenance.scaleValue(scale),
				window: window,
				seed: seed,
				chordBias: chordBias,
				voiceLeading: voiceLeading,
				randomness: randomness,
				stickiness: stickiness,
				preserveOnsets: preserveOnsets,
				memoryMode: memoryMode,
				chordMap: VoxProvenance.chordMapValue(chordMap)
			)
		)
	}

	init {
		|root, scale, window, seed, chordMap, chordBias, voiceLeading,
		randomness, stickiness, preserveOnsets, memoryMode|

		this.root = root ? 60;
		this.scale = scale ? Scale.at(\ionian);
		this.window = window ? 12;
		this.seed = seed;
		this.chordMap = chordMap;
		this.chordBias = chordBias ? 1.5;
		this.voiceLeading = voiceLeading ? \balanced;
		this.randomness = randomness ? 0.15;
		this.stickiness = stickiness ? 0.7;
		this.preserveOnsets = preserveOnsets ? true;
		this.memoryMode = memoryMode ? \origin;
		warnedMemoryMode = false;
		^this
	}

	isScaleMidi { |midinote|
		var pc = (((midinote - root) % 12) + 12) % 12;
		^scale.semitones.detect { |semitone|
			semitone.asInteger == pc.asInteger
		}.notNil;
	}

	candidatesFor { |center|
		var low = (center - window).ceil.asInteger;
		var high = (center + window).floor.asInteger;

		^(low..high).select { |note|
			this.isScaleMidi(note)
		}
	}

	assignPitch { |ev|
		var metadata = ev[\metadata] ? Dictionary.new;
		var center = metadata[\grain_origin_midinote] ? ev[\midinote];
		var candidates = this.candidatesFor(center);

		candidates.isEmpty.if {
			^ev[\midinote]
		};

		^candidates.choose
	}

	voiceLeadingWeight {
		if (voiceLeading == \none) { ^0 };
		if (voiceLeading == \loose) { ^0.25 };
		if (voiceLeading == \strong) { ^1.0 };
		^0.55
	}

	normalisedMemoryMode {
		if ((memoryMode == \origin) or: { memoryMode == \global }) {
			^memoryMode
		};

		warnedMemoryMode.if {
			^\origin
		};

		"HarmonyMask: unknown memoryMode %, using \\origin.".format(memoryMode).warn;
		warnedMemoryMode = true;
		^\origin
	}

	memoryKeyFor { |ev, metadata|
		^(this.normalisedMemoryMode == \global).if {
			\global
		} {
			metadata[\grain_origin_midinote] ? ev[\midinote]
		}
	}

	candidatesForChord { |center, chord|
		var candidates;

		if (chord.isNil) {
			^this.candidatesFor(center)
		};

		candidates = chord.candidatesAround(center, window);
		candidates.isEmpty.if {
			^this.candidatesFor(center)
		};

		^candidates
	}

	scoreCandidate { |candidate, ev, chord, previousPitch|
		var metadata = ev[\metadata] ? Dictionary.new;
		var origin = metadata[\grain_origin_midinote] ? ev[\midinote];
		var originDistance = (candidate - origin).abs;
		var identityPenalty = (metadata[\grain_is_onset] == false and: { candidate == ev[\midinote] }).if {
			chord.notNil.if {
				chordBias + 1
			} {
				0
			}
		} {
			0
		};
		var previousDistance = previousPitch.notNil.if {
			(candidate - previousPitch).abs
		} {
			0
		};
		var leapPenalty = (previousDistance > 7).if {
			(previousDistance - 7) * 0.75
		} {
			0
		};
		var registerPenalty = ((candidate - root).abs - 24).max(0) * 0.1;
		var chordToneCost = chord.notNil.if {
			chord.isChordTone(candidate).if {
				(0 - chordBias) * (chord.weight ? 1.0)
			} {
				chordBias * (chord.weight ? 1.0)
			}
		} {
			0
		};
		var stickyCost = previousPitch.notNil.if {
			(candidate == previousPitch).if {
				0 - stickiness
			} {
				stickiness * 0.25
			}
		} {
			0
		};
		var jitter = (randomness > 0).if {
			randomness.rand
		} {
			0
		};

		^originDistance
			+ (previousDistance * this.voiceLeadingWeight)
			+ leapPenalty
			+ registerPenalty
			+ chordToneCost
			+ stickyCost
			+ identityPenalty
			+ jitter
	}

	chooseCandidate { |candidates, ev, chord, previousPitch|
		var best, bestCost;

		candidates.do { |candidate|
			var cost = this.scoreCandidate(candidate, ev, chord, previousPitch);
			if (best.isNil or: { cost < bestCost }) {
				best = candidate;
				bestCost = cost;
			}
		};

		^(
			pitch: best,
			cost: bestCost
		)
	}

	assignPitchForChord { |ev, chord, previousPitch|
		var metadata = ev[\metadata] ? Dictionary.new;
		var center = metadata[\grain_origin_midinote] ? ev[\midinote];
		var candidates = this.candidatesForChord(center, chord);

		candidates.isEmpty.if {
			^(
				pitch: ev[\midinote],
				cost: nil
			)
		};

		^this.chooseCandidate(candidates, ev, chord, previousPitch)
	}

	doProcess { |vox|
		^this.withSeed(seed, {
		var previousByOrigin = Dictionary.new;
		var previousGlobal;

		Vox.new(
			vox.events.collect { |ev|
				var newEv = ev.copy;
				var chord;
				var key;
				var previousPitch;
				var assignment;
				var metadata = ev[\metadata].notNil.if {
					ev[\metadata].copy
				} {
					Dictionary.new
				};
				var isOnset = metadata[\grain_is_onset] == true;
				var shouldPreserveOnset = preserveOnsets and: { isOnset };
				var assigned = shouldPreserveOnset.if {
					ev[\midinote]
				} {
					nil
				};

				chordMap.notNil.if {
					chord = chordMap.atTick(ev[\absTime]);
					key = this.memoryKeyFor(ev, metadata);
					previousPitch = (this.normalisedMemoryMode == \global).if {
						previousGlobal
					} {
						previousByOrigin[key]
					};

					shouldPreserveOnset.not.if {
						assignment = this.assignPitchForChord(ev, chord, previousPitch);
						assigned = assignment[\pitch];
					};

					metadata[\harmony_mask_chord_root] = chord.notNil.if {
						chord.root
					} {
						nil
					};
					metadata[\harmony_mask_is_chord_tone] = chord.notNil.if {
						chord.isChordTone(assigned)
					} {
						nil
					};
					metadata[\harmony_mask_cost] = assignment.notNil.if {
						assignment[\cost]
					} {
						nil
					};

					if (this.normalisedMemoryMode == \global) {
						previousGlobal = assigned;
					} {
						previousByOrigin[key] = assigned;
					};
				} {
					shouldPreserveOnset.not.if {
						assigned = this.assignPitch(ev)
					};
				};

				metadata[\harmony_mask_origin_midinote] = ev[\midinote];
				metadata[\harmony_mask_assigned_midinote] = assigned;
				newEv[\midinote] = assigned;
				newEv[\metadata] = metadata;

				newEv
			},
			vox.metremap,
			vox.label,
			vox.metadata.copy
			)
		})
	}
}
