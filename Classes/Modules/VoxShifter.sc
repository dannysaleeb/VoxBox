VoxShifter : VoxModule {
	var <offset, <direction;

	*new { |offset = 0, direction = 1|
		^super.new.initShift(offset, direction)
	}

	*later { |offset|
		^this.new(offset, 1)
	}

	*earlier { |offset|
		^this.new(offset, -1)
	}

	*ticks { |ticks = 0|
		^this.new(ticks, 1)
	}

	initShift { |offsetArg, directionArg|
		label = \shift;
		metadata = Dictionary.new;
		offset = offsetArg ? 0;
		direction = ((directionArg ? 1) < 0).if { -1 } { 1 };
		^this
	}

	offset_ { |value|
		offset = value ? 0;
		this.touch;
		^this
	}

	direction_ { |value|
		direction = ((value ? 1) < 0).if { -1 } { 1 };
		this.touch;
		^this
	}

	provenanceSpec {
		^(
			op: \shift,
			params: (
				offset: VoxProvenance.posValue(offset),
				direction: direction
			)
		)
	}

	offsetTicks { |metremap|
		var ticks;

		ticks = if (offset.isKindOf(Pos)) {
			TimeConverter.posToTicks(offset, metremap)
		} {
			if (offset.isNumber) {
				offset.asInteger
			} {
				"VoxShifter: expected offset as ticks or Pos.".warn;
				0
			}
		};

		^ticks * direction
	}

	doProcess { |vox|
		var tickOffset = this.offsetTicks(vox.metremap);
		var events = vox.events.collect { |ev|
			var newEv = ev.copy;
			var newAbsTime = ev[\absTime] + tickOffset;

			newEv[\absTime] = newAbsTime;
			newEv[\position] = TimeConverter.ticksToPos(newAbsTime, vox.metremap);
			newEv
		};

		^Vox.new(
			events.sortBy(\absTime),
			vox.metremap,
			vox.label,
			vox.metadata.deepCopy,
			this
		)
	}
}
