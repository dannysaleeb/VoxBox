// Bear in mind these tasks can't be paused/stopped mid-flow, only end of cycle
// This needs addressing, possibly by scheduling instead, rather than using .wait.

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

		if (clock.isNil) { clock = TempoClock.default };

		^Task ({

			var plug = source.respondsTo(\out).if { source.out } { source };
			var events = plug.events;
			var startTick = events.first[\absTime];
			var tpqn = plug.metremap.tpqn;

			events.do { |event|
				{
					var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
					var durBeats = event[\dur].toMIDIBeats(tpqn);

					deltaBeats.wait;

					midiout.noteOn(event.channel, event.midinote, event.velocity);

					durBeats.wait;

					midiout.noteOff(event.channel, event.midinote, event.velocity);
				}.fork(clock);
			}
		}, clock);
	}

	makeMIDILoop { |midiout|

		if (clock.isNil) { clock = TempoClock.default };

		^Task ({
			loop {
				var plug = source.respondsTo(\out).if { source.out } { source };
				var events = plug.events; // get updated event list
				var startTick = events.first[\absTime]; // absolute start of clip
				var lastEvent, clipEnd, totalTicks, totalBeats;
				var tpqn = plug.metremap.tpqn;

				// Schedule each event individually with correct offset
				events.do { |event|
					{
						var deltaBeats = (event[\absTime] - startTick).toMIDIBeats(tpqn);
						var durBeats = event[\dur].toMIDIBeats(tpqn);

						deltaBeats.wait;

						midiout.noteOn(event.channel, event.midinote, event.velocity);

						durBeats.wait;

						midiout.noteOff(event.channel, event.midinote, event.velocity);

					}.fork(clock);
				};

				// Wait for the full clip duration before restarting
				lastEvent = events.last;
				clipEnd = lastEvent[\absTime] + lastEvent[\dur];
				totalTicks = clipEnd - startTick;

				totalTicks.toMIDIBeats(tpqn).wait;
			}
		}, clock);
	}
}