import sys, re, os, math
src, out = sys.argv[1], sys.argv[2]
TOK = re.compile(r'[MmCcSsLlZzHhVv]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?')
def bez(p0,p1,p2,p3,n=12):
    return [tuple(((1-t)**3)*a+3*((1-t)**2)*t*b+3*(1-t)*t*t*c+t**3*d for a,b,c,d in zip(p0,p1,p2,p3)) for t in [i/n for i in range(1,n+1)]]
def flatten(d):
    toks = TOK.findall(d); i=0; cur=(0,0); start=(0,0); pts=[]; cmd=None; lastc2=None
    def num():
        nonlocal i; v=float(toks[i]); i+=1; return v
    while i < len(toks):
        if re.match(r'[A-Za-z]', toks[i]): cmd=toks[i]; i+=1
        rel = cmd.islower(); C=cmd.upper()
        def pt():
            x,y=num(),num(); return (cur[0]+x,cur[1]+y) if rel else (x,y)
        if C=='M':
            cur=pt(); start=cur; pts.append(cur); cmd='l' if rel else 'L'; lastc2=None
        elif C=='L':
            cur=pt(); pts.append(cur); lastc2=None
        elif C=='C':
            c1=pt(); c2=pt(); e=pt(); pts+=bez(cur,c1,c2,e); lastc2=c2; cur=e
        elif C=='S':
            c1=(2*cur[0]-lastc2[0],2*cur[1]-lastc2[1]) if lastc2 else cur
            c2=pt(); e=pt(); pts+=bez(cur,c1,c2,e); lastc2=c2; cur=e
        elif C=='Z':
            cur=start; pts.append(cur)
        else: raise ValueError(cmd)
    return pts
lines=[]
for f in sorted(os.listdir(src)):
    if not f.endswith('.svg'): continue
    ch = chr(int(f[:5],16))
    txt = open(os.path.join(src,f),encoding='utf-8').read()
    sp = txt.split('kvg:StrokeNumbers')[0]
    ds = re.findall(r'<path[^>]*\sd="([^"]+)"', sp)
    strokes=[]
    for d in ds:
        p=flatten(d)
        # drop near-duplicate points
        q=[p[0]]
        for a in p[1:]:
            if math.dist(a,q[-1])>=1.0: q.append(a)
        if len(q)==1: q.append(p[-1])
        strokes.append(' '.join(f'{x:.1f},{y:.1f}' for x,y in q))
    lines.append(ch+'\t'+';'.join(strokes))
open(out,'w',encoding='utf-8',newline='\n').write('# Hiragana stroke data derived from KanjiVG (c) Ulrich Apel, CC BY-SA 3.0, https://kanjivg.tagaini.net\n# Format: <char>\t<stroke>;<stroke>... where a stroke is space-separated x,y points in a 109x109 box, in stroke order.\n'+'\n'.join(lines)+'\n')
print(len(lines))
