#!/usr/bin/env python3
"""Independent finite inputs from already captured original constructor examples."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
names=['Pandoc','Meta','MetaValue','ListNumberStyle','ListNumberDelim','Format','RowHeadColumns','Alignment','ColWidth','Row','TableHead','TableBody','TableFoot','Caption','Cell','RowSpan','ColSpan','Block','QuoteType','MathType','Inline','Citation','CitationMode']
keys=['pandoc','meta','metaValue','style','delim','format','rowHeadColumns','alignment','colWidth','row','tableHead','tableBody','tableFoot','caption','cell','rowSpan','colSpan','block','quote','math','inline','citation','citationMode']
rename=dict(zip(keys,names));out=[]
for line in (ROOT/'core/src/test/resources/pandoc-types/read-examples.jsonl').read_text().splitlines():
 r=json.loads(line)
 if r['kind']not in rename:continue
 for op in ['rnf','force','deepseq','rwhnf']:out.append(dict(operation=op,kind=rename[r['kind']],input=r['input']))
assert set(r['kind']for r in out)==set(names)
(HERE/'requests.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=True,separators=(',',':'))+'\n'for r in out))
print(len(out),'finite source inputs')
