#!/usr/bin/env python3
"""Independent original yaml Value/warning/exception expectations; never runs Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/yaml-values/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_YAML_VALUES_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-yaml-values-fixtures.py','licenses/Yaml-BSD-2-Clause.txt','licenses/Libyaml-BSD-2-Clause.txt','licenses/Aeson-BSD-3-Clause.txt','core/src/test/resources/yaml-parser/data.jsonl.gz','vendor/aeson-pretty-dependency-2.2.3.0/src/Data/Aeson/Types/Internal.hs']
for folder in ['tools/haskell-yaml-values-reference','vendor/libyaml-clib-0.2.5','vendor/yaml-0.11.11.2','vendor/libyaml-0.1.4']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file())
def hashes():return {f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 errors={c['expected'].get('error',c['expected'].get('result',{}).get('error')) for c in cs if isinstance(c['expected'],dict)}
 assert set(['NonScalarKey','UnknownAlias','UnexpectedEvent','InvalidYaml','MultipleDocuments','AesonException','OtherParseException','NonStringKey','NonStringKeyAlias','CyclicIncludes','LoadSettingsException'])<=errors,errors
 assert any(c['expected'].get('result',{}).get('warnings') for c in cs if isinstance(c['expected'],dict))
 print(f'Verified {len(cs)} original yaml value/warning/error observations and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group,**flags):
 cs.append(dict(request=req,expected=ask(req),group=group,**flags))
 if len(cs)%1000==0:print('Captured',len(cs),'yaml value observations',flush=True)
def chars(s):return [ord(c) for c in s]
def bs(s):return list(s.encode())
def scalar(s='',tag='NoTag',style=1,anchor=None):return ['Scalar',bs(s),[tag],style,chars(anchor) if anchor is not None else None]
def stream(events):return [['StreamStart'],['DocumentStart']]+events+[['DocumentEnd'],['StreamEnd']]
def bytes_case(s,group,**flags):
 b=bs(s) if isinstance(s,str) else s
 for all_docs in [False,True]:add([all_docs,'bytes',b],group,**flags)
# Input inventory only: fresh outputs come from yaml, not previous scanner/parser results.
seen=set()
with gzip.open(root/'core/src/test/resources/yaml-parser/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  c=json.loads(line);b=c['request'][1];key=bytes(b)
  if key in seen:continue
  seen.add(key);flags={k:c[k] for k in ['path','literal','index','example'] if k in c}
  bytes_case(b,'syntax-'+c['group'],**flags)
for s in ['', '# foo\n# bar','foo: true','--- 1\n--- 2','--- &a true\n--- *a','--- &a key\n--- {*a: value}','--- &a [1]\n--- *a','--- &a {key: value}\n--- *a','--- &a true\n--- {*a: value}','- &a one\n- *a\n- &a two\n- *a','&a [*a]','&a {key: *a}','- &a [one]\n- &a [*a]\n- *a','<<: scalar\n<<: null','x: 1\nx: 2\nx: 3','a: [{b: 1, b: 2}, {b: 3, b: 4}]','outer: {inner: {? [key]: value}}']:
 bytes_case(s,'anchors-documents-errors')
for name in ['foo','true','null','1','0x10','<<','',"a.b","'\\",'a_b','你好','𝒜','\n','x\x00y']:
 for val in ['foo','true','null','1','[1, 2]','{x: value}']:
  escaped=json.dumps(name,ensure_ascii=False)
  bytes_case('first: &a '+val+'\n'+escaped+': one\n'+escaped+': two\nother: {*a: value}', 'keys-and-alias-types')
# Repeated merges intentionally replace the source's tracked merged-key set.
merges=['{x: 1}','{x: 2, y: 3}','[{x: 1}, {x: 2, y: 3}]','[{x: 1}, true, null, [9], {y: 2}]','[]','{}','true','null','1','text']
for merge,ending in itertools.product(merges,['','x: 4','x: 4\nx: 5','<<: {z: 9}\nx: 4','<<: {x: 9}\nx: 4','y: 4\ny: 5']):
 for prefix in ['', 'x: 0\n','x: 0\ny: 0\n']:
  bytes_case(prefix+'<<: '+merge+'\n'+ending,'merge-warning-order')
# Direct event streams exercise demand, malformed endings and lenient scalar UTF8.
base=[['StreamStart'],['StreamEnd'],['DocumentStart'],['DocumentEnd'],['Alias',chars('a')],scalar(),scalar('true',anchor='a'),scalar('key'),['SequenceStart',['NoTag'],0,None],['SequenceEnd'],['MappingStart',['NoTag'],0,None],['MappingEnd']]
for e in [[],*[[x] for x in base],stream([scalar('value')]),stream([['SequenceStart',['NoTag'],1,None],scalar('value'),['SequenceEnd']]),stream([['MappingStart',['NoTag'],2,None],scalar('key'),scalar('value'),['MappingEnd']])]:
 for n in range(len(e)+1):
  for all_docs in [False,True]:add([all_docs,'events',e[:n]],'event-prefixes')
rng=random.Random(0x0111112)
for _ in range(1600):
 events=[rng.choice(base) for _ in range(rng.randrange(1,12))]
 if rng.random()<.7:events=stream(events)
 for all_docs in [False,True]:add([all_docs,'events',events],'malformed-events')
for b in [[0],[0xff],[0xc0,0x80],[0xed,0xa0,0x80],[0xf4,0x90,0x80,0x80],[0xe2,0x82],[0xe2,0x28,0xa1],list(range(256))]:
 for style,tag in itertools.product(range(7),['NoTag','StrTag','IntTag','NullTag','MapTag']):
  event=['Scalar',b,[tag],style,chars('anchor')]
  for all_docs in [False,True]:add([all_docs,'events',stream([event])],'direct-scalar-bytes')
# Demand stops at stream end, leaving trailing malformed events untouched.
for trailing in base:
 for all_docs in [False,True]:add([all_docs,'events',stream([scalar('1')])+[trailing]],'unused-tail')
for level in [1,8,32,100,300]:bytes_case('['*level+'value'+']'*level,'nested-values')
# Full prettyPrintParseException branches, raw String characters, paths and Aeson Show.
raws=['','plain','\n','\x00\x0eH','你好','😀',chr(0xd800)+chr(0xdc00),'é123']
values=[['null'],['bool',True],['bool',False],['text',chars('é\n')],['number','-123',-4],['array',[['bool',True],['number','1',0]]],['object',[[chars('z'),['null']],[chars('a'),['bool',False]]]]]
paths=[[],[['index',-1]]]
for k in ['',"a.b","'\\",'foo','a_b','a1','你好','𝒜','a\n','1','Ⅻ','²']:
 paths.append([['key',chars(k)],['index',2],['key',chars('end')]])
for e in [['NonScalarKey'],['MultipleDocuments'],['CyclicIncludes'],['InvalidYaml',None]]:add(['exception',e],'exception-branches')
for raw in raws:
 for e in [['UnknownAlias',chars(raw)],['AesonException',chars(raw)],['OtherParseException',chars(raw)],['InvalidYaml',['Generic',chars(raw)]],['InvalidYaml',['Parse',chars(raw),[],[0,0,0]]],['InvalidYaml',['Parse',chars(raw),chars('while parsing'),[-1,-2,-3]]]]:
  add(['exception',e],'exception-raw-strings')
 for v in values:add(['exception',['NonStringKeyAlias',chars(raw),v]],'exception-value-show')
for path in paths:
 add(['exception',['NonStringKey',path]],'exception-json-path')
 for precedence in [0,10,11]:add(['warning',path,precedence],'warning-show')
for r,e in itertools.product([None]+base,[None]+base):add(['exception',['UnexpectedEvent',r,e]],'exception-event-show')
for raw in raws:
 for event in [scalar('a\n',tag='StrTag',style=3,anchor=''),['Scalar',[0,14,72,255,49],['UriTag',chars(raw)],6,chars(raw)],['SequenceStart',['UriTag',chars(raw)],2,chars(raw)],['MappingStart',['NoTag'],0,chars(raw)]]:
  add(['exception',['UnexpectedEvent',event,None]],'exception-tag-anchor-show')
 for e in [['UnknownAlias',chars(raw)],['NonStringKeyAlias',chars(raw),rng.choice(values)]]:
  add(['exception',['LoadSettingsException',chars(raw),['LoadSettingsException',chars('other.yaml'),e]]],'exception-nested-settings')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),oracle='Unchanged yaml 0.11.11.2 decodeHelper_/decodeAllHelper_ and prettyPrintParseException; libyaml actual decoding; frozen GHC 9.12.2 and Aeson 2.2.3.0 +ordered-keymap')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'yaml value observations; SHA',m['caseHash'],flush=True)
