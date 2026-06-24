VoxGridSplitter : VoxModule {
	var <unit;

	*new { |unit = \eighth|
		^super.new.initGrid(unit)
	}

	initGrid { |unitArg|
		unit = unitArg ? \eighth;
		label = \gridSplitter;
		metadata = Dictionary.new;
		^this
	}

	unit_ { |value|
		unit = value;
		this.touch;
		^this
	}

	unitTicks { |metremap|
		if (unit.isKindOf(Fragments)) {
			^unit.unitTicks(metremap)
		};
		^Fragments(unit).unitTicks(metremap)
	}

	nextBoundaryAfter { |tick, cellTicks|
		^(((tick / cellTicks).floor.asInteger + 1) * cellTicks)
	}

	splitEvent { |event, vox, eventIndex|
		var cellTicks, startTick, finishTick, cursor, segmentIndex, segments;

		cellTicks = this.unitTicks(vox.metremap).max(1);
		startTick = event[\absTime];
		finishTick = startTick + event[\dur];
		cursor = startTick;
		segmentIndex = 0;
		segments = List.new;

		if (event[\dur] <= 0) { ^[event.deepCopy] };

		while { cursor < finishTick } {
			var nextBoundary, segmentEnd, newEvent, metadata;

			nextBoundary = this.nextBoundaryAfter(cursor, cellTicks);
			segmentEnd = nextBoundary.min(finishTick);
			newEvent = event.deepCopy;
			metadata = event[\metadata].notNil.if {
				event[\metadata].deepCopy
			} {
				Dictionary.new
			};

			newEvent[\absTime] = cursor;
			newEvent[\dur] = segmentEnd - cursor;
			newEvent[\position] = TimeConverter.ticksToPos(cursor, vox.metremap);

			metadata[\grid_origin_absTime] = startTick;
			metadata[\grid_origin_dur] = event[\dur];
			metadata[\grid_origin_midinote] = event[\midinote];
			metadata[\grid_source_index] = eventIndex;
			metadata[\grid_index] = segmentIndex;
			metadata[\grid_is_onset] = segmentIndex == 0;
			metadata[\grid_inserted_onset] = segmentIndex > 0;
			metadata[\grid_unit] = unit;
			newEvent[\metadata] = metadata;

			segments.add(newEvent);
			cursor = segmentEnd;
			segmentIndex = segmentIndex + 1;
		};

		segments.do { |segment|
			segment[\metadata][\grid_count] = segments.size
		};

		^segments.asArray
	}

	doProcess { |vox|
		var events = List.new;

		vox.events.do { |event, index|
			this.splitEvent(event, vox, index).do { |segment|
				events.add(segment)
			}
		};

		^Vox.new(
			events.asArray.sortBy(\absTime),
			vox.metremap,
			vox.label,
			vox.metadata.deepCopy,
			this
		)
	}
}
