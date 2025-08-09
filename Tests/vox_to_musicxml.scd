// 1) Install/load the JSON quark once:
// Quarks.install("JSON");  // run once, then recompile server
(
MIDIClient.init;
m = MIDIOut.new(0);
m.latency = 0;

f = SimpleMIDIFile.read("/Users/dannysaleeb/Library/CloudStorage/Dropbox/universe/work/Lockton Canons/HC/hc_orig.mid");

// ~arrangment = VoxArrangement.new
)

~hc_dux = Vox.fromMIDI(f, \dux);


// -- Helper: Metre -> music21-friendly TS string
~metreToTSString = { |metre|
    // Expect metre.beats: Array of numerators per beat (ints/floats)
    //        metre.divs:  Array of denominators per beat (ints)
    var beats = metre.beats.collect(_.asFloat); // ensure numeric
    var dens  = metre.divisions;

    // If all denominators equal, simple/additive case
    if (dens.asSet.size == 1) {
        var d = dens.first.asInteger;
        var sumNum = beats.sum; // may be int already
        // If all beats are integers like [1,1,1,1], emit "4/4"
        // Otherwise additive like "3+2+3/8"
        var allOnes = beats.any({ |b| b == b.round(1) and: { (b == 1.0) } });
        if (allOnes) {
            ("%/%").format(sumNum.asInteger, d);
        } {
            ("%/%").format(beats.collect({ |b| b.asInteger }).join("+"), d);
        }
    }
	{
        // Mixed denominators: convert to common denominator
        var lcm = dens.inject(1, { |acc, v| acc.lcm(v.asInteger) });
        var nums = beats.collectWith(dens, { |b, den|
            (b * (lcm / den)).round(1).asInteger
        });
        ("%/%").format(nums.join("+"), lcm);
    }
};

~metreToTSString.value(Metre.new([1, 1, 1], [4, 4, 4]))

// -- Exporter: build regions with ts strings
~voxToJSON = { |vox, path|
    var plug = vox.out;
    var tpqn = plug.metremap.tpqn;

    var regions = plug.metremap.regions.collect { |r|
        var tsStr;
        // adjust to your Metre/MetreRegion API: r.metre.beats, r.metre.divs, r.at
        tsStr = ~metreToTSString.(r.metre);
        (at: r.start, ts: tsStr)
    };

    var events = plug.events.collect { |e|
        (
            start: e[\absTime].asInteger,
            dur:   e[\dur].asInteger,
            pitch: e[\midinote].asInteger,
            vel:   (e[\velocity] ? 80).asInteger
        )
    };

    var obj = (
        type: "Vox",
        label: plug.label.asString,
        tpqn: tpqn.asInteger,
        metreMap: (regions: regions),
        events: events
    );

    File.use(path, "w", { |f| f.write(obj.asJSON) });
    "Wrote JSON to %".format(path).postln;
};

// ---- Example usage ----
// Make a tiny Vox (or use your existing ~hc_dux)
~outPath = thisProcess.nowExecutingPath.dirname +/+ "vox_demo.json";
~voxToJSON.(~hc_dux, ~outPath);
