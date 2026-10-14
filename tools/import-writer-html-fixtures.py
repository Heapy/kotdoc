#!/usr/bin/env python3
"""Observe original CSS/XML/writer-HTML functions, never the Kotlin port."""
import argparse, atexit, collections, gzip, hashlib, itertools, json, os, random, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/writers/html-shared.jsonl.gz'
p = argparse.ArgumentParser(); p.add_argument('--oracle', default=os.environ.get('KOTDOC_WRITER_HTML_REFERENCE')); p.add_argument('--check-source', action='store_true'); args=p.parse_args()
sources = ['tools/import-writer-html-fixtures.py','tools/prepare-writer-html-reference.py','tools/import-pandoc-xml-data.py',
 'upstream/pandoc/src/Text/Pandoc/XML.hs','upstream/pandoc/src/Text/Pandoc/CSS.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs',
 'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs','upstream/pandoc/test/Tests/XML.hs','upstream/pandoc/test/Tests/Writers/HTML.hs',
 'vendor/tagsoup-0.14.8/src/Text/HTML/TagSoup/Entity.hs','core/src/test/resources/native/upstream.json',
 'core/src/main/resources/io/heapy/kotdoc/pandoc-xml-data.json','licenses/pandoc-GPL-2.0.md','licenses/Commonmark-BSD-3-Clause.txt',
 'licenses/tagsoup-BSD-3-Clause.txt','licenses/DocLayout-BSD-3-Clause.txt','licenses/pandoc-types-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt']
for folder in ['upstream/pandoc-types/src','vendor/doclayout-0.6','vendor/commonmark-0.3.1','vendor/xml-entity-text-read-2.1.2','tools/haskell-writer-html-reference']:
 sources += sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
def hashes(): return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs): return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
originals=json.loads((root/'core/src/test/resources/native/upstream.json').read_text())
attrs={}
def attributes(v):
 if isinstance(v,list):
  if len(v)==3 and isinstance(v[0],str) and isinstance(v[1],list) and all(isinstance(x,str) for x in v[1]) and isinstance(v[2],list) and all(isinstance(x,list) and len(x)==2 and all(isinstance(y,str) for y in x) for x in v[2]):
   yield v
  for x in v: yield from attributes(x)
 elif isinstance(v,dict):
  for x in v.values(): yield from attributes(x)
for row in originals:
 if not row['accepted']:continue
 found=list(attributes(row['json']))
 if found:sources.append('upstream/pandoc/'+row['path'])
 for a in found:attrs.setdefault(json.dumps(a,ensure_ascii=True,separators=(',',':')),a)
originalAttrs=list(attrs.values())
if args.check_source:
 subprocess.run(['python3',str(root/'tools/prepare-writer-html-reference.py'),'--check'],check=True)
 subprocess.run(['python3',str(root/'tools/import-pandoc-xml-data.py'),'--check'],check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert [c['input'][1] for c in cs if c.get('originalAttr')]==originalAttrs
 assert [cp for c in cs if c['op']=='unicode' for cp in c['input']]==list(range(0x110000))
 print(f'Verified {len(cs)} original CSS/XML/HTML captures, {len(originalAttrs)} distinct upstream attributes, all 1,114,112 Haskell Chars and {len(set(sources))} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(op,v,**extra):
 proc.stdin.write(json.dumps([op,v],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,v)
 cs.append(dict(op=op,input=v,expected=json.loads(line),**extra))
 if len(cs)%1000==0:print('Captured',len(cs),'observations',flush=True)
def points(s):return list(map(ord,s))
add('registries',None)
for a in originalAttrs:
 add('attrs',[points('span'),a],originalAttr=True)
 for k,v in a[2]:
  if k=='style':add('css',[['width','height','color','font-size'],points(v)],originalStyle=True)
data=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/pandoc-xml-data.json').read_text())
keys=list(dict.fromkeys(data['html4Attributes']+data['html5Attributes']+data['rdfaAttributes']))+['','invalid','markdown','data-test','aria-test','epub:type','xml:lang','DATA-test','ARIA-test','Label','a:b:c','strange key','key"=value','a\nkey']
for k in keys:
 add('attrs',[points('span'),['',[],[[k,'<&\"\'\x00\n\t\r']]]],attributePolicy=True)
 add('attrs',[points('span'),['',[],[[k.upper(),'value']]]],attributePolicy=True)
for k,v in data['entities']:
 add('xml-text',points('&'+k+';'),namedEntity=True)
 add('xml-text',points(k),namedLookup=True)
for n in [0,1,9,13,32,127,128,159,160,255,0xd7ff,0xd800,0xdbff,0xdc00,0xdfff,0xe000,0xfffd,0xfffe,0xffff,0x10000,0x10ffff,0x110000,10**100]:
 for spelling in [str(n),hex(n)[2:],hex(n)[2:].upper()]:
  prefix='' if spelling==str(n) else 'x'
  for suffix in ['', ';',';;','x',' ']:add('xml-text',points('#'+prefix+spelling+suffix),numericEdge=True)
for s in ['#x0x41','#X0X41','#x+41','#-1','#+65','&#65;','&amp','&amp ','&unknown;&amp;','&&amp;','&a\nb;','&amp;&amp;','&amp;;','&#0;','&#xD800;','a\x00b','\ud800\udc00','☃😀',"'\"<>&"]:
 add('xml-text',points(s),edge=True)
for prefix in ['#x0x','#x0X','#X0x','#X0X']:
 for tail in ['', '0','41','41;','41;;','D800;','110000;','41x',' 41','-41','+41']:
  add('xml-text',points(prefix+tail),hexPrefix=True)
alphabet='ab:; \n'
props=['b','a','b','missing','']
for length in range(6):
 for chars in itertools.product(alphabet,repeat=length):add('css',[props,points(''.join(chars))],exhaustiveCss=True)
for s in ['color:red;garbage','color:red;:garbage','p:q:r','p:;',' : ;','a:b;\u00a0c:d;','a:b;\x85c:d;','a:b;\u200bc:d;','a:b;\vc:d;','a:b;\fc:d;','color: red; color:blue;',';a:b','a: \u00a0 ;']:
 add('css',[['color','a','c','p'],points(s)],cssEdge=True)
 for key in ['color','a','new']:
  add('style',[[key,'<&new>'],[['id','1'],['style',s],['style','later:original;'],['id','2']]],styleEdge=True)
for a in [[],[['id','x']], [['Style','x']], [['style','']], [['style','color:red;']]]:
 add('style',[['color','blue'],a],styleEdge=True)
for a in ['AlignDefault','AlignLeft','AlignRight','AlignCenter']:add('alignment',{'t':a})
rng=random.Random(0xC55A77)
whitespace=' \r\n\t\v\f\u0085\u00a0\u1680\u2000\u2007\u2028\u2029\u202f\u205f\u3000'
alphabet='abcd0123:;'+whitespace+'\x00é😀'
for i in range(2000):
 s=''.join(rng.choice(alphabet) for _ in range(rng.randrange(81)))
 add('css',[rng.sample(['a','b','c','ab','','not found'],rng.randrange(7)),points(s)],seededCss=True)
 if i<300:add('style',[[rng.choice(['a','b','ab','','new']),s[:5]],[['class','foo'],['style',s],['style','untouched']]],seededStyle=True)
def genDoc(depth=0):
 kind=rng.choice(['literal','raw','space','cr','newline','empty','after','blank'] if depth>2 else ['literal','raw','space','cr','newline','empty','after','blank','concat','prefix','flush','before'])
 if kind=='literal':return [kind,points(rng.choice(['','a b','\n','😀中\t','x\ny','\ud800','\x00']))]
 if kind=='raw':return [kind,rng.choice([-1,0,1,10,2**63-1]),points(rng.choice(['','ab','😀','\ud800']))]
 if kind=='concat':return [kind,genDoc(depth+1),genDoc(depth+1)]
 if kind=='prefix':return [kind,rng.choice(['','> ','\n']),genDoc(depth+1)]
 if kind in ['flush','before']:return [kind,genDoc(depth+1)]
 if kind=='after':return [kind,rng.choice(['','after','\n'])]
 if kind=='blank':return [kind,rng.choice([-1,0,1,2])]
 return [kind]
for i in range(300):
 a=[rng.choice(['','id','a\nb','\x00\"<&','😀']),rng.choice([[],[''],['one','two'],['a\nb','<&']]),[[rng.choice(keys),rng.choice(['','text','\"<&\'\x00\n\r\t','😀'])] for _ in range(rng.randrange(5))]]
 add('attrs',[points(rng.choice(['span','','span\nx','😀','\ud800'])),a],seededAttrs=True)
 add('xml-tag',[points(rng.choice(['tag','','x\ny','a<&','😀','\ud800'])),a[2],genDoc()],seededTag=True)
for start in range(0,0x110000,4096):add('unicode',list(range(start,min(start+4096,0x110000))),exhaustiveUnicode=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),oracle='GHC 9.12.2 original Pandoc 3.12.1 XML; extracted original CSS and writer HTML bodies; Commonmark 0.3.1; DocLayout 0.6; TagSoup 0.14.8')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} CSS/XML/writer observations; SHA {meta["caseHash"]}',flush=True)
