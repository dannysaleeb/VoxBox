(
///////////////
// Metre tests
///////////////

~outcome = {arg bool;
	if (bool) {
		\success
	} { \failure }
};

//
//metre exists?
//
m = Metre.new([1, 1], [4, 4]); // do not change -- hardcoded below
"--------".postln;
"metre exists: %".format(~outcome.(m.notNil)).postln;
//
// beats is correct
//
"--------".postln;
"number of beats: %".format(~outcome.(m.beats.size == 2)).postln;
//
// divisions is correct
//
"--------".postln;
"number of divisions: %".format(~outcome.(m.divisions.size == 2)).postln;
//
// ticks per beat
//
"--------".postln;
"ticks per beat: %".format(~outcome.(m.ticksPerBeat == [960, 960])).postln;
//
// ticks per beat after tpqn change
//
m.set_tpqn(480);
"--------".postln;
"ticks per beat after adjusted tpqn: %".format(~outcome.(m.ticksPerBeat == [480, 480])).postln;
m.set_tpqn(960);
//
// ticks per division
//
"--------".postln;
"ticks per division: %".format(~outcome.(m.ticksPerDivision == [240, 240])).postln;
//
// ticks per division for irregular metre
//
m.set_beats([1,1,1.5]);
m.set_divisions([4,4,6]);
"--------".postln;
"ticks per division after adjusted divisions: %".format(~outcome.(m.ticksPerDivision == [240, 240, 240])).postln;
m.set_beats([1,1]);
m.set_divisions([4,4]);
//
// bars to ticks test 1
//
"--------".postln;
"1 bar to ticks: %".format(~outcome.(m.barsToTicks(1) == m.ticksPerBar)).postln;
//
// bars to ticks test 2
//
"--------".postln;
"10 bars to ticks: %".format(~outcome.(m.barsToTicks(10) == (m.ticksPerBar*10))).postln;
//
// bars to ticks test 3
//
"--------".postln;
"100 bars to ticks: %".format(~outcome.(m.barsToTicks(100) == (m.ticksPerBar*100))).postln;
//
// bars to ticks test 1
//
"--------".postln;
"1 bar to ticks: %".format(~outcome.(m.barsToTicks(1) == m.ticksPerBar)).postln;
//
// divisions to ticks 1
//
"--------".postln;
"1 division to ticks: %".format(~outcome.(m.divisionsToTicks(1) == (m.tpqn / 4))).postln;
//
// divisions to ticks 2
//
"--------".postln;
"4 divisions to ticks: %".format(~outcome.(m.divisionsToTicks(4) == m.tpqn)).postln;
//
// divisions to ticks 3
//
"--------".postln;
"8 divisions to ticks: %".format(~outcome.(m.divisionsToTicks(8) == (m.tpqn * 2))).postln;
//
// divisions to ticks 4
//
"--------".postln;
"24 divisions to ticks: %".format(~outcome.(m.divisionsToTicks(24) == (m.ticksPerBar * 3))).postln;
//
// divisions to ticks 5
//
m = Metre([1.5, 1, 1], [2, 3, 4]); // 7/8 with irregular divisions
"--------".postln;
"1 division to ticks in irregularly divided bar: %".format(~outcome.(m.divisionsToTicks(1) == (m.ticksPerBeat[0] / m.divisions[0]))).postln;
//
// divisions to ticks 6
//
"--------".postln;
"5 divisions to ticks in irregularly divided bar: %".format(~outcome.(m.divisionsToTicks(5) == (((m.ticksPerBeat[0] / m.divisions[0])*2) + ((m.ticksPerBeat[1] / m.divisions[1])*3)))).postln;
)

960/6

m.ticksPerBeat
// MetreMap tests
(
~result = nil;
// :add
~metre1 = Metre([1,1,1], [4,4,4]); // 3/4
~metre2 = Metre([1,1,1,1], [4,4,4,4]); // 4/4
~metre3 = Metre([1.5,1,1], [6,4,4]); // 7/8
~metre4 = Metre([1,1.5,1], [4,4,4]); // 7/8 weird division

~region1 = Region(3840, nil, ~metre2);
~region2 = Region(0, nil, ~metre1);

~map = MetreMap.new;
~map.add(~region1);

// check single region was added
"---------".postln;

if (~map.entries.size == 1) {
	~outcome = \success;
} { ~outcome = \failure };

"Single region added: %".format(~outcome).postln;

// check region.tick value correct
if (~map.entries.tick == ~region1.tick) {
	~outcome =
}
)

// sequence, create MetreMap, add 4/4 at tick 3840 (bar 2)
// add 3/4 at tick 0 (should update region 2) and tests insertion before existing one

960*4