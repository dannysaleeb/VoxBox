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
	playMIDI { |midiout, quant|
		var plug = source.respondsTo(\out).if { source.out } { source };

		plug.postln;

		if (plug.isKindOf(VoxPlugMulti)) {
			task = this.makeMIDITaskMulti(midiout);
		} {
			task = this.makeMIDITask(midiout);
		};

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

	makeMIDITaskMulti { |midiout|
		if (clock.isNil) { clock = TempoClock.default };

		^Task({
			var plug = source.respondsTo(\out).if { source.out } { source };
			var plugs = plug.asArray;
			var globalStartTick = plugs.collect { |p| p.events.first[\absTime] }.minItem;
			var tpqn = plugs[0].metremap.tpqn;

			plugs.do { |p, i|
				var channel = i.clip(0, 15);
				var events = p.events;

				"Voice % (channel = %): % events".format(i, channel, events.size).postln;

				events.do { |event, j|
					{
						var deltaBeats = (event[\absTime] - globalStartTick).toMIDIBeats(tpqn);
						var durBeats = event[\dur].toMIDIBeats(tpqn);

						"[voice % | idx %] → CH:% | NOTE:% | ABS:% | DELTA: %b | DUR: %b | VEL: %"
						.format(i, j, channel, event[\midinote], event[\absTime], deltaBeats, durBeats, event[\velocity]).postln;

						deltaBeats.wait;
						midiout.noteOn(channel, event[\midinote], event[\velocity]);
						durBeats.wait;
						midiout.noteOff(channel, event[\midinote], event[\velocity]);
					}.fork(clock);
				};
			};
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