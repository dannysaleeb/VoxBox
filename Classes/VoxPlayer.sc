/////////////////////////////////////////////////////
// Player Wrapper Class: for synth & MIDI playback //
/////////////////////////////////////////////////////
VoxPlayer {
	var source, clock, task;

	*new {
		arg source, clock;

		^super.newCopyArgs(source, clock)
	}

	stop {
		if (task.notNil) { task.stop };
	}

	start {
		if (task.notNil) { task.start }
	}

	pause {
		if (task.notNil) { task.pause }
	}

	////////////////////
	// SYNTH PLAYBACK //
	////////////////////
	play {|quant|
		task = this.makeTask;
		task.play(quant: quant);
	}

	loop {|quant|
		task = this.makeLoop;
		task.play(quant: quant);
	}

	makeTask { |instrument = \default|

		if (clock.isNil) {clock = TempoClock.default };

		^Task ({
			var plug = source.respondsTo(\out).if { source.out } { source };
			var events = plug.events;
			var startTick = events.first[\absTime];
			var tpqn = plug.metremap.tpqn;

			events.do({ |event|
				{
					var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
					var durBeats = event[\dur].toMIDIBeats(tpqn);
					var synth;

					deltaBeats.wait;
					synth = Synth(instrument, [\freq, event[\midinote].midicps]);
					durBeats.wait;
					synth.release(1.5);

				}.fork(clock);
			})
		})
	}

	makeLoop { |instrument = \default|

		if (clock.isNil) {clock = TempoClock.default };

		^Task ({
			loop {
				var plug = source.respondsTo(\out).if { source.out } { source };
				var events = plug.events;
				var startTick = events.first[\absTime];
				var lastEvent, clipEnd, totalTicks, totalBeats;
				var tpqn = plug.metremap.tpqn;

				events.do({ |event|
					{
						var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
						var durBeats = event[\dur].toMIDIBeats(tpqn);
						var synth;

						deltaBeats.wait;
						synth = Synth(instrument, [\freq, event[\midinote].midicps]);
						durBeats.wait;
						synth.release(1.5);

					}.fork(clock);
				});

				// Wait for the full clip duration before restarting
				lastEvent = events.last;
				clipEnd = lastEvent[\absTime] + lastEvent[\dur];
				totalTicks = clipEnd - startTick;

				totalTicks.toMIDIBeats(tpqn).wait;
			};
		}, clock)
	}


	///////////////////
	// MIDI PLAYBACK //
	///////////////////
	playMIDI {|midiout, quant|
		task = this.makeMIDITask(midiout);
		task.play(quant: quant);
	} // play once as midi

	loopMIDI {|midiout, quant|
		task = this.makeMIDILoop(midiout);
		task.play(quant: quant);
	} // play loop as midi

	makeMIDITask { |midiout|

		var plug = source.respondsTo(\out).if { source.out } { source };

		if (clock.isNil) { clock = TempoClock.default };

		if (plug.isKindOf(VoxPlugMulti)) {
			^Task({
				plug.asArray.do { |p, i|
					{
						var events = p.events;
						var startTick = events.first[\absTime];
						var tpqn = p.metremap.tpqn;

						events.do { |event|
							var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
							var durBeats = event[\dur].toMIDIBeats(tpqn);

							deltaBeats.wait;
							midiout.noteOn(i, event[\midinote], event[\velocity]);
							durBeats.wait;
							midiout.noteOff(i, event[\midinote], event[\velocity]);
						};
					}.fork(clock);
				};
			}, clock)
		};

		// Single VoxPlug fallback
		^Task({
			var events = plug.events;
			var startTick = events.first[\absTime];
			var tpqn = plug.metremap.tpqn;

			events.do { |event|
				var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
				var durBeats = event[\dur].toMIDIBeats(tpqn);

				deltaBeats.wait;
				midiout.noteOn(event[\channel], event[\midinote], event[\velocity]);
				durBeats.wait;
				midiout.noteOff(event[\channel], event[\midinote], event[\velocity]);
			}
		}, clock);
	}

	makeMIDILoop { |midiout|
		if (clock.isNil) { clock = TempoClock.default };

		^Task({
			loop {
				var plug = source.respondsTo(\out).if { source.out } { source };

				if (plug.isKindOf(VoxPlugMulti)) {
					plug.asArray.do { |p, i|
						{
							var events = p.events;
							var startTick = events.first[\absTime];
							var tpqn = p.metremap.tpqn;
							var endTick, totalBeats;

							events.do { |event|
								var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
								var durBeats = event[\dur].toMIDIBeats(tpqn);

								deltaBeats.wait;
								midiout.noteOn(i, event[\midinote], event[\velocity]);
								durBeats.wait;
								midiout.noteOff(i, event[\midinote], event[\velocity]);
							};

							endTick = events.last[\absTime] + events.last[\dur];
							totalBeats = (endTick - startTick).toMIDIBeats(tpqn);

							totalBeats.wait;
						}.fork(clock);
					};
				} {
					var events = plug.events;
					var startTick = events.first[\absTime];
					var tpqn = plug.metremap.tpqn;
					var endTick, totalBeats;

					events.do { |event|
						var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
						var durBeats = event[\dur].toMIDIBeats(tpqn);

						deltaBeats.wait;
						midiout.noteOn(event[\channel], event[\midinote], event[\velocity]);
						durBeats.wait;
						midiout.noteOff(event[\channel], event[\midinote], event[\velocity]);
					};

					endTick = events.last[\absTime] + events.last[\dur];
					totalBeats = (endTick - startTick).toMIDIBeats(tpqn);

					totalBeats.wait;
				};
			}
		}, clock);
	}
}