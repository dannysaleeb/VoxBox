VoxExport {
	*resolveSnapshot { |source|
		var snapshot;

		if (source.isKindOf(Vox) or: { source.isKindOf(VoxMulti) }) {
			^source.copy
		};

		if (source.notNil and: { source.respondsTo(\out) }) {
			snapshot = source.out;
			if (snapshot.isKindOf(Vox) or: { snapshot.isKindOf(VoxMulti) }) {
				^snapshot.copy
			};
		};

		"VoxExport: source must resolve to Vox or VoxMulti.".warn;
		^nil
	}

	*scriptPath {
		var classPath = this.filenameSymbol.asString.standardizePath;
		^(classPath.dirname.dirname +/+ "Scripts" +/+ "voxexport_musicxml.py").standardizePath
	}

	*beatFraction { |quarterLength|
		var denoms = [1, 2, 4, 8, 16, 32, 64];
		var denom, numerator;

		denom = denoms.detect { |candidate|
			var value = quarterLength.asFloat * candidate / 4;
			value > 0 and: { value.round(1) == value.round(0.000001) }
		};

		if (denom.isNil) {
			^[(quarterLength.asFloat * 4).round(1).asInteger.max(1), 16]
		};

		numerator = (quarterLength.asFloat * denom / 4).round(1).asInteger;
		^[numerator, denom]
	}

	*metreToTimeSignature { |metre|
		var fractions, commonDenom, numerator;

		fractions = metre.beats.collect { |beat| this.beatFraction(beat) };
		commonDenom = fractions.collect { |pair| pair[1] }.inject(1, { |acc, denom|
			acc.lcm(denom)
		});
		numerator = fractions.collect { |pair|
			pair[0] * (commonDenom / pair[1])
		}.sum.asInteger;

		^"%/%".format(numerator.max(1), commonDenom.asInteger.max(1))
	}

	*encodeMetreMap { |map|
		^(
			regions: map.regions.collect { |region|
				(
					start: region.start.asInteger,
					beats: region.metre.beats,
					divisions: region.metre.divisions,
					timeSignature: this.metreToTimeSignature(region.metre)
				)
			}
		)
	}

	*encodeEvent { |event|
		var output = (
			start: event[\absTime].asInteger,
			dur: event[\dur].asInteger,
			midinote: event[\midinote].asInteger
		);

		if (event[\velocity].notNil) {
			output[\velocity] = event[\velocity].asInteger;
		};
		if (event[\channel].notNil) {
			output[\channel] = event[\channel].asInteger;
		};

		^output
	}

	*encodePart { |vox|
		^(
			label: vox.label.asString,
			events: vox.events
				.select { |event|
					event[\absTime].isNumber and: {
						event[\dur].isNumber and: { event[\midinote].isNumber }
					}
				}
				.sort { |left, right|
					if (left[\absTime] == right[\absTime]) {
						left[\midinote] < right[\midinote]
					} {
						left[\absTime] < right[\absTime]
					}
				}
				.collect { |event| this.encodeEvent(event) }
		)
	}

	*documentFor { |snapshot|
		var parts, type;

		if (snapshot.isKindOf(Vox)) {
			type = "Vox";
			parts = [this.encodePart(snapshot)];
		} {
			if (snapshot.isKindOf(VoxMulti)) {
				type = "VoxMulti";
				parts = snapshot.asArray
					.sort { |left, right| left.label.asString < right.label.asString }
					.collect { |vox| this.encodePart(vox) };
			} {
				"VoxExport: snapshot must be Vox or VoxMulti.".warn;
				^nil
			}
		};

		^(
			format: "voxbox-export",
			version: 1,
			type: type,
			label: snapshot.label.asString,
			tpqn: snapshot.metremap.tpqn.asInteger,
			metreMap: this.encodeMetreMap(snapshot.metremap),
			parts: parts
		)
	}

	*writeJSON { |source, path|
		var snapshot = this.resolveSnapshot(source);
		var document;

		if (snapshot.isNil) { ^nil };
		document = this.documentFor(snapshot);
		if (document.isNil) { ^nil };

		File.use(path.standardizePath, "w", { |file|
			file.write(JSONlib.convertToJSON(document, postWarnings: false))
		});

		^snapshot
	}

	*writeMusicXML { |source, path, python = "python3", keepJSON = false|
		var snapshot, xmlPath, jsonPath, script, command, result;

		xmlPath = path.standardizePath;
		jsonPath = (xmlPath ++ ".voxexport.json").standardizePath;
		script = this.scriptPath;

		if (File.exists(script).not) {
			"VoxExport: MusicXML bridge not found at %.".format(script).warn;
			^nil
		};

		snapshot = this.writeJSON(source, jsonPath);
		if (snapshot.isNil) { ^nil };

		command = "% % % %".format(
			python.asString.shellQuote,
			script.shellQuote,
			jsonPath.shellQuote,
			xmlPath.shellQuote
		);
		result = command.systemCmd;

		if (result != 0) {
			"VoxExport: MusicXML conversion failed; JSON left at %.".format(jsonPath).warn;
			^nil
		};

		if (keepJSON.not) {
			File.delete(jsonPath);
		};

		^snapshot
	}
}
