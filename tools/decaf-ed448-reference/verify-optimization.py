#!/usr/bin/env python3
"""Finite original-C optimizer observations, not an ISO-C UB proof."""
import argparse,gzip,hashlib,json,runpy,tempfile
from pathlib import Path
D=Path(__file__).resolve().parent;R=D.parents[1];p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args();module=runpy.run_path(str(R/'tools/import-decaf-ed448-fixtures.py'))
with gzip.open(R/'core/src/test/resources/crypto-decaf-ed448/data.jsonl.gz','rt')as f:next(f);rows=[(i,json.loads(v))for i,v in enumerate(f)]
rows=[(i,row)for i,row in rows if i%31==0 or'kat'in row['request']];result=[]
with tempfile.TemporaryDirectory(prefix='kotdoc-ed448-opt-')as tmp:
 for opt in ['0','3']:
  oracle=module['Oracle'](Path(tmp),opt)
  for index,row in rows:
   actual=oracle.observe(row['request']);assert actual==row['expected'],(opt,index,row['request']);result.append(dict(optimization=opt,row=index,ret=actual['ret'],sha256=hashlib.sha256(json.dumps(actual,sort_keys=True,separators=(',',':')).encode()).hexdigest()))
path=D/'OptimizationObservations.json'
if a.check:assert json.loads(path.read_text())==result
else:path.write_text(json.dumps(result,indent=2)+'\n')
print('PASS',len(result),'ordinary O0/O3 observations equal captured O2; UB portability not proven')
