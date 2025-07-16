+ Number {

	toPos {
		arg metremap;
		^TimeConverter.ticksToPos(this.asInteger, metremap);
	}

	toMIDIBeats { |tpqn| ^this / tpqn }

}