#!/usr/bin/env python3
"""Full original HTML reader observations; expectations never come from Kotlin."""
import argparse,collections,gzip,hashlib,json,re,subprocess,itertools,random,os
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-html-reader-reference';target=root/'core/src/test/resources/html-reader/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
src=(root/'upstream/pandoc/test/Tests/Readers/HTML.hs').read_text().replace('module Tests.Readers.HTML (tests) where','module OriginalReaderTests where')
for line in ['import Test.Tasty\n','import Test.Tasty.QuickCheck\n','import Test.Tasty.Options (IsOption(defaultValue))\n']:src=src.replace(line,'')
src=src.replace('import Tests.Helpers','import ReaderCases');assert (folder/'OriginalReaderTests.hs').read_text()==src
smart=(root/'upstream/pandoc/src/Text/Pandoc/Parsing/Smart.hs').read_text().replace('module Text.Pandoc.Parsing.Smart','module OriginalSmart').replace('import Text.Pandoc.Parsing.General', 'import Text.Pandoc.Parsing hiding\n  (apostrophe,dash,doubleCloseQuote,doubleQuoteEnd,doubleQuoteStart,ellipses,singleQuoteEnd,singleQuoteStart,smartPunctuation)')
general=(root/'upstream/pandoc/src/Text/Pandoc/Parsing/General.hs').read_text();begin=general.index('isSpaceChar ::');last='isSpaceChar _    = False\n'
helper=general[begin:general.index(last,begin)+len(last)]
assert (folder/'OriginalSmart.hs').read_text()==smart+'\n-- General.isSpaceChar is not re-exported by Text.Pandoc.Parsing.\n'+helper
with gzip.open(root/'core/src/test/resources/latex-reader/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/latex-reader/data.jsonl.gz','tools/import-html-reader-fixtures.py','upstream/pandoc/src/Text/Pandoc/Readers/HTML.hs','upstream/pandoc/test/Tests/Readers/HTML.hs','upstream/pandoc/src/Text/Pandoc/Parsing/Smart.hs','vendor/tagsoup-0.14.8/src/Text/HTML/TagSoup.hs','vendor/tagsoup-0.14.8/src/Text/HTML/TagSoup/Render.hs','vendor/tagsoup-0.14.8/src/Text/HTML/TagSoup/Options.hs','vendor/tagsoup-0.14.8/src/Text/HTML/TagSoup/Entity.hs','upstream/pandoc-types/src/Text/Pandoc/Arbitrary.hs','upstream/pandoc/src/Text/Pandoc/UTF8.hs','vendor/text-2.1.2/src/Data/Text/Encoding.hs','vendor/text-2.1.2/src/Data/Text/Internal/Encoding.hs','vendor/text-2.1.2/src/Data/Text/Internal/StrictBuilder.hs','vendor/text-2.1.2/src/Data/Text/Encoding/Error.hs']
sources+=sorted(str(x.relative_to(root)) for x in folder.rglob('*') if x.is_file())
html_files=sorted((root/'upstream/pandoc/test').glob('*.html'))
nested_html_files=sorted(x for x in (root/'upstream/pandoc/test').rglob('*.html') if x not in html_files)
sources+=[str(x.relative_to(root)) for x in html_files+nested_html_files]+['upstream/pandoc/test/html-reader.native']
command_files=[]
for f in sorted((root/'upstream/pandoc/test/command').glob('*.md')):
 if any(re.search(r'(?:-f\s*|-r\s*|--from(?:=|\s+))html\b',m[1]) for m in re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',f.read_text(),re.M|re.S)):command_files.append(f)
sources+=[str(f.relative_to(root)) for f in command_files]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def links():return {s:os.readlink(root/s) for s in sorted(set(sources)) if (root/s).is_symlink()}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['sourceLinks']==links() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert m['originalQuickCheckDriver']==[dict(name='Round trip',requested=25,success=True,observations=25)]
 assert all(c['expected']['verdict'] for c in cs if c['group']=='original-quickcheck-driver-and-shrinking-witnesses')
 print('Verified',len(cs),'full original HTML reader observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
exposed=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX','Text.Pandoc.Class.CommonState']
for name in exposed:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+n+',\n' for n in exposed))
files=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0')[:-1]
for name in files:assert (args.prepared/'pandoc'/name).read_bytes()==(changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()),name
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def ask(v):
 proc.stdin.write(json.dumps(v,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,v
 out=json.loads(line);assert not isinstance(out,dict) or 'exception' not in out,(str(v)[:500],out)
 return out
opts=ask(['defaults']);fmtopts=ask(['format-defaults']);tests=ask(['tests']);cs=[]
def read(text,group,options=None,**extra):
 request=['read',options if options is not None else opts,[[[[],1,1],list(map(ord,text))]],[],[]]
 cs.append(dict(group=group,request=request,expected=ask(request),**extra))
math_extensions={'dollars':'tex_math_dollars','double dollars':'tex_math_dollars','parentheses':'tex_math_single_backslash','brackets':'tex_math_single_backslash','double-backslash parentheses':'tex_math_double_backslash','double-backslash brackets':'tex_math_double_backslash'}
for name,text,golden,doc in tests:
 o=opts.copy();parts=name.split('/')
 if parts[0]=='math groups and comments':o['extensions']=[math_extensions[parts[1]]]
 elif parts[0] in ['main','header']:o['extensions']=['native_divs']
 read(''.join(map(chr,text)),'all-original-unit-tests',o,name=name,nativeGolden=golden,nativeStandalone=bool(doc['meta']))
 assert cs[-1]['expected']['document']==doc,(name,cs[-1]['expected'],doc)
for f in html_files:
 read(f.read_text(),'all-original-html-files',fmtopts,source=str(f.relative_to(root)))
 if f.name=='html-reader.html':
  native=ask(['native',True,cs[-1]['expected']['document']]);golden=(root/'upstream/pandoc/test/html-reader.native').read_text()
  assert native==golden or native+'\n'==golden,(native[:500],golden[:500])
  cs[-1].update(nativeGolden=golden.removesuffix('\n'),nativeStandalone=True)
for f in command_files:
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',f.read_text(),re.M|re.S)):
  fmt=re.search(r'(?:-f\s*|-r\s*|--from(?:=|\s+))(html(?:[+-][a-z_]+)*)',m[1])
  if not fmt:continue
  o=fmtopts.copy();extensions=set(o['extensions'])
  for sign,name in re.findall(r'([+-])([a-z_]+)',fmt[1][4:]):
   if sign=='+':extensions.add(name)
   else:extensions.discard(name)
  o['extensions']=sorted(extensions)
  read(m[2],'all-original-command-inputs',o,source=str(f.relative_to(root)),index=index,command=m[1])
for source in ['', 'a','a\nb','a\u0080\u0081\u009f','<p> </p>','<div>hello<p>para</div>tail','<main id=x><p>main</p></main><main>second</main>', '<p>"hello \'world\'" -- ... -4</p>','<q cite="a">outer<q>inner</q>end</q>',r'<p>\begin{math}x\end{math} \eqref{x} \ref{x}</p>', '<p>$x$ \\(x\\) \\\\(x\\\\)</p>','<svg class="fa-fw" width="20"><path d="x"/></svg>', '<iframe src="missing.html"></iframe>', '<div role="doc-endnotes"><ol><li id="n">note</li></ol></div><a role="doc-noteref" href="#n">1</a>', '<a role="doc-noteref" href="#missing">1</a>','<table><tr><th>A<th>B<tr><td>1<td>2</table>', '<pre> a\n b\t \nc</pre>']:
 for extensions in [[],fmtopts['extensions'],['raw_html'],['epub_html_exts','native_divs','native_spans','auto_identifiers'],['smart'],['smart','old_dashes'],['tex_math_dollars','tex_math_single_backslash','tex_math_double_backslash','raw_tex']]:
  o=opts.copy();o['extensions']=extensions;read(source,'extension-state-and-grammar-boundaries',o)
for tag in ['a','q','bdo','code','tt','samp','var','span','kbd','abbr','strong','em','s','small','u','sup','sub','style','script','svg','math','input','unknown','div','section','header','aside','main','figure','pre','ul','ol','dl','table','iframe']:
 for text in ['','a <em>b</em> c','\n  x\t y\n','<p>a</p><p>b</p>']:
  read('<'+tag+' id="x" class="c" data-test="v">'+text+'</'+tag+'>','every-element-dispatch',fmtopts)
for cp in range(128,160):read('<p>'+chr(cp)+'</p>','all-invalid-html-control-characters')
for name,n,values in ask(['properties']):
 assert n==25
 for seed,size,doc,verdict in values:
  req=['roundtrip',doc];expected=ask(req);assert expected['verdict']==verdict
  cs.append(dict(group='original-roundtrip-property',name=name,seed=seed,size=size,request=req,expected=expected))
originals=[x for x in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if x['accepted']]
for original in originals:
 req=['roundtrip',original['json']];cs.append(dict(group='original-roundtrip-over-entire-document-corpus',source=original['path'],request=req,expected=ask(req)))
# Success/failure effects use only an injected openURL instance; the actual
# original reader and every other PandocPure method are unchanged.
for mime in ['text/html','text/html; charset=utf-8','TEXT/HTML','image/png','application/octet-stream',None]:
 for content in [b'<p>frame</p>',b'\xef\xbb\xbf<p>a\r\nb</p>',b'<a role="doc-noteref" href="#missing">n</a>',b'\xff',b'\xc2\x00']:
  request=['read-urls',fmtopts,[[[[],1,1],list(map(ord,'<iframe src="frame"></iframe> tail'))]],[[list(map(ord,'frame')),list(content),mime]]]
  cs.append(dict(group='iframe-url-effects-mime-bom-cr-and-host-utf8-exceptions',request=request,expected=ask(request)))
for count in [1,4,5,6,10]:
 urls=[[list(map(ord,str(i))),list(('<iframe src="'+str(i+1)+'"></iframe> level'+str(i)).encode()),'text/html'] for i in range(count)]
 request=['read-urls',fmtopts,[[[[],1,1],list(map(ord,'<iframe src="0"></iframe>'))]],urls]
 cs.append(dict(group='iframe-depth-and-nested-report-order',request=request,expected=ask(request)))
for prefix in range(256):
 request=['utf8-row',prefix];cs.append(dict(group='all-65536-strict-utf8-byte-pairs',request=request,expected=ask(request)))
for data in [[],[0xef,0xbb,0xbf],list(b'\xef\xbb\xbf\rabc\r\n'),[0xef,0xbb,0xbf,0xef,0xbb,0xbf],[0xf0,0x9f,0x98,0x80],[0xe2,0x82],[0xf0,0x9f,0x98],[0xe0,0x80,0x80],[0xed,0xa0,0x80],[0xf4,0x90,0x80,0x80],[0x61,0x0d,0xc2,0x00],[0xef,0xbb,0xbf,0xff]]:
 request=['utf8',data];cs.append(dict(group='strict-utf8-bom-cr-prefix-and-codepoint-boundaries',request=request,expected=ask(request)))
rng=random.Random(0x48544d4c)
for _ in range(512):
 data=[rng.randrange(256) for _ in range(rng.randrange(12))]
 request=['utf8',data];cs.append(dict(group='strict-utf8-random-and-error-order',request=request,expected=ask(request)))
source_texts=['','<div>word</div> tail','<DIV ID="a">word</DIV>','<div><div>x</div></div>','<div>no end','<p>x<p>y','<div>\n<em>x</em>\n</div>tail','<!--a-->tail','<!--bad -- > comment-->','<!DOCTYPE html>','<!bad>','<?xml version="1.0"?>','<br/>tail','<www.boe.es/buscar/act.php?id=x>','<https://example.org>','<foo bar.baz=x>','<foo 9name=x>','<a x="a>b">x</a>','<div>😀\t\nend</div>','<span>\n</span>','</div>','<div a="unterminated','<!---->','<math:math>x</math:math>']
for kind,source,predicate in itertools.product(['tag','balanced'],source_texts,['any','block','inline','comment','div','never']):
 for split in [False,True]:
  chunks=[[[[],1,1],list(map(ord,source))]] if not split else [[[[97],3,4],list(map(ord,source[:3]))],[[[98],7,2],list(map(ord,source[3:]))]]
  o=opts.copy();o['strip-comments']=split
  req=['source',kind,o,chunks,predicate];cs.append(dict(group='all-public-html-sources-apis-and-chunk-positions',request=req,expected=ask(req)))
smart_inputs=['',"'",'"',"'word'",'"word"',"'word'next",'"outer \'inner\'"',"' ", '"\n',"'\t", "'\u00a0x'", "'\u2003x'",'...tail','..','--','---','----','-1','-a',"a'b",'’','“','\n','\t','😀']
for kind,source,context,last,extensions in itertools.product(['single-start','single-end','double-start','double-end','apostrophe','close','ellipsis','dash','smart'],smart_inputs,range(3),[False,True],[[],['smart'],['smart','old_dashes']]):
 o=opts.copy();o['extensions']=extensions
 req=['smart',kind,o,[[[[],1,1],list(map(ord,source))]],context,last];cs.append(dict(group='complete-smart-exports-context-last-position-and-extensions',request=req,expected=ask(req)))
def pt(s):return list(map(ord,s))
for name in ['br','BR','hr','img','İMG','use','path','rect','script','SCRIPT','style','raw','?xml','!DOCTYPE','div','x:y','é','\u1c89']:
 for text in ['','&"<>','--><!-- x -->','😀\n\t','<script>&raw</script>']:
  for attributes in [[],[[pt(''),pt('')]],[[pt('a'),pt('')],[pt(''),pt(text)],[pt('x'),pt(text)],[pt('x'),pt('duplicate')]]]:
   tags=[[0,pt(name),attributes],[2,pt(text)],[3,pt(text)],[1,pt(name)],[0,pt(name),attributes],[1,pt(name)],[4,pt('warning')],[5,2,3]]
   req=['render',tags];cs.append(dict(group='complete-tagsoup-render-and-canonicalization-dependencies',request=req,expected=ask(req)))
qc_driver=[]
for name,n,success,observations in ask(['quickcheck-properties']):
 qc_driver.append(dict(name=name,requested=n,success=success,observations=len(observations)))
 for doc,verdict in observations:
  req=['roundtrip',doc];expected=ask(req);assert expected['verdict']==verdict
  cs.append(dict(group='original-quickcheck-driver-and-shrinking-witnesses',name=name,request=req,expected=expected))
for f in nested_html_files:
 read(f.read_text(),'remaining-original-nested-html-files',fmtopts,source=str(f.relative_to(root)))
assert qc_driver==[dict(name='Round trip',requested=25,success=True,observations=25)]
for kind,source,context,last,extensions in itertools.product(['single-quoted','double-quoted'],smart_inputs,range(3),[False,True],[[],['smart'],['smart','old_dashes']]):
 o=opts.copy();o['extensions']=extensions
 req=['smart',kind,o,[[[[],1,1],list(map(ord,source))]],context,last];cs.append(dict(group='original-smart-direct-quoted-exports',request=req,expected=ask(req)))
req=['utf8-all-scalars'];cs.append(dict(group='strict-utf8-every-original-encoded-haskell-char',request=req,expected=ask(req)))
proc.stdin.close();proc.wait();assert proc.returncode==0
if target.exists():
 with gzip.open(target,'rt') as f:next(f);old=[json.loads(x) for x in f]
 assert cs[:len(old)]==old,'Existing independent observations changed'
m={'sources':hashes(),'sourceLinks':links(),'cases':len(cs),'caseHash':digest(cs),'groups':dict(collections.Counter(c['group'] for c in cs)),'originalUnitCount':len(tests),'originalQuickCheckDriver':qc_driver}
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as gz:
 for c in [m,*cs]:gz.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'full original HTML observations,',len(tests),'original units, hash',m['caseHash'],flush=True)
