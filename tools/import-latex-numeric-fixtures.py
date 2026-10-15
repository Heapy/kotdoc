#!/usr/bin/env python3
"""Platform-specific original libm results; extracts pure declarations byte-for-byte."""
import argparse,gzip,hashlib,io,json,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-numeric-reference'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--archive',type=Path);p.add_argument('--results',type=Path);p.add_argument('--platform',choices=['macos-aarch64','linux-x86_64'],required=True);args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs').read_text();shared=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text()
header='{-# LANGUAGE OverloadedStrings #-}\n-- Exact pure declarations from pinned LaTeX.Parsing and Shared. GPL-2.0-or-later. Copyright (C) 2006-2024 John MacFarlane.\nmodule OriginalNumeric where\nimport Control.Monad (MonadPlus,mzero,guard)\nimport Data.Char (isSpace,isLetter,isDigit)\nimport Data.Text (Text)\nimport qualified Data.Text as T\nimport Data.List (dropWhileEnd,isSuffixOf)\nimport Numeric (showEFloat,showFFloat)\n\n'
assert (folder/'OriginalNumeric.hs').read_text()==header+shared[shared.index('safeRead ::'):]+'\n'+src[src.index('evalInteval ::'):src.index('\n\nstartsWithAlphaNum ::')+1]
base=root/'core/src/test/resources/latex-kernel/data.jsonl.gz'
with gzip.open(base,'rt') as f:
 next(f);cases=[c for line in f if (c:=json.loads(line))['request'][0] in ['integer','float','format','fixed','exponent']]
requests=[]
for c in cases:
 r=c['request'];nums=r[1] if isinstance(r[1],list) else r[1:]
 requests.append('('+json.dumps(r[0])+','+json.dumps(nums,separators=(',',':'))+')\n')
if args.archive:
 with tarfile.open(args.archive,'w') as tar:
  for name in ['NumericReference.hs','OriginalNumeric.hs']:tar.add(folder/name,arcname=name)
  data=''.join(requests).encode();info=tarfile.TarInfo('requests');info.size=len(data);tar.addfile(info,io.BytesIO(data))
 print('Prepared',len(requests),'original numeric requests');raise SystemExit
target=root/f'core/src/test/resources/latex-kernel/numeric-{args.platform}.jsonl.gz'
sources=['tools/import-latex-numeric-fixtures.py','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','licenses/pandoc-GPL-2.0.md','licenses/GHC-base-BSD-3-Clause.txt']
sources+=sorted(str(f.relative_to(root)) for f in folder.iterdir() if f.is_file())
sources+=['vendor/ghc-math-9.12.2/GHC/Internal/Real.hs','vendor/ghc-9.12.2/GHC/Internal/Float.hs']
hashes={s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));actual=[json.loads(line) for line in f]
 assert m['sources']==hashes and m['cases']==len(actual)==len(cases) and m['caseHash']==digest(actual) and m['platform']==args.platform
 assert [c['request'] for c in actual]==[c['request'] for c in cases]
 assert m['requestHash']==hashlib.sha256(''.join(requests).encode()).hexdigest()
 differences=[i for i,(a,b) in enumerate(zip(actual,cases)) if a['expected']!=b['expected']]
 assert differences==m['differencesFromFullMacOracle']
 assert all(actual[i]['request'][0]=='float' for i in differences)
 if args.platform=='macos-aarch64':assert not differences
 print('Verified',len(actual),args.platform,'original numeric observations;',len(differences),'libm-dependent differences');raise SystemExit
assert args.results
results=[json.loads(line) for line in args.results.read_text().splitlines()];assert len(results)==len(cases)
actual=[dict(request=c['request'],expected=v) for c,v in zip(cases,results)]
diffs=[i for i,(a,b) in enumerate(zip(actual,cases)) if a['expected']!=b['expected']]
assert all(actual[i]['request'][0]=='float' for i in diffs)
if args.platform=='macos-aarch64':assert not diffs
m=dict(sources=hashes,cases=len(actual),caseHash=digest(actual),platform=args.platform,requestHash=hashlib.sha256(''.join(requests).encode()).hexdigest(),differencesFromFullMacOracle=diffs)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for c in [m,*actual]:z.write((json.dumps(c,separators=(',',':'))+'\n').encode())
print('Captured',len(actual),args.platform,'original numeric observations;',len(diffs),'libm-dependent differences')
