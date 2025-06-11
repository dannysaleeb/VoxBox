// MetreMap tests
(
~outcome = nil;
// :add
~metre1 = Metre([1,1,1], [4,4,4]); // 3/4
~metre2 = Metre([1,1,1,1], [4,4,4,4]); // 4/4
~metre3 = Metre([1.5,1,1], [6,4,4]); // 7/8
~metre4 = Metre([1,1.5,1], [4,4,4]); // 7/8 weird division

~region1 = Region(3840, nil, ~metre2);
~region2 = Region(0, nil, ~metre1);

~map = MetreMap.new;
~map.add(~region1);

// print ...
"---------".postln;

if (~map.entries.size == 1) {
	~outcome = \success;
} { ~outcome = \failure };

"Single region added: %".format(~outcome).postln;
)

// sequence, create MetreMap, add 4/4 at tick 3840 (bar 2)
// add 3/4 at tick 0 (should update region 2) and tests insertion before existing one

960*4