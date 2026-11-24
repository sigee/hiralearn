"""Copies glyphs for the given code points from a donor font into a target font (decomposed, scaled to the
target's units per em). Used to give Noto Serif JP the Hungarian ő/Ő/ű/Ű it lacks."""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.recordingPen import DecomposingRecordingPen

target_path, donor_path, out_path = sys.argv[1:4]
codepoints = [0x150, 0x151, 0x170, 0x171]

target = TTFont(target_path)
donor = TTFont(donor_path)
scale = target['head'].unitsPerEm / donor['head'].unitsPerEm
donor_glyphs = donor.getGlyphSet()
donor_cmap = donor.getBestCmap()

order = target.getGlyphOrder()
glyf = target['glyf']
hmtx = target['hmtx']
for cp in codepoints:
    src = donor_cmap[cp]
    name = f'uni{cp:04X}'
    pen = TTGlyphPen(None)
    # Drawing through the glyph set decomposes composites (o + hungarumlaut) into plain outlines.
    rec = DecomposingRecordingPen(donor_glyphs)
    donor_glyphs[src].draw(rec)
    rec.replay(TransformPen(pen, (scale, 0, 0, scale, 0, 0)))
    glyph = pen.glyph()
    if name not in order:
        order.append(name)
    glyf[name] = glyph
    adv, _ = donor['hmtx'][src]
    glyph.recalcBounds(glyf)
    hmtx[name] = (round(adv * scale), getattr(glyph, 'xMin', 0))
    for table in target['cmap'].tables:
        if table.isUnicode():
            table.cmap[cp] = name
target.setGlyphOrder(order)
if 'vmtx' in target:
    for cp in codepoints:
        name = f'uni{cp:04X}'
        target['vmtx'][name] = (target['head'].unitsPerEm, 0)
target.save(out_path)
print('added', [hex(c) for c in codepoints], 'scale', scale)
