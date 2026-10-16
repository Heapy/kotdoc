#!/usr/bin/env python3
"""Finite source caller-range observations; not a universal invariant proof."""
import argparse,gzip,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('corpus',type=Path);p.add_argument('--check',action='store_true');a=p.parse_args();profiles={};tables=[];variants={};total=0
with gzip.open(a.corpus,'rt')as f:
 manifest=json.loads(next(f))
 for line in f:
  row=json.loads(line);r=row['request'];e=row['expected'];profile=f"field{r['bits']}/api{r['scalarBits']}/neon{r['vectorMasks32']}";op=r['op'];v=profiles.setdefault(profile,{}).setdefault(op,dict(rows=0,fieldWrites=0,labels={}));v['rows']+=1;v['fieldWrites']+=e['fieldAudit']['count'];total+=e['fieldAudit']['count']
  for label,s in e['fieldAudit']['stats'].items():
   t=v['labels'].setdefault(label,dict(writes=0,maxWord='0000000000000000',aboveRadix=0));t['writes']+=s['calls'];t['maxWord']=max(t['maxWord'],s['maxWord']);t['aboveRadix']+=s['aboveRadix']
  if 'compiledTableComparison'in row:tables.append(dict(profile=profile,operation=op,**row['compiledTableComparison']))
  if 'ordinaryMatchesNormalized'in row:
   v=variants.setdefault(profile,{}).setdefault(op,dict(rows=0,matches={k:0 for k in row['ordinaryMatchesNormalized']}));v['rows']+=1
   for k,value in row['ordinaryMatchesNormalized'].items():v['matches'][k]+=int(value)
for profile,ops in profiles.items():assert len(ops)==17
artifact=dict(kind='finite actual caller field-write ranges; universal caller range invariant remains unproven',profiles=profiles,totalFieldWrites=total,compiledTableComparisons=tables,originalUndefinedShiftCompilerObservations=variants,assertionProbes=manifest['assertionProbes'])
path=Path(__file__).resolve().parent/'CallerRangeObservations.json'
if a.check:assert json.loads(path.read_text())==artifact
else:path.write_text(json.dumps(artifact,indent=2)+'\n')
print('PASS caller range observations',total,'field writes;',len(tables),'full table comparisons;',len(manifest['assertionProbes']),'assertion probes')
