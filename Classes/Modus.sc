//   .--..--..--..--..--..--..--..--..--..--..--..--..--..--..--.
//  / .. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \
//  \ \/\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ \/ /
//   \/ /`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'\/ /
//   / /\                                                    / /\
//  / /\ \    __  __           _           _     _ _        / /\ \
//  \ \/ /   |  \/  | ___   __| |_   _ ___| |   (_) |__     \ \/ /
//   \/ /    | |\/| |/ _ \ / _` | | | / __| |   | | '_ \     \/ /
//   / /\    | |  | | (_) | (_| | |_| \__ \ |___| | |_) |    / /\
//  / /\ \   |_|  |_|\___/ \__,_|\__,_|___/_____|_|_.__/    / /\ \
//  \ \/ /                                                  \ \/ /
//   \/ /                                                    \/ /
//   / /\.--..--..--..--..--..--..--..--..--..--..--..--..--./ /\
//  / /\ \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \.. \/\ \
//  \ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `'\ `' /
//   `--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'`--'

// A modular MIDI timing and score utility library for SuperCollider.
// Provides conversion, structure, and extensible clip-processing tools for music programming.


//////////////////////////////////////////////////
// Score Position [bars, beats, divisions, tick]
//////////////////////////////////////////////////
Pos {
    var bar, beat, division, tick;

    *new { |bar=0, beat=0, division=0, tick=0|

        ^super.newCopyArgs(bar, beat, division, tick);
    }

	//////////////
	// GETTERS
	//////////////
	bar { ^bar }
	beat { ^beat }
	division { ^division }
	tick { ^tick }

	//////////////
	// SETTERS
	//////////////
	set_bar { arg inval; bar = inval }
	set_beat { arg inval; beat = inval }
	set_division { arg inval; division = inval }
	set_tick { arg inval; tick = inval }

	// there's probably no reason to individually set these? Or maybe if doing newFrom? not sure.

    asString {
        ^"Pos[% % % %]".format(bar.asInteger, beat.asInteger, division.asInteger, tick.asInteger);
    }
}

TimeConverter {

	*posToTicks {|pos, metre|
		var
		bars = metre.barsToTicks(pos.bar),
		beats = metre.beatsToTicks(pos.beat),
		divisions = metre.divisionsToTicks(pos.division, pos.beat);

		^[bars, beats, divisions, pos.tick].sum
	}

	*ticksToPos {|ticks, metre|
		var bar, remainder, beat, division, tick;

		#bar, remainder = metre.ticksToBars(ticks);
		#beat, remainder = metre.ticksToBeats(remainder);
		#division, tick = metre.ticksToDivisions(remainder, beat);

		^Pos(bar, beat, division, tick)
	}

	*posToTicksMM {|pos, metremap|
		var
		beats = metremap.barsToBeats(pos.bar) + pos.beat,
		divs = metremap.beatsToDivisions(beats) + pos.division;

		^metremap.divisionsToTicks(divs) + pos.tick
	}

	// ONE DONE!
	*ticksToPosMM {|ticks, metremap|
		var bar, remainder, beat, division, tick;

		#bar, remainder = metremap.ticksToBars(ticks);
		#beat, remainder = metremap.ticksToBeats(remainder);
		#division, tick = metremap.ticksToDivisions(remainder, beat);

		^Pos(bar, beat, division, tick)
	}

	*normalize {
		arg pos, metre;

		var totalTicks = this.posToTicks(pos, metre);

		^this.ticksToPos(totalTicks, metre)
	}

	*normalizeMM {
		arg pos, metremap;

		var totalTicks = this.posToTicksMM(pos, metremap);

		^this.ticksToPos(totalTicks, metremap)
	}
}

//////////////////////////
// Metre
//////////////////////////
Metre {
    var beats, divisions, tpqn;

    *new {
		arg beats=[1,1,1,1], divisions=[4,4,4,4], tpqn=960;

		// ensure divisions.size == beats.size
		if (beats.size != divisions.size) {
			divisions = beats.size.collect({arg i; divisions.wrapAt(i)});
			"divisions was altered to match length of beats".warn;
		};

        ^super.newCopyArgs(beats, divisions, tpqn);
    }

	//////////////
	// GETTERS
	//////////////
	beats { ^beats }
	divisions { ^divisions }
	tpqn { ^tpqn }

	//////////////
	// SETTERS
	//////////////
	set_beats {|inval| beats = inval }
	set_divisions {|inval| divisions = inval }
	set_tpqn {|inval| tpqn = inval }

	// could add validation checks to setters ...

	asString {
		^"Metre(%, %)".format(beats, divisions)
	}
}

Region {
	var start, metre;

	*new {
		arg start, metre;

		^super.newCopyArgs(start, metre)
	}

	start { ^start }
	metre { ^metre }

	set_start { |inval| start = inval }
	set_metre { |inval| metre = inval }

	asString {
		^"Region(start: % || metre: %)".format(start, metre)
	}
}

//////////////////////////
// MetreMap
//////////////////////////
MetreMap {
	var <>regions;
	// can this be private?

	*new {
		^super.new.init
	}

	init {
		regions = List.new;
		^this;
	}

	add {|region|
		var matchIndex, insertionIndex;

		if (region.isKindOf(Region).not) {
			"can only add Region objects to MetreMap".warn;
			^this
		};

		if (regions.isEmpty) { regions.add(region); ^this };

		// matching
		matchIndex = this.matchingRegionIndex(region);
		if (matchIndex.notNil) {
			regions.put(matchIndex, region);
			this.pushDownstream(region);
			^this
		};

		if (this.isEarliest(region)) {
			var nextBarline, offset;
			// add region as first region
			regions.addFirst(region);
			this.pushDownstream(region);
			^this
		};

		// in between or at end
		insertionIndex = this.insertionIndex(region.start);

		if (this.isBarAligned(region)) {
			regions.put(insertionIndex, region);
			this.pushDownstream(region);
			^this
		};

		this.snapToLastBarline(region);
		matchIndex = this.matchingRegionIndex(region);
		if (matchIndex.notNil) {
			regions.put(matchIndex, region);
			this.pushDownstream(region);
			^this
		};

		regions.insert(insertionIndex, region);
		this.pushDownstream(region);
		^this

	}
}

//////////////////////////
// Vox
//////////////////////////
Vox {
	// it gets a noteSustainEvents array and a metre
	var <>events, <>metreMap, <>range;
	var <>index = 0;

	*new {
		arg noteSustainEvents, metreMap=MetreMap.new;
		var events, range;

		if (noteSustainEvents.isEmpty) {
			^super.newCopyArgs([], metreMap);
		};

		// create events
		events = noteSustainEvents.collect({
			arg nse;
			var event;

			event = Event.newFrom([[\track, \absTime, \midicmd, \channel, \midinote, \velocity, \dur, \upVelo], nse].lace);

		});

		// make sure they're sorted
		events.sortBy(\absTime);

		// add position information
		events.do({
			arg event;
			event[\position] = event[\absTime].toPos(metreMap);
		});

		// set highlight to span the entire voice.
		if (events.isEmpty) { nil }
		{
			var
			first = events[0],
			last = events[events.size-1];

			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
		};

		^super.newCopyArgs(events, metreMap, range)
	}

	*newFromEventsArray {
		arg events, metreMap;
		var range;

		// make sure they're sorted
		events.sortBy(\absTime);

		// set range
		if (events.isEmpty) { nil }
		{
			var
			first = events[0],
			last = events[events.size-1];

			// range is a range between two absTime values
			range = [first.absTime, last.absTime + last.dur];
		};

		^super.newCopyArgs(events, metreMap, range);

	}

	asTask {
		arg instrument=\default;

		^Task {
			var i=0, clip, synth, event;

			clip = this.clip(this.range);

			clip.do {
				arg event;
				synth = Synth(instrument, [\freq, event[\midinote].midicps]);
				i = i+1;
				// need to adapt for new metre format
				// will need to store position info on events, so can check divisions in beat
				(event[\dur] / this.metre.ticksPerBeat).wait;
				synth.release(2)
			}
		}
	}

	asLoop {
		arg instrument=\default;
		var clip;

		clip = this.clip(this.range);

		^Task {
			var i=0, synth;
			loop {
				var event;
				event = clip.wrapAt(i);
				synth = Synth(instrument, [\freq, event[\midinote].midicps]);
				i = i+1;
				// need to adapt for new metre format
				(event[\dur] / this.metre.ticksPerBeat).wait;
				synth.release(2);
			}
		}
	}

	midiTask {
		arg midiout;

		^Task {
			var clip;

			clip = this.clip(this.range);

			clip.do {
				arg event;
				midiout.noteOn(event.channel, event.midinote, event.velocity);
				// need to adapt for new Metre format
				(event.dur / this.metre.ticksPerBeat).wait;
				midiout.noteOff(event.channel, event.midinote, event.velocity);
			}
		}
	}

	midiLoop {
		arg midiout;
		var clip;

		clip = this.clip(this.range);

		^Task {
			var i=0;

			loop {
				var event;
				event = clip.wrapAt(i);
				midiout.noteOn(event.channel, event.midinote, event.velocity);
				// need to adapt for new Metre format
				(event[\dur] / this.metre.ticksPerBeat).wait;
				midiout.noteOff(event.channel, event.midinote, event.velocity);
				i = i+1;
			}
		}
	}

	highlight { |startPos, endPos|

		^range = [metreMap.absTicks(startPos).asInteger, metreMap.absTicks(endPos).asInteger];
	}

	highlighted {
		^"% --> %".format(range[0].toPos(metreMap), range[1].toPos(metreMap))
	}

	clip {
		var rangeStart = range[0];
        var rangeEnd = range[1];
		var events, return = [];

		// copy events
		events = this.events.deepCopy;

		return = events.select({
			arg event;

			var eventStart = event[\absTime]; // gets events tick position
			var eventEnd = eventStart + event[\dur];

			(eventStart < rangeEnd) and: (eventEnd > rangeStart);

		}).do({
			arg event;

			var eventStart = event[\absTime]; // gets events tick position
			var eventEnd = eventStart + event[\dur];

			if ((eventStart < rangeStart) && (eventEnd > rangeStart)) {
				event[\dur] = eventEnd - rangeStart;
				event[\absTime] = rangeStart;
			};

			if ((eventStart < rangeEnd) && (eventEnd > rangeEnd)) {
				event[\dur] = rangeEnd - eventStart;
			}
		});

		^Vox.newFromEventsArray(return, metreMap);
		// maybe problematic if I'm clipping, it would need to be a specific metre map...
    }

}