#!/usr/bin/env python3
"""Finite 25 dictionary / 225 method audit; original Haskell observations only."""
import argparse,collections,gzip,hashlib,itertools,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DEST=ROOT/'core/src/test/resources/pandoc-types';PROJECT='tools/haskell-types-order-closure-reference'
KINDS=['pandoc','meta','metaValue','style','delim','format','rowHeadColumns','alignment','colWidth','row','head','body','foot','caption','cell','rowSpan','colSpan','block','quote','math','inline','citation','mode','inlines','blocks']
APIS=['Pandoc','Meta','MetaValue','ListNumberStyle','ListNumberDelim','Format','RowHeadColumns','Alignment','ColWidth','Row','TableHead','TableBody','TableFoot','Caption','Cell','RowSpan','ColSpan','Block','QuoteType','MathType','Inline','Citation','CitationMode','Inlines','Blocks']
METHODS=['eq','ne','compare','lt','le','gt','ge','minimum','maximum']
SEEDS=['pandoc','meta','inline','block','style','delim','alignment','row','head','body','foot','caption','cell','quote','math','citation','mode','inlines','blocks']
def sha(b):return hashlib.sha256(b).hexdigest()
def compact(v):return json.dumps(v,ensure_ascii=True,separators=(',',':'))
def examples():
 rows=[json.loads(x) for x in (DEST/'read-examples.jsonl').read_text().splitlines()]
 rename={'tableHead':'head','tableBody':'body','tableFoot':'foot','citationMode':'mode'}
 return [dict(e,kind=rename.get(e['kind'],e['kind'])) for e in rows if rename.get(e['kind'],e['kind']) in KINDS]
def requests():
 cases=[];groups=collections.defaultdict(list)
 def add(name,**r):cases.append(dict(name=name,request=r))
 for e in examples():groups[e['kind']].append(e['input'])
 assert sum(map(len,groups.values()))==79 and set(groups)==set(KINDS)
 for k in KINDS:
  for i,(a,b) in enumerate(itertools.product(groups[k],repeat=2)):add('constructor-cross/'+str(i),operation='relations',kind=k,left=a,right=b)
  for i,s in enumerate(groups[k]):add('immediate-field/'+str(i),operation='fields',kind=k,input=s)
 for k in SEEDS:
  for size in [0,1,3,8,30]:
   for seed in range(1,41):add('source-generator-pair',operation='seeded',kind=k,size=size,seed=seed)
 for k,elem in [('inlines','inline'),('blocks','block')]:
  values=['Many {unMany = fromList []}']+['Many {unMany = fromList ['+s+']}' for s in groups[elem]]
  if k=='inlines':values+=['Many {unMany = fromList [Str "a",Str "b"]}','Many {unMany = fromList [Str "ab"]}']
  else:values+=['Many {unMany = fromList [Plain [],HorizontalRule]}']
  for i,(a,b) in enumerate(itertools.product(values,repeat=2)):add('Many-constructor-sequence/'+str(i),operation='relations',kind=k,left=a,right=b)
  for probe,method in itertools.product(['empty-bad','length-bad','both-bad','later-different','later-equal','left-bad','right-bad'],METHODS):add('Many-demand/'+probe,operation='partial',kind=k,probe=probe,method=method)
 def table(n):return 'Table ("",[],[]) (Caption Nothing []) [(AlignDefault,ColWidth ('+n+'))] (TableHead ("",[],[]) []) [] (TableFoot ("",[],[]) [])'
 numbers=['NaN','-NaN','Infinity','-Infinity','0.0','-0.0','1e-300']
 for k in ['inlines','blocks']:
  def many(n):return 'Many {unMany = fromList ['+('Note ['+table(n)+']' if k=='inlines' else table(n))+']}'
  for i,(a,b) in enumerate(itertools.product(numbers,repeat=2)):add('Many-IEEE/'+str(i),operation='relations',kind=k,left=many(a),right=many(b))
 for k,ctor in [('rowHeadColumns','RowHeadColumns'),('rowSpan','RowSpan'),('colSpan','ColSpan')]:
  for a,b in itertools.product([-2**63,-1,0,1,2**63-1],repeat=2):add('newtype-Int64-bounds',operation='relations',kind=k,left=ctor+' ('+str(a)+')',right=ctor+' ('+str(b)+')')
 def q(s):return '"'+''.join('\\'+str(ord(c))+'\\&' for c in s)+'"'
 for a,b in itertools.product(['a','A','ß','SS','ς','σ','Ꭰ','ꭰ'],repeat=2):add('Format-casefold-caller',operation='relations',kind='format',left='Format '+q(a),right='Format '+q(b))
 for k in KINDS:
  for i,s in enumerate(groups[k]):add('competing-field-order/'+str(i),operation='fields',kind=k,input=s,pairOrder=True)
 return cases
SOURCES=['tools/import-types-order-closure-fixtures.py','core/src/main/kotlin/io/heapy/kotdoc/ast/AstComparison.kt',*['core/src/main/kotlin/io/heapy/kotdoc/ast/'+n+'.kt' for n in ['Pandoc','Builder','Format','UnicodeText','PandocRead','AstShow']],'core/src/main/resources/io/heapy/kotdoc/case-mappings.json','core/src/test/kotlin/io/heapy/kotdoc/ast/OriginalTypesOrderingClosureTest.kt','core/src/test/kotlin/io/heapy/kotdoc/ast/AstComparisonTest.kt','core/src/test/resources/ast-order/data.jsonl.gz','core/src/test/resources/pandoc-types/read-examples.jsonl','core/src/test/resources/pandoc-types/read-census.json','upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc-types/src/Text/Pandoc/Builder.hs','upstream/pandoc-types/src/Text/Pandoc/Arbitrary.hs','vendor/ghc-ast-order-9.12.2/GHC/Classes.hs','vendor/ghc-ast-order-9.12.2/GHC/Internal/Maybe.hs','vendor/containers-syntax-0.7/src/Data/Map/Internal.hs','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/ghc-prim-BSD-3-Clause.txt','licenses/Containers-BSD-3-Clause.txt']
def hashes():
 paths=SOURCES+[str(p.relative_to(ROOT)) for p in (ROOT/PROJECT).iterdir() if p.is_file()]
 return {p:sha((ROOT/p).read_bytes()) for p in sorted(paths)}
def audit(rows):
 with gzip.open(ROOT/'core/src/test/resources/ast-order/data.jsonl.gz','rt') as f:oldmeta=json.loads(next(f));old=[json.loads(x) for x in f]
 assert oldmeta['cases']==len(old)==75470
 rename={'tableHead':'head','tableBody':'body','tableFoot':'foot','citationMode':'mode','pandoc-blocks':'pandoc','caption-short':'caption','tableBody-head':'body'}
 oldby=collections.defaultdict(list)
 for i,r in enumerate(old):
  req=r['request'];kind=rename.get(req[1],req[1]) if len(req)>1 else None
  if req[0] in ['relations','width','text'] and kind in KINDS and set(METHODS)<=set(r['expected']):oldby[kind].append(i)
 slots={};field_count=0;field_pair_count=0
 for k,api in zip(KINDS,APIS):
  ids=[i for i,r in enumerate(rows) if r['request']['kind']==k and r['request']['operation'] in ['relations','seeded']]
  assert ids
  slots[k]={method:dict(api='AstComparison.'+api+'.'+dict(eq='equal',ne='notEqual',lt='less',le='lessEqual',gt='greater',ge='greaterEqual').get(method,method),new_case_indices=ids,new_field_case_indices=[i for i,r in enumerate(rows) if r['request']['kind']==k and r['request']['operation']=='fields'],existing_native_case_count=len(oldby[k]),existing_native_witness_indices=oldby[k][:8]) for method in METHODS}
 for r in rows:
  v=json.loads(r['expectedWire']);op=r['request']['operation']
  if op=='fields':
   assert isinstance(v,list),v
   if r['request'].get('pairOrder'):field_pair_count+=len(v)
   else:field_count+=len(v)
  elif op=='partial':assert isinstance(v,(dict,str,bool)),v
  else:
   if op=='seeded':v=v['expected']
   assert set(v)==set(METHODS),v
 census=json.loads((DEST/'read-census.json').read_text())
 assert {c['kind'] for c in census}==set(KINDS)
 assert field_count==sum(x['arity'] for c in census for x in c['constructors'])==92
 assert field_pair_count==sum(x['arity']*(x['arity']-1)//2 for c in census for x in c['constructors'])
 policies={}
 for c in census:
  k=c['kind'];name=APIS[KINDS.index(k)]
  typ=('Many Inline' if k=='inlines' else 'Many Block' if k=='blocks' else name)
  eq=('handwritten Text.toCaseFold' if k=='format' else 'coerced Seq Eq: length then list elements' if k in ['inlines','blocks'] else 'stock/coerced source field Eq')
  order=('handwritten compare citationHash' if k=='citation' else 'handwritten compare Text.toCaseFold' if k=='format' else 'coerced Seq compare/list plus original Ord defaults' if k in ['inlines','blocks'] else 'source derived/coerced Ord dictionary; separate generated less where present')
  policies[k]=dict(source_type=typ,source='upstream/pandoc-types/src/Text/Pandoc/'+('Builder.hs' if k in ['inlines','blocks'] else 'Definition.hs'),constructors_in_source_order=c['constructors'],eq_contract=eq,ord_contract=order,
                  defaults_source='vendor/ghc-ast-order-9.12.2/GHC/Classes.hs',compiler_evidence=PROJECT+'/GhcDerivedEqOrd.txt')
 assert len(slots)==25 and sum(map(len,slots.values()))==225
 return dict(dictionary_method_slots=slots,source_dictionaries=policies,immediate_field_observations=field_count,competing_field_pair_observations=field_pair_count,existing_native_cases=75470,new_requests=len(rows),methods=METHODS)
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--draft',action='store_true');p.add_argument('--check-source',action='store_true');a=p.parse_args()
 payload=DEST/'order-closure.jsonl.gz';manifest=DEST/'order-closure-manifest.json'
 if a.check_source:
  raw=gzip.decompress(payload.read_bytes());rows=[json.loads(x) for x in raw.splitlines()];m=json.loads(manifest.read_text())
  assert m['sources']==hashes() and m['payloadSha256']==sha(raw) and m['audit']==audit(rows)
  assert [dict(name=r['name'],request=r['request']) for r in rows]==requests()
  assert m['nativeResponsesSha256']==sha(('\n'.join(r['expectedWire'] for r in rows)+'\n').encode())
  print('EqOrd25dictionaries/225methods,',len(rows),'neworiginalrequests;',m['audit']['immediate_field_observations'],'fieldobservations and75470existingcases verified');return
 if not a.oracle:p.error('--oracle required')
 proc=subprocess.Popen([a.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True);rows=[]
 for r in requests():
  proc.stdin.write(compact(r['request'])+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,r
  rows.append(dict(r,expectedWire=line.removesuffix('\n')))
 proc.stdin.close();assert proc.wait()==0
 raw=('\n'.join(compact(r) for r in rows)+'\n').encode();result=audit(rows)
 if a.draft:
  target=Path('/tmp/kotdoc-types-order-closure-draft/resources/pandoc-types');target.mkdir(parents=True,exist_ok=True);(target/'order-closure.jsonl.gz').write_bytes(gzip.compress(raw,mtime=0))
 else:
  payload.write_bytes(gzip.compress(raw,mtime=0));manifest.write_text(json.dumps(dict(sources=hashes(),payloadSha256=sha(raw),nativeResponsesSha256=sha(('\n'.join(r['expectedWire'] for r in rows)+'\n').encode()),audit=result),indent=2)+'\n')
 print('Captured',len(rows),'originalrequests;',result['immediate_field_observations'],'immediatefields;225slots')
if __name__=='__main__':main()
