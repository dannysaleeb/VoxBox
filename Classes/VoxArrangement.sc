VoxArrangementSlot : VoxSnapshotTarget {
	var <arrangement, <id, <start, <mode, <anchor;

	*new { |arrangement, id, start, mode = \overlay, anchor = \ticks|
		^super.new.init(arrangement, id, start, mode, anchor)
	}

	init { |arrangementArg, idArg, startArg, modeArg, anchorArg|
		arrangement = arrangementArg;
		id = idArg;
		start = startArg;
		mode = modeArg;
		anchor = anchorArg;
		^this
	}

	storeSnapshot { |source|
		^arrangement.add(id, start, source, mode, anchor)
	}
}

VoxArrangement : VoxNode {
	var <regions, <metremap, <history, <historyPointer, <nextOrder;

	*new { |metremap, label = \anonArrangement|
		^super.new.init(metremap, label)
	}

	init { |metremapArg, labelArg|
		input = nil;
		label = labelArg;
		metadata = Dictionary.new;
		revision = 0;
		regions = List.new;
		history = List.new;
		historyPointer = -1;
		nextOrder = 0;
		metremap = (metremapArg ?? { MetreMap.new }).copy;
		if (metremap.isEmpty) {
			metremap.add(MetreRegion.new(0, Metre([1, 1, 1, 1], [4, 4, 4, 4])));
		};
		this.commit(\initial);
		^this
	}

	slot { |id, start, mode = \overlay, anchor = \ticks|
		^VoxArrangementSlot.new(this, id, start, mode, anchor)
	}

	validateTPQN { |snapshot|
		if (
			snapshot.metremap.notNil.and({
				metremap.notNil
			}).and({
				snapshot.metremap.tpqn != metremap.tpqn
			})
		) {
			(
				"VoxArrangement: source tpqn % does not match arrangement tpqn %."
				.format(snapshot.metremap.tpqn, metremap.tpqn)
			).warn;
			^false
		};
		^true
	}

	add { |id, start, source, mode = \overlay, anchor = \ticks|
		var snapshot, region, resolvedStart;

		if (id.isKindOf(Symbol).not) {
			"VoxArrangement: region IDs must be Symbols.".warn;
			^this
		};

		if (this.regionAt(id).notNil) {
			("VoxArrangement: region % already exists; use replace.".format(id)).warn;
			^this
		};

		snapshot = VoxSnapshotTarget.resolveSnapshot(source);
		if (snapshot.isNil) { ^this };
		if (this.validateTPQN(snapshot).not) { ^this };

		resolvedStart = start.isKindOf(Pos).if {
			TimeConverter.posToTicks(start, metremap)
		} {
			start
		};
		snapshot = VoxProvenance.stamp(snapshot, VoxProvenance.boundary(
			\arrangementRegion,
			(id: id, start: start, resolvedTick: resolvedStart, anchor: anchor, mode: mode),
			VoxProvenance.provenanceOf(source)
		));

		region = VoxRegion.new(id, snapshot, start, metremap, anchor, mode, nextOrder);
		if (region.isNil) { ^this };

		regions.add(region);
		nextOrder = nextOrder + 1;
		this.sortRegions;
		this.touch;
		^this
	}

	replace { |id, start, source, mode = \overlay, anchor = \ticks|
		var index, snapshot, region, order, resolvedStart;

		index = regions.detectIndex({ |item| item.id == id });
		if (index.isNil) {
			("VoxArrangement: cannot replace missing region %.".format(id)).warn;
			^this
		};

		snapshot = VoxSnapshotTarget.resolveSnapshot(source);
		if (snapshot.isNil) { ^this };
		if (this.validateTPQN(snapshot).not) { ^this };

		resolvedStart = start.isKindOf(Pos).if {
			TimeConverter.posToTicks(start, metremap)
		} {
			start
		};
		snapshot = VoxProvenance.stamp(snapshot, VoxProvenance.boundary(
			\arrangementRegion,
			(id: id, start: start, resolvedTick: resolvedStart, anchor: anchor, mode: mode),
			VoxProvenance.provenanceOf(source)
		));

		order = regions[index].order;
		region = VoxRegion.new(id, snapshot, start, metremap, anchor, mode, order);
		if (region.isNil) { ^this };

		regions[index] = region;
		this.sortRegions;
		this.touch;
		^this
	}

	regionAt { |id|
		^regions.detect({ |region| region.id == id })
	}

	at { |id|
		var region;

		region = this.regionAt(id);
		if (region.isNil) {
			("VoxArrangement: region % does not exist.".format(id)).warn;
			^nil
		};
		^region.copy
	}

	keys {
		^regions.collect(_.id)
	}

	endTick {
		if (regions.isEmpty) { ^0 };
		^regions.collect({ |region| region.resolvedStart + region.duration }).maxItem
	}

	append { |id, source, mode = \overlay|
		^this.add(id, this.endTick, source, mode, \ticks)
	}

	insertBefore { |id, beforeId, source, mode = \overlay|
		var target, inserted, start;

		if (this.regionAt(id).notNil) {
			("VoxArrangement: region % already exists; use replace.".format(id)).warn;
			^this
		};

		target = this.regionAt(beforeId);
		if (target.isNil) {
			("VoxArrangement: region % does not exist.".format(beforeId)).warn;
			^this
		};

		start = target.resolvedStart;
		this.add(id, start, source, mode, \ticks);
		inserted = this.regionAt(id);
		if (inserted.isNil) { ^this };

		this.shiftRegionsFrom(start, inserted.duration, id);
		this.sortRegions;
		this.touch;
		^this
	}

	insertAfter { |id, afterId, source, mode = \overlay|
		var target, inserted, start;

		if (this.regionAt(id).notNil) {
			("VoxArrangement: region % already exists; use replace.".format(id)).warn;
			^this
		};

		target = this.regionAt(afterId);
		if (target.isNil) {
			("VoxArrangement: region % does not exist.".format(afterId)).warn;
			^this
		};

		start = target.resolvedStart + target.duration;
		this.add(id, start, source, mode, \ticks);
		inserted = this.regionAt(id);
		if (inserted.isNil) { ^this };

		this.shiftRegionsFrom(start, inserted.duration, id);
		this.sortRegions;
		this.touch;
		^this
	}

	moveBefore { |id, beforeId|
		^this.moveInSequence(id, beforeId, \before)
	}

	moveAfter { |id, afterId|
		^this.moveInSequence(id, afterId, \after)
	}

	moveInSequence { |id, targetId, relation|
		var moving, target, sequence, targetIndex, packStart;

		moving = this.regionAt(id);
		target = this.regionAt(targetId);
		if (moving.isNil) {
			("VoxArrangement: region % does not exist.".format(id)).warn;
			^this
		};
		if (target.isNil) {
			("VoxArrangement: region % does not exist.".format(targetId)).warn;
			^this
		};
		if (id == targetId) { ^this };

		sequence = regions.copy;
		packStart = sequence.first.resolvedStart;
		sequence.remove(moving);
		targetIndex = sequence.indexOf(target);
		if (relation == \after) { targetIndex = targetIndex + 1 };
		sequence.insert(targetIndex, moving);
		this.pack(packStart, sequence.collect(_.id));
		^this
	}

	pack { |start, orderedIds|
		var cursor, sequence;

		if (regions.isEmpty) { ^this };
		cursor = start ?? { regions.first.resolvedStart };
		sequence = orderedIds.isNil.if {
			regions.copy
		} {
			orderedIds.collect({ |id|
				var region = this.regionAt(id);
				if (region.isNil) {
					("VoxArrangement: region % does not exist.".format(id)).warn;
				};
				region
			}).reject(_.isNil)
		};

		sequence.do({ |region, index|
			region.order_(index);
			if (region.move(cursor, metremap, \ticks).not) { ^this };
			region.refreshProvenance;
			cursor = cursor + region.duration;
		});

		nextOrder = nextOrder.max(sequence.size);
		this.sortRegions;
		this.touch;
		^this
	}

	shiftRegionsFrom { |tick, delta, exceptId|
		regions.do({ |region|
			if ((region.id != exceptId).and({ region.resolvedStart >= tick })) {
				if (region.move(region.resolvedStart + delta, metremap, \ticks)) {
					region.refreshProvenance;
				};
			};
		});
		^this
	}

	readoutRows {
		^regions.collect({ |region|
			(
				id: region.id,
				start: region.resolvedStart,
				end: region.resolvedStart + region.duration,
				duration: region.duration,
				mode: region.mode,
				anchor: region.anchor,
				source: region.vox.label
			)
		})
	}

	readout {
		var lines;

		if (regions.isEmpty) {
			^"VoxArrangement %: empty".format(label)
		};

		lines = List.new;
		lines.add("VoxArrangement % (% regions)".format(label, regions.size));
		regions.do({ |region, index|
			lines.add("%  %  %..%  dur:%  mode:%  anchor:%  source:%".format(
				index,
				region.id,
				region.resolvedStart,
				region.resolvedStart + region.duration,
				region.duration,
				region.mode,
				region.anchor,
				region.vox.label
			));
		});
		^lines.join("\n")
	}

	postReadout {
		this.readout.postln;
		^this
	}

	move { |id, start, anchor|
		var region;

		region = this.regionAt(id);
		if (region.isNil) {
			("VoxArrangement: region % does not exist.".format(id)).warn;
			^this
		};

			if (region.move(start, metremap, anchor).not) { ^this };
			region.refreshProvenance;
			this.sortRegions;
			this.touch;
		^this
	}

	remove { |id|
		var region;

		region = this.regionAt(id);
		if (region.isNil) {
			("VoxArrangement: region % does not exist.".format(id)).warn;
			^this
		};

		regions.remove(region);
		this.touch;
		^this
	}

	clear {
		regions.clear;
		this.touch;
		^this
	}

	metremap_ { |newMap|
		if (newMap.isKindOf(MetreMap).not) {
			"VoxArrangement: metremap must be a MetreMap.".warn;
			^this
		};

		if (metremap.notNil.and({ newMap.tpqn != metremap.tpqn })) {
			"VoxArrangement: replacement metre map must preserve tpqn.".warn;
			^this
		};

		metremap = newMap.copy;
			regions.do({ |region|
				region.refreshPositionAnchor(metremap);
				region.refreshProvenance;
			});
		this.sortRegions;
		this.touch;
		^this
	}

	sortRegions {
		regions = regions.sort({ |left, right|
			if (left.resolvedStart == right.resolvedStart) {
				left.order < right.order
			} {
				left.resolvedStart < right.resolvedStart
			}
		});
		^this
	}

	trimEvent { |event, range|
		var startTick, finishTick, left, right, result;

		startTick = event[\absTime];
		finishTick = startTick + event[\dur];
		result = List.new;

		if ((finishTick <= range.start).or({ startTick >= range.end })) {
			result.add(event.deepCopy);
			^result
		};

		if (startTick < range.start) {
			left = event.deepCopy;
			left[\dur] = range.start - startTick;
			left[\position] = TimeConverter.ticksToPos(left[\absTime], metremap);
			result.add(left);
		};

		if (finishTick > range.end) {
			right = event.deepCopy;
			right[\absTime] = range.end;
			right[\dur] = finishTick - range.end;
			right[\position] = TimeConverter.ticksToPos(right[\absTime], metremap);
			result.add(right);
		};

		^result
	}

	clearRange { |tracks, labels, range|
		labels.do({ |voiceLabel|
			var events;

			events = tracks[voiceLabel];
			if (events.notNil) {
				tracks[voiceLabel] = events.collect({ |event|
					this.trimEvent(event, range)
				}).flat;
			};
		});
		^tracks
	}

	addPlacedVoxes { |tracks, voxes|
		voxes.do({ |vox|
			var events;

			events = tracks[vox.label] ?? { List.new };
			events = events.addAll(vox.events.collect(_.deepCopy));
			tracks[vox.label] = events;
		});
		^tracks
	}

	out {
		var tracks, rendered, placed, labels, range, voiceLabels;

		tracks = IdentityDictionary.new;

		regions.do({ |region|
			placed = region.placedVoxes(metremap);
			labels = placed.collect(_.label).asSet.asArray;
			range = region.resolvedRange;

			if (region.mode == \interweave) {
				this.clearRange(tracks, labels, range);
			};

			if (region.mode == \splice) {
				this.clearRange(tracks, tracks.keys.asArray, range);
			};

			this.addPlacedVoxes(tracks, placed);
		});

		voiceLabels = tracks.keys.asArray.sort({ |left, right|
			left.asString < right.asString
		});

		rendered = voiceLabels.collect({ |voiceLabel|
			var events;

			events = tracks[voiceLabel].sort({ |left, right|
				left[\absTime] < right[\absTime]
			});
			Vox.new(events, metremap, voiceLabel, Dictionary.new, this)
		});

		^VoxProvenance.stampTree(
			VoxMulti.new(rendered, metremap, label, metadata, this),
			this.provenance
		)
	}

	exportJSON { |path|
		^VoxExport.writeJSON(this, path)
	}

	exportMusicXML { |path, python = "python3", keepJSON = false|
		^VoxExport.writeMusicXML(this, path, python, keepJSON)
	}

	commit { |commitLabel|
		var state;

		if (historyPointer < (history.size - 1)) {
			history = history.copyRange(0, historyPointer);
		};

		state = (
			regions: regions.collect(_.copy),
			metremap: metremap.copy,
			nextOrder: nextOrder,
			label: commitLabel
		);
		history.add(state);
		historyPointer = history.size - 1;
		^this
	}

	restoreState { |state|
		regions = state[\regions].collect(_.copy).as(List);
		metremap = state[\metremap].copy;
		nextOrder = state[\nextOrder];
		this.sortRegions;
		this.touch;
		^this
	}

	undo {
		if (historyPointer <= 0) {
			"VoxArrangement: nothing to undo.".warn;
			^this
		};

		historyPointer = historyPointer - 1;
		^this.restoreState(history[historyPointer])
	}

	redo {
		if (historyPointer >= (history.size - 1)) {
			"VoxArrangement: nothing to redo.".warn;
			^this
		};

		historyPointer = historyPointer + 1;
		^this.restoreState(history[historyPointer])
	}
}

VoxRegion {
	var <id, <vox, <start, <resolvedStart, <anchor, <mode, <order;
	var <originTick, <duration;

	*new { |id, vox, start, metremap, anchor = \ticks, mode = \overlay, order = 0|
		^super.new.init(id, vox, start, metremap, anchor, mode, order)
	}

		init { |idArg, voxArg, startArg, metremap, anchorArg, modeArg, orderArg|
		id = idArg;
		vox = voxArg.copy;
		mode = modeArg;
		order = orderArg;

		if ([\overlay, \interweave, \splice].includes(mode).not) {
			("VoxRegion %: unknown compositing mode %.".format(id, mode)).warn;
			^nil
		};

		originTick = this.events.collect({ |event| event[\absTime] }).minItem;
		if (originTick.isNil) {
			("VoxRegion %: cannot place an empty snapshot.".format(id)).warn;
			^nil
		};

		duration = this.events.collect({ |event|
			event[\absTime] + event[\dur]
		}).maxItem - originTick;

		if (this.move(startArg, metremap, anchorArg).not) { ^nil };
			^this
		}

		refreshProvenance {
			var recipe = vox.provenance;
			var inputRecipe = (recipe[\op] == \arrangementRegion).if {
				recipe[\input]
			} {
				recipe
			};

			vox = VoxProvenance.stamp(vox, VoxProvenance.boundary(
				\arrangementRegion,
				(id: id, start: start, resolvedTick: resolvedStart, anchor: anchor, mode: mode),
				inputRecipe
			));
			^this
		}

	events {
		if (vox.isKindOf(VoxMulti)) {
			^vox.asArray.collect(_.events).flat
		};
		^vox.events
	}

	sourceVoxes {
		if (vox.isKindOf(VoxMulti)) {
			^vox.asArray
		};
		^[vox]
	}

	move { |newStart, metremap, newAnchor|
		var selectedAnchor;

		selectedAnchor = newAnchor ?? { anchor ?? { \ticks } };
		if ([\ticks, \pos].includes(selectedAnchor).not) {
			("VoxRegion %: anchor must be ticks or pos.".format(id)).warn;
			^false
		};

		if ((selectedAnchor == \pos).and({ newStart.isKindOf(Pos).not })) {
			("VoxRegion %: position anchors require Pos.".format(id)).warn;
			^false
		};

		start = newStart.respondsTo(\copy).if({ newStart.copy }, { newStart });
		anchor = selectedAnchor;
		resolvedStart = this.resolveStart(start, metremap);

		if (resolvedStart.isNil) {
			("VoxRegion %: start must be a Pos or tick Integer.".format(id)).warn;
			^false
		};
		^true
	}

	resolveStart { |placement, metremap|
		if (placement.isKindOf(Integer)) { ^placement };
		if (placement.isKindOf(Pos)) {
			^TimeConverter.posToTicks(placement, metremap)
		};
		^nil
	}

	refreshPositionAnchor { |metremap|
		if (anchor == \pos) {
			resolvedStart = TimeConverter.posToTicks(start, metremap);
		};
		^this
	}

	resolvedRange {
		^TimeRange.fromTicks(resolvedStart, resolvedStart + duration)
	}

	placedVoxes { |metremap|
		^this.sourceVoxes.collect({ |sourceVox|
			var shifted;

			shifted = sourceVox.events.collect({ |event|
				var moved;

				moved = event.deepCopy;
				moved[\absTime] = resolvedStart + (event[\absTime] - originTick);
				moved[\position] = TimeConverter.ticksToPos(moved[\absTime], metremap);
				moved
			});

			Vox.new(shifted, metremap, sourceVox.label, sourceVox.metadata, sourceVox.source)
		})
	}

	copy {
		^VoxRegion.new(id, vox, start, vox.metremap, anchor, mode, order)
			.resolvedStart_(resolvedStart)
	}

	resolvedStart_ { |ticks|
		resolvedStart = ticks;
		^this
	}

	order_ { |newOrder|
		order = newOrder;
		^this
	}
}
