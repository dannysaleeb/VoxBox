Canoniser : VoxModule {
	var <>numVoices;
	var <>delayTicks;

	*new {
		arg numVoices = 2, delayTicks = 960, label;

		^super.new(label ?? \canoniser)
		.numVoices_(numVoices)
		.delayTicks_(delayTicks);
	}
}