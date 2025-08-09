# vox_to_musicxml.py
from music21 import stream, note, meter, duration, clef

def vox_json_to_musicxml(json_path, xml_path):
    # Import the real stdlib json explicitly as pyjson
    import json as pyjson

    with open(json_path, 'r') as f:
        data = pyjson.load(f)

    tpqn = int(data['tpqn'])
    events = sorted(data.get('events', []), key=lambda e: int(e['start']))
    ts_regions = data.get('metreMap', {}).get('regions', [{'at': 0, 'ts': '4/4'}])

    # Build score/part first (so 'p' exists before we insert TS)
    sc = stream.Score()
    part = stream.Part(id=str(data.get('label', 'part')))
    part.insert(0, clef.TrebleClef())

    # Insert time signatures at quarterLength offsets
    for r in ts_regions:
        try:
            ts_str = r['ts']
            ts = meter.TimeSignature(ts_str)
            offset_ql = float(int(r.get('at', 0))) / tpqn
            part.insert(offset_ql, ts)
        except Exception as e:
            # Fallback to a single default 4/4 if parsing fails
            print(f"Warning: could not parse TS {r} ({e}); defaulting to 4/4 at 0")
            part.insert(0, meter.TimeSignature('4/4'))
            break

    # Helper to convert ticks -> quarterLength
    to_ql = lambda ticks: float(int(ticks)) / tpqn

    # Emit rests for gaps and notes for events
    cur = 0
    for ev in events:
        start = int(ev['start'])
        dur = int(ev['dur'])
        if start > cur:
            part.append(note.Rest(quarterLength=to_ql(start - cur)))
            cur = start
        n = note.Note()
        n.pitch.midi = int(ev['pitch'])
        n.duration = duration.Duration(to_ql(dur))
        vel = ev.get('vel')
        if vel is not None:
            n.volume.velocity = int(vel)
        part.append(n)
        cur = start + dur

    sc.append(part)
    sc.write('musicxml', fp=xml_path)
    print(f"Wrote MusicXML to {xml_path}")

# Example CLI usage:
# python -c "from vox_to_musicxml import vox_json_to_musicxml; vox_json_to_musicxml('vox_demo.json','vox_demo.musicxml')"
