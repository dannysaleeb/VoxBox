/////////////////////////////////////////////////////
// Rolling synth and MIDI playback for live VoxBox //
/////////////////////////////////////////////////////
VoxPlayer {
	var source, clock, task;
	var <lookahead = 0.05, <pollInterval = 0.025, <channelMap;
	var rendered, renderedRevision, scheduleGeneration = 0, scheduled;
	var eventIndex, startTick, endTick, cycleTicks, renderedTPQN;
	var <indexBuildCount = 0, <lastWindowVisitCount = 0;
	var activeMIDI, activeSynths, midiout, instrument = \default;

	*new { |source, clock|
		^super.new.init(source, clock)
	}

	init { |sourceArg, clockArg|
		source = sourceArg;
		clock = clockArg ? TempoClock.default;
		scheduled = Dictionary.new;
		eventIndex = [];
		activeMIDI = Dictionary.new;
		activeSynths = IdentitySet.new;
		^this
	}

	lookahead_ { |beats|
		lookahead = beats.max(0.001);
	}

	pollInterval_ { |beats|
		pollInterval = beats.max(0.001);
	}

	channelMap_ { |map|
		channelMap = map;
	}

	scheduledCount { ^scheduled.size }

	activeMIDICount { ^activeMIDI.values.sum ? 0 }

	sourceRevision {
		^source.respondsTo(\effectiveRevision).if {
			source.effectiveRevision
		} {
			source.hash
		}
	}

	refreshRendered {
		var revision = this.sourceRevision;

		if (renderedRevision.isNil or: { renderedRevision != revision }) {
			rendered = source.respondsTo(\out).if { source.out } { source };
			renderedRevision = revision;
			this.rebuildEventIndex;
			scheduleGeneration = scheduleGeneration + 1;
			scheduled.clear;
		};

		^rendered
	}

	rebuildEventIndex {
		var parts;

		parts = [];
		startTick = nil;
		endTick = nil;
		cycleTicks = nil;
		renderedTPQN = nil;

		if (rendered.isNil) {
			eventIndex = parts;
			indexBuildCount = indexBuildCount + 1;
			^this
		};

		if (rendered.isKindOf(Vox)) {
			parts = rendered.events.collect { |event, index|
				(label: rendered.label, event: event, index: index, metremap: rendered.metremap)
			}
		};

		if (rendered.isKindOf(VoxMulti)) {
			parts = rendered.asArray.collect { |part|
				part.events.collect { |event, index|
					(label: part.label, event: event, index: index, metremap: part.metremap)
				}
			}.flatten
		};

		if (
			rendered.isKindOf(Vox).not.and({
				rendered.isKindOf(VoxMulti).not
			})
		) {
			"VoxPlayer: expected Vox, VoxMulti or a compatible live source.".warn;
			parts = [];
		};

		eventIndex = parts.sort({ |left, right|
			left[\event][\absTime] < right[\event][\absTime]
		});

		if (eventIndex.notEmpty) {
			startTick = eventIndex.first[\event][\absTime];
			endTick = eventIndex.collect { |part|
				part[\event][\absTime] + part[\event][\dur]
			}.maxItem;
			cycleTicks = (endTick - startTick).max(1);
			renderedTPQN = rendered.metremap.tpqn;
		};

		indexBuildCount = indexBuildCount + 1;
		^this
	}

	eventParts {
		this.refreshRendered;
		^eventIndex
	}

	firstIndexAtOrAfter { |tick|
		var low = 0, high = eventIndex.size;

		while { low < high } {
			var middle = ((low + high) / 2).floor.asInteger;
			if (eventIndex[middle][\event][\absTime] < tick) {
				low = middle + 1
			} {
				high = middle
			}
		};
		^low
	}

	partsBetween { |windowStartTick, windowEndTick|
		var index, parts;

		lastWindowVisitCount = 0;
		if (eventIndex.isEmpty) { ^[] };

		index = this.firstIndexAtOrAfter(windowStartTick);
		parts = List.new;

		while {
			index < eventIndex.size and: {
				eventIndex[index][\event][\absTime] <= windowEndTick
			}
		} {
			parts.add(eventIndex[index]);
			lastWindowVisitCount = lastWindowVisitCount + 1;
			index = index + 1;
		};

		^parts
	}

	channelFor { |part|
		var mapped = channelMap.notNil.if { channelMap[part[\label]] } { nil };
		^mapped ? part[\event][\channel] ? 0
	}

	startMIDI { |part|
		var event = part[\event];
		var channel = this.channelFor(part);
		var key = [channel, event[\midinote]];

		midiout.noteOn(channel, event[\midinote], event[\velocity] ? 90);
		activeMIDI[key] = (activeMIDI[key] ? 0) + 1;
		clock.sched(event[\dur].toMIDIBeats(part[\metremap].tpqn), {
			this.stopMIDINote(channel, event[\midinote], event[\velocity] ? 90);
			nil
		});
	}

	stopMIDINote { |channel, note, velocity = 90|
		var key = [channel, note];
		var count = activeMIDI[key] ? 0;

		if (count > 0) {
			midiout.noteOff(channel, note, velocity);
			if (count <= 1) {
				activeMIDI.removeAt(key)
			} {
				activeMIDI[key] = count - 1
			}
		}
	}

	startSynth { |part|
		var event = part[\event];
		var synth = Synth(instrument, [\freq, event[\midinote].midicps]);

		activeSynths.add(synth);
		clock.sched(event[\dur].toMIDIBeats(part[\metremap].tpqn), {
			synth.release(1.5);
			activeSynths.remove(synth);
			nil
		});
	}

	schedulePart { |part, cycle, cycleTicks, startTick, startBeat, mode|
		var event = part[\event];
		var offsetTicks = event[\absTime] - startTick;
		var eventBeat = startBeat + ((cycle * cycleTicks) + offsetTicks).toMIDIBeats(rendered.metremap.tpqn);
		var key = "%:%:%:%:%:%".format(
			scheduleGeneration, cycle, part[\label], part[\index], event[\absTime], event[\midinote]
		);
		var generation = scheduleGeneration;

		if (scheduled[key].isNil) {
			scheduled[key] = true;
			clock.schedAbs(eventBeat.max(clock.beats), {
				scheduled.removeAt(key);
				if (generation == scheduleGeneration) {
					if (mode == \midi) {
						this.startMIDI(part)
					} {
						this.startSynth(part)
					}
				};
				nil
			});
		}
	}

	makeRollingTask { |mode = \synth, shouldLoop = false|
		^Task({
			var keepRunning = true;
			var startBeat = clock.beats + pollInterval;

			while { keepRunning } {
				var parts;

				this.refreshRendered;

				if (eventIndex.isEmpty) {
					pollInterval.wait;
				} {
					var now = clock.beats;
					var horizon = now + lookahead;
					var elapsedTicks = ((now - startBeat) * renderedTPQN).max(0);
					var firstCycle = (elapsedTicks / cycleTicks).floor.asInteger;
					var lastCycle = (((horizon - startBeat) * renderedTPQN) / cycleTicks)
						.ceil.asInteger.max(firstCycle);

					(firstCycle..lastCycle).do { |cycle|
						if (shouldLoop or: { cycle == 0 }) {
							var sourceWindowStart = startTick + (
								((now - startBeat) * renderedTPQN) - (cycle * cycleTicks)
							);
							var sourceWindowEnd = startTick + (
								((horizon - startBeat) * renderedTPQN) - (cycle * cycleTicks)
							);

							parts = this.partsBetween(sourceWindowStart, sourceWindowEnd);
							parts.do { |part|
								var eventBeat = startBeat + (
									(cycle * cycleTicks) + (part[\event][\absTime] - startTick)
								).toMIDIBeats(renderedTPQN);

								if (eventBeat >= now and: { eventBeat <= horizon }) {
									this.schedulePart(part, cycle, cycleTicks, startTick, startBeat, mode);
								}
							}
						}
					};

					if (shouldLoop.not and: {
						now > (startBeat + cycleTicks.toMIDIBeats(renderedTPQN) + lookahead)
					}) {
						keepRunning = false
					} {
						pollInterval.wait
					}
				}
			}
		}, clock)
	}

	play { |quant|
		task = this.makeRollingTask(\synth, false);
		task.play(clock, quant: quant);
	}

	loop { |quant|
		task = this.makeRollingTask(\synth, true);
		task.play(clock, quant: quant);
	}

	playMIDI { |midioutArg, quant|
		midiout = midioutArg;
		task = this.makeRollingTask(\midi, false);
		task.play(clock, quant: quant);
	}

	loopMIDI { |midioutArg, quant|
		midiout = midioutArg;
		task = this.makeRollingTask(\midi, true);
		task.play(clock, quant: quant);
	}

	stop {
		if (task.notNil) { task.stop };
		scheduleGeneration = scheduleGeneration + 1;
		scheduled.clear;
		activeMIDI.keysValuesDo { |key, count|
			count.do { midiout.noteOff(key[0], key[1], 0) }
		};
		activeMIDI.clear;
		activeSynths.do { |synth| synth.release(0.05) };
		activeSynths.clear;
	}

	start {
		if (task.notNil) { task.start }
	}

	pause {
		if (task.notNil) { task.pause }
	}
}

VoxRecordingMIDIOut {
	var <messages;

	*new { ^super.new.init }

	init {
		messages = List.new;
		^this
	}

	noteOn { |channel, note, velocity|
		messages.add((type: \noteOn, channel: channel, note: note, velocity: velocity));
	}

	noteOff { |channel, note, velocity|
		messages.add((type: \noteOff, channel: channel, note: note, velocity: velocity));
	}

	clear {
		messages.clear;
	}
}
