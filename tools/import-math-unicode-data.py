#!/usr/bin/env python3
"""Translate the original ordered mathematical Unicode table, including duplicates."""
import argparse,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=(root/'vendor/texmath-0.13.3.1/src/Text/TeXMath/Unicode/ToUnicode.hs').read_text()
def char(s):
 if s.startswith('\\'):return int(s[1:])
 assert len(s)==1,s
 return ord(s)
body=src.split('unicodeTable =',1)[1]
rows=re.findall(r"\(\((Text\w+),'([^']+)'\),'([^']+)'\)",body)
assert len(rows)==len(re.findall(r'\(\(Text',body))
data='// Generated from texmath 0.13.3.1; © 2009 John MacFarlane, GPL-2.0-or-later.\npackage io.heapy.kotdoc.math\n\ninternal val mathUnicodeTable = listOf(\n'+',\n'.join(f'    Triple(MathTextType.{t},{char(a)},{char(b)})' for t,a,b in rows)+'\n)\n'
target=root/'core/src/main/kotlin/io/heapy/kotdoc/math/MathUnicodeData.kt'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
if args.check:assert target.read_text()==data
else:target.write_text(data)
print('Verified' if args.check else 'Translated',len(rows),'ordered original Unicode rows')
