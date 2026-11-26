"""Gives a font the Hungarian ő/Ő/ű/Ű it lacks, built from the font's own glyphs: the base letter (o, O, u, U) plus
two copies of the acute accent taken from its acute-accented form (ó, Ó, ú, Ú). Copying the letters from another font
(e.g. Noto Serif) gives them a different x-height, width and weight, so they stand out in words.

Usage: py tools/add_glyphs.py <in.ttf> <out.ttf>   (in and out may be the same file; existing ő/ű are replaced)
Run it on each Noto Serif JP weight after subsetting."""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.recordingPen import DecomposingRecordingPen, RecordingPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

# new code point -> (base letter, acute-accented letter)
GLYPHS = {0x151: ('o', 'oacute'), 0x150: ('O', 'Oacute'), 0x171: ('u', 'uacute'), 0x170: ('U', 'Uacute')}
# Each copy of the acute is narrowed a little, and the pair is spaced so the gap between them is this share of a
# narrowed accent's width (roughly how Noto Serif draws its double acute).
ACCENT_SCALE_X = 0.85
GAP = 0.35


def contours(glyph_set, name):
    """The glyph's outline split into contours, each as a list of recorded pen operations."""
    rec = DecomposingRecordingPen(glyph_set)
    glyph_set[name].draw(rec)
    result, current = [], []
    for op in rec.value:
        current.append(op)
        if op[0] in ('closePath', 'endPath'):
            result.append(current)
            current = []
    return result


def bounds(glyph_set, ops):
    pen = BoundsPen(glyph_set)
    rec = RecordingPen()
    rec.value = ops
    rec.replay(pen)
    return pen.bounds


def build(font, base, accented):
    gs = font.getGlyphSet()
    base_top = bounds(gs, [op for c in contours(gs, base) for op in c])[3]
    # The accent is whatever the accented letter has above the base letter.
    accent = [op for c in contours(gs, accented) if bounds(gs, c)[1] > base_top for op in c]
    x_min, _, x_max, _ = bounds(gs, accent)
    centre, width = (x_min + x_max) / 2, (x_max - x_min) * ACCENT_SCALE_X
    shift = (width * (1 + GAP)) / 2

    pen = TTGlyphPen(None)
    gs[base].draw(DecomposingPenAdapter(gs, pen))
    rec = RecordingPen()
    rec.value = accent
    for direction in (-1, 1):
        # Scale around the accent's own centre, then move it sideways.
        dx = centre - centre * ACCENT_SCALE_X + direction * shift
        rec.replay(TransformPen(pen, (ACCENT_SCALE_X, 0, 0, 1, dx, 0)))
    return pen.glyph()


class DecomposingPenAdapter(DecomposingRecordingPen):
    """Draws a possibly composite glyph into another pen as plain outlines."""

    def __init__(self, glyph_set, out):
        super().__init__(glyph_set)
        self.out = out

    def closePath(self):
        super().closePath()
        self._flush()

    def endPath(self):
        super().endPath()
        self._flush()

    def _flush(self):
        RecordingPen.replay(self, self.out)
        self.value = []


def main():
    in_path, out_path = sys.argv[1:3]
    font = TTFont(in_path)
    order = font.getGlyphOrder()
    glyf, hmtx = font['glyf'], font['hmtx']
    for cp, (base, accented) in GLYPHS.items():
        name = f'uni{cp:04X}'
        cmap_name = font.getBestCmap().get(cp)
        if cmap_name and cmap_name != name:
            name = cmap_name
        glyph = build(font, base, accented)
        if name not in order:
            order.append(name)
        glyf[name] = glyph
        glyph.recalcBounds(glyf)
        hmtx[name] = (hmtx[base][0], getattr(glyph, 'xMin', 0))
        if 'vmtx' in font:
            font['vmtx'][name] = font['vmtx'][base]
        for table in font['cmap'].tables:
            if table.isUnicode():
                table.cmap[cp] = name
    font.setGlyphOrder(order)
    font.save(out_path)
    print('built', ', '.join(chr(cp) for cp in GLYPHS), 'in', out_path)


if __name__ == '__main__':
    main()
