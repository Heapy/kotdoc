#!/usr/bin/env python3
"""Capture original registered properties; never derive expectations from Kotlin."""
import argparse, gzip, hashlib, json, re, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'core/src/test/resources/pandoc-types'
PROJECT='tools/haskell-types-property-closure-reference'
SOURCE='upstream/pandoc-types/test/test-pandoc-types.hs'
FILES=[SOURCE,'tools/import-types-property-closure-fixtures.py',*[PROJECT+'/'+n for n in ['OriginalRemainingProperties.hs','TypesPropertyClosureReference.hs','kotdoc-types-property-closure-reference.cabal','cabal.project','cabal.project.freeze','README.md']],*['upstream/pandoc-types/src/Text/Pandoc/'+n+'.hs' for n in ['Arbitrary','Definition','Builder']], 'core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesRemainingPropertiesTest.kt', *['core/src/test/kotlin/io/heapy/kotdoc/ast/'+n+'.kt' for n in ['OriginalTypesPropertiesTest','UpstreamJsonTest','TablesTest']], *['core/src/test/resources/pandoc-types/'+n for n in ['properties-manifest.json','properties.jsonl.gz','json-fixtures.json']]]
def excerpt(s,a,b): return s[s.index(a):s.index(b)]
def verify_module():
 s=(ROOT/SOURCE).read_text(); module=(ROOT/PROJECT/'OriginalRemainingProperties.hs').read_text()
 header=module[:module.index('prop_roundtrip ::')]
 assert header=="""{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Verbatim original property bodies from pandoc-types/test/test-pandoc-types.hs.
-- Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; licenses/.
module OriginalRemainingProperties where
import Text.Pandoc.Arbitrary ()
import Text.Pandoc.Definition
import Text.Pandoc.Builder (normalizeTableHead, normalizeTableBody, normalizeTableFoot,
                            emptyCell, simpleFigureWith)
import qualified Text.Pandoc.Builder as Builder
import Data.Aeson (decode, encode)
import Test.QuickCheck (forAll, choose, Property, Arbitrary, Testable, arbitrary, Gen)
"""
 expected=header+'\n'.join(excerpt(s,a,b) for a,b in [('prop_roundtrip ::','testEncode ::'),('withWidth ::','t_tableNormExample ::'),('p_figureRepresentation ::','tests ::')])
 specs=[('widthNormIsIdempotent ::','p_tableNormHeadIdempotent ::','\\n a ->','sourceWidthNormIsIdempotent f n a'),('normIsSubset ::','p_tableNormHeadIsSubset ::','\\n a ->','sourceNormIsSubset f proj n a'),('testRowCorrectWidth ::','p_tableNormHeadRowWidth ::','\\n a ->','sourceRowCorrectWidth f proj n a'),('p_tableNormBodyIsSubset ::','p_tableNormFootIsSubset ::','\\n tb ->','sourceBodyIsSubset n tb'),('p_tableNormBodyRowWidth ::','p_tableNormFootRowWidth ::','\\n tb ->','sourceBodyRowWidth n tb'),('p_figureRepresentation ::','tests ::','\\figureCaption ->','sourceFigureRepresentation figureCaption')]
 for a,b,binder,name in specs: expected+='\n-- Mechanical exposure of the original forall body; body below is verbatim.\n'+name+' ='+excerpt(s,a,b).split(binder,1)[1]
 assert module==expected,'Original property excerpt or exposed body differs from pinned source'
def mappings():
 s=(ROOT/SOURCE).read_text().split('tests ::',1)[1]
 out={}
 for name in re.findall(r'testProperty\s+"([^" ]+(?: [^" ]+)*)"',s):
  assert name in {'p_'+op+' '+target+suffix for op in ['walk','query'] for target,suffix in [('inline','Trans' if op=='walk' else 'Query'),('block','Trans' if op=='walk' else 'Query'),('metaValue','Trans' if op=='walk' else 'Query'),('meta','Trans' if op=='walk' else 'Query')]} | {'p_'+op+'List '+target+suffix for op in ['walk','query'] for target in ['inlines','blocks'] for suffix in ['Trans' if op=='walk' else 'Query']} | {'p_tableNorm'+family+pred for family in ['Head','Body','Foot'] for pred in ['Idempotent','IsSubset','RowWidth']} | {'round-trip','p_figureRepresentation figure representation'}
  factory='originalTablePredicates' if name.startswith('p_tableNorm') else 'originalFigureRepresentation' if name.startswith('p_figureRepresentation') else 'originalJsonRoundTrip' if name=='round-trip' else 'originalWalkPredicates'
  cls='OriginalTypesPropertiesTest' if factory=='originalWalkPredicates' else 'OriginalTypesRemainingPropertiesTest'
  prefix=name.replace('p_walkList','p_walk').replace('p_queryList','p_query')
  out['pandoc-types/property/'+name]=cls+'.'+factory+': '+prefix+' *'
 for _,symbol in re.findall(r'testEncodeDecode\s+"([^"]+)"\s+(t_\w+)',s): out['pandoc-types/json/'+symbol]='UpstreamJsonTest.upstream exact JSON encoding and typed decoding: '+symbol+' encoding/decoding'
 out['pandoc-types/literal/t_tableSan']='TablesTest.upstream table sanitisation'
 out['pandoc-types/literal/t_tableNormExample']='TablesTest.upstream table normalization example'
 assert len(out)==81
 return out
def hashes(): return {f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in FILES}
def validate(raw,suite):
 rows=[json.loads(x) for x in raw.splitlines()]; reports=[json.loads(x) for x in suite.splitlines()]
 assert len(rows)==5305 and len(reports)==11
 for kind,count in [('head',1500),('body',1500),('foot',1500),('figure',305),('json',500)]: assert sum(r['kind']==kind for r in rows)==count
 for kind in ['head','body','foot']:
  assert {(r['seed'],r['width']) for r in rows if r['kind']==kind}=={(seed,width) for seed in range(1,101) for width in range(2,17)}
 assert {r['seed'] for r in rows if r['kind']=='json'}==set(range(1,501))
 assert {r['witness'] for r in rows if r['kind']=='figure'}=={'','empty','space','softbreak','linebreak','emptyStr'}
 for r in rows:
  for k in ['idempotent','subset','rowWidth'] if r['kind'] in ['head','body','foot'] else ['predicate']: assert r[k] is True
 assert all(r['success'] is True and r['tests']==1000 for r in reports)
 assert len({r['name'] for r in reports})==11
 return rows,reports
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');a=p.parse_args()
 verify_module(); mapping=mappings(); manifest=DEST/'remaining-property-manifest.json'
 names=['remaining-properties','remaining-property-suite']
 if a.check_source:
  saved=json.loads(manifest.read_text());assert saved['sources']==hashes();assert saved['registrations']==mapping
  raws=[gzip.decompress((DEST/(n+'.jsonl.gz')).read_bytes()) for n in names]
  assert saved['payloads']=={n:hashlib.sha256(r).hexdigest() for n,r in zip(names,raws)}
  validate(*raws);print('Original81 registrations; 5305 native records, 11 original properties x1000 trials; source/payload verified');return
 if not a.oracle:p.error('--oracle required')
 raws=[subprocess.check_output([a.oracle]),subprocess.check_output([a.oracle,'suite'])];validate(*raws)
 for n,r in zip(names,raws):(DEST/(n+'.jsonl.gz')).write_bytes(gzip.compress(r,mtime=0))
 manifest.write_text(json.dumps({'kotlinAssertions':14316,'originalRegisteredCases':81,'registrations':mapping,'sources':hashes(),'payloads':{n:hashlib.sha256(r).hexdigest() for n,r in zip(names,raws)}},indent=2)+'\n')
 print('Captured5305 original records and11 original QuickCheck property reports')
if __name__=='__main__':main()
