#!/usr/bin/env python3
"""Original ordered shrink, exhaustive Text.pack Char, and demand observations."""
import argparse, gzip, hashlib, json, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'core/src/test/resources/pandoc-types'
PROJECT='tools/haskell-types-shrink-closure-reference'
SOURCES=[__file__.removeprefix(str(ROOT)+'/'),*[PROJECT+'/'+n for n in ['TypesShrinkClosureReference.hs','kotdoc-types-shrink-closure-reference.cabal','cabal.project','cabal.project.freeze','README.md']],*['upstream/pandoc-types/src/Text/Pandoc/'+n+'.hs' for n in ['Arbitrary','Definition','Builder']], 'vendor/quickcheck-2.16.0.0/Arbitrary.hs', 'core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesShrink.kt','core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesShrinkClosureTest.kt','core/src/main/resources/io/heapy/kotdoc/regex-char-data.json','core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json', *['core/src/main/kotlin/io/heapy/kotdoc/parsing/'+n+'.kt' for n in ['RegexCharacters','ParserCharacters']], 'licenses/pandoc-types-BSD-3-Clause.txt','licenses/QuickCheck-BSD-3-Clause.txt']
def sha(b):return hashlib.sha256(b).hexdigest()
def hashes():return {p:sha((ROOT/p).read_bytes()) for p in SOURCES}
def validate(raw,chars,demand):
 rows=[json.loads(x) for x in raw.splitlines()];c=[json.loads(x) for x in chars.splitlines()];d=[json.loads(x) for x in demand.splitlines()]
 assert len(rows)==134 and len(c)==1088 and len(d)==6
 assert sum(r['limit'] is not None for r in rows)==7
 assert all(r['truncated'] is False for r in rows if r['limit'] is None)
 assert len([r for r in rows if r['kind']=='inline' and r['name'] in ['Str','Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps','Quoted','Cite','Code','Space','SoftBreak','LineBreak','Math','RawInline','Link','Image','Note','Span']])==20
 assert len([r for r in rows if r['kind']=='block' and not r['name'].startswith(('length','definition','deep'))])==14
 assert [(r['start'],r['end']) for r in c]==[(s,s+1023) for s in range(0,0x110000,1024)]
 assert {r['name']:r['value'] for r in d}=={'removed-all-size':0,'removed-half-size':50000,'retained-element-error':None,'last-small-removal-size':8,'definition-first-size':99999,'citation-noop-list-count':1}
 return rows,c,d
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');a=p.parse_args();m=DEST/'shrink-closure-manifest.json';names=['shrink-closure','shrink-char-sweep','shrink-demand']
 if a.check_source:
  saved=json.loads(m.read_text());assert saved['sources']==hashes()
  raws=[gzip.decompress((DEST/(n+'.jsonl.gz')).read_bytes()) for n in names];assert saved['payloads']=={n:sha(r) for n,r in zip(names,raws)};validate(*raws);print('Ordered shrink134, exhaustive Char1114112/1088batches, demand6: sources/payload verified');return
 if not a.oracle:p.error('--oracle required')
 raw=subprocess.check_output([a.oracle]);demand=subprocess.check_output([a.oracle,'demand']);proc=subprocess.Popen([a.oracle,'chars'],stdout=subprocess.PIPE)
 rawHash=hashlib.sha256();batches=[]
 for line in proc.stdout:
  rawHash.update(line);r=json.loads(line);expected=json.dumps(r.pop('expected'),separators=(',',':')).encode();r['sha256']=sha(expected);batches.append(json.dumps(r,separators=(',',':')))
 assert proc.wait()==0
 chars=('\n'.join(batches)+'\n').encode();raws=[raw,chars,demand];validate(*raws)
 for n,r in zip(names,raws):(DEST/(n+'.jsonl.gz')).write_bytes(gzip.compress(r,mtime=0))
 m.write_text(json.dumps({'sources':hashes(),'payloads':{n:sha(r) for n,r in zip(names,raws)},'nativeCharRawSha256':rawHash.hexdigest(),'finiteCases':134,'charInputs':1114112,'charBatches':1088,'demandCases':6},indent=2)+'\n');print('Captured ordered134 + Char1114112 + demand6')
if __name__=='__main__':main()
