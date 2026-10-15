#!/usr/bin/env python3
"""Independent original Table.hs and compactifyTable observations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-table-reference';parent=root/'tools/haskell-latex-inline-reference';target=root/'core/src/test/resources/latex-table/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle');p.add_argument('--prepared',type=Path);p.add_argument('--parsec-archive',type=Path);p.add_argument('--unicode-cache',type=Path);args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Table.hs').read_text()
exports='hline, lbreak, splitWordTok, parseAligns, parseTableRow, parseTableCell, cellAlignment, multirowCell, multicolumnCell, fixTableHead, fixTableBody, fixTableRows, fixTableRow, simpTable, addTableCaption, '
assert (folder/'OriginalTable.hs').read_text()==src.replace('module Text.Pandoc.Readers.LaTeX.Table','module OriginalTable',1).replace('  ( tableEnvironments','  ( '+exports+'tableEnvironments',1).replace('import Text.Pandoc.Readers.LaTeX.Parsing','import OriginalParsing',1)
assert (folder/'InlineObserver.hs').read_text()==(parent/'LaTeXInlineReference.hs').read_text().replace('module Main where','module InlineObserver where',1)
with gzip.open(root/'core/src/test/resources/latex-inline/data.jsonl.gz','rt') as f:parent_meta=json.loads(next(f))
sources=list(parent_meta['sources'])+['tools/import-latex-table-fixtures.py','core/src/test/resources/latex-inline/data.jsonl.gz','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Table.hs','upstream/pandoc/test/Tests/Shared.hs']
sources+=['upstream/pandoc-types/src/Text/Pandoc/'+name+'.hs' for name in ['Walk','Builder','Definition']]
sources+=sorted(str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file())
pattern=r'\\(?:begin\s*\{(?:longtable|table|tabular\*?|tabularx|supertabular\*?)\}|multirow|multicolumn|cmidrule|endfirsthead|endhead)'
command_files=[s for s in subprocess.check_output(['git','ls-files','test/command/*.md'],cwd=root/'upstream/pandoc').decode().splitlines() if re.search(pattern,(root/'upstream/pandoc'/s).read_text())]
sources+=['upstream/pandoc/'+s for s in command_files]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def links():return {s:os.readlink(root/s) for s in sorted(set(sources)) if (root/s).is_symlink()}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['sourceLinks']==links() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original Table/compactifyTable observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared and args.parsec_archive and args.unicode_cache
for package,version in [('unicode-transforms','0.4.0.1'),('unicode-data','0.9.0')]:
 base=root/'vendor'/(package+'-'+version)
 arc=args.unicode_cache/package/version/(package+'-'+version+'.tar.gz')
 assert hashlib.sha256(arc.read_bytes()).hexdigest()==re.search(r'SHA256: ([0-9a-f]{64})',(base/'KOTDOC-PROVENANCE').read_text())[1]
 with tarfile.open(arc) as tar:
  for path in base.rglob('*'):
   if path.is_file() and path.name!='KOTDOC-PROVENANCE':assert path.read_bytes()==tar.extractfile(package+'-'+version+'/'+str(path.relative_to(base))).read(),path
provenance=(root/'vendor/parsec-3.1.18.0/KOTDOC-PROVENANCE').read_text()
assert hashlib.sha256(args.parsec_archive.read_bytes()).hexdigest()==re.search(r'SHA256: ([0-9a-f]{64})',provenance)[1]
with tarfile.open(args.parsec_archive) as tar:
 for path in (root/'vendor/parsec-3.1.18.0').rglob('*'):
  if path.is_file() and path.name!='KOTDOC-PROVENANCE':assert path.read_bytes()==tar.extractfile('parsec-3.1.18.0/'+str(path.relative_to(root/'vendor/parsec-3.1.18.0'))).read(),path
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
exposed=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX','Text.Pandoc.Class.CommonState']
for name in exposed:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+n+',\n' for n in exposed))
files=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0')[:-1]
for name in files:assert (args.prepared/'pandoc'/name).read_bytes()==(changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()),name
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 (root.parent/'kotdoc-latex-table-last-request.json').write_text(json.dumps(dict(completed=len(cs),request=req)))
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group,allow_exception=False,**extra):
 expected=ask(req)
 assert allow_exception or not(isinstance(expected,dict) and 'exception' in expected),(req,expected)
 cs.append(dict(request=req,expected=expected,group=group,**extra))
def raw(s):return list(map(ord,s))
def tokens(s):return ask(['tokens',[raw('source'),2,3],raw(s)])
default=ask(['default-options'])
def initial():
 opts=default.copy();opts['extensions']=['latex_macros'];return [['options',opts]]
def parse(text,p,group,changes=None,allow_exception=False,**extra):
 add(['parse',initial() if changes is None else changes,None,[],tokens(text) if isinstance(text,str) else text,p],group,allow_exception,**extra)
cell=['cell'];fail=['fail',0,[]]
registries=ask(['registries']);add(['registries'],'all-seven-table-environments')
for spec in ['', 'l','r','c','p{5cm}','X','m{2cm}','b{3cm}','|l|c|r|','@{}r@{}l','@{x}r@{y}l','>{pre}l<{post}','>{\\emph}l','l>{x}r<{y}','p{.5\\linewidth}r','p{0.5\\linewidth}r','l{0\\linewidth}','r{-1\\linewidth}','c{Infinity\\linewidth}','p{NaN\\linewidth}','*{2}{r}','*2r','*{-1}{c}','*{0}{l}','*{abc}{l}','*{0x2}{lr}','*{9223372036854775808}{l}','*{18446744073709551616}{l}','*{2}{>{a}l<{b}}','>{bad','l<bad','q','😀',' l \n c ',r'\bgroup l\egroup']:
 for prefix,suffix in [('{','}'),('{ ',' }tail'),('',''),('{','')]:parse(prefix+spec+suffix,['aligns'],'alignment-width-repetition-prefix-suffix-and-error-boundaries')
for text in ['l','r','c','*','p{5cm}','|l|','||r||','c{ignored}','X','😀','ab','','{l}']:
 parse(text,['alignment'],'cell-alignment-symbols-ignored-arguments-and-errors')
for cp in [0,32,65,0x7f,0x300,0xd800,0xffff,0x10000,0x1f600,0x10ffff]:
 for value in [raw(chr(cp)),raw('a'+chr(cp)+'b'),[]]:
  ts=[[[raw('split'),-2,0],['Word'],value]]
  parse(ts,['split'],'word-token-splitting-scalar-empty-and-position-boundaries')
for command in ['hline','cline','cmidrule','toprule','bottomrule','midrule','endhead','endfirsthead','unknown']:
 for option in ['', '[2pt]','(lr)','[2pt](lr)','(bad','[bad','{x}']:
  parse(' \n\\'+command+option+'tail',['raw',['hline']],'all-rules-optional-width-parens-and-raw-errors')
for command in ['\\','tabularnewline','hline']:
 for option in ['', '[2pt]','[bad']:
  parse('\\'+command+option+' \n tail',['raw',['lbreak']],'line-break-optional-width-and-whitespace')
contents=['','A','A & B',r'\emph{A} & B',r'\verb|a&b| & C',r'$a&b$ & C',r'\parbox{3cm}{a&b} & C',r'\begin{math}a&b\end{math} & C',r'\multicolumn{2}{c}{One} & Two',r'\multirow{2}{5em}{One} & Two',r'\multicolumn{2}{|c|}{\multirow{2}{5em}{One}} & Two',r'\label{x}A & B',r'\caption{Title}A & B']
for name,spec,text,finish in itertools.product(registries,['','l','|rl|','>{pre}l<{post}r','p{0.5\\linewidth}r','*{2}{c}'],contents,['','\\\\','\\\\\\hline']):
 width='{0.8\\linewidth}' if name in ['tabular*','tabularx'] else ''
 source=width+'[t]{'+spec+'}'+text+finish+'\\end{'+name+'}tail'
 parse(source,['raw',['entry',raw(name),cell,cell]],'every-environment-columns-content-spans-caption-and-terminator')
for command in ['multirow','multicolumn']:
 for span in ['-2','0','1','2','3','0x2','abc','9223372036854775807','9223372036854775808','18446744073709551616']:
  arg='{c}' if command=='multicolumn' else '{5em}'
  for content in ['A','',r'\emph{B}',r'\multirow{2}{5em}{nested}','{bad']:
   source='\\'+command+'{'+span+'}'+arg+'{'+content+'}tail'
   for p in [[command,cell],['cell',cell]]:parse(source,p,'all-multirow-multicolumn-integer-wrapping-nesting-and-errors')
for source in [r'\multirow[c]{2}[3]{5em}[1in]{A}',r'\multirow[bad]{2}{5em}{A}',r'\multicolumn{2}{c}{\multirow{2}{5em}{A}}',r'\multirow{2}{5em}{\multicolumn{2}{c}{A}}']:
 parse(source,['cell',cell],'full-multirow-prototype-and-nesting-order')
for text in ['', 'x','{x}',r'\multirow{2}{5em}{x}',r'\multicolumn{2}{c}{x}']:
 for cb in [cell,['take',1,[{'t':'Para','c':[{'t':'Str','c':'callback'}]}],[['last-label','from-callback']]],['fail',0,[]],['fail',1,[['report',raw('callback failure')]]]]:
  for wrap in [lambda p:p,lambda p:['or',['try',p],['state']],lambda p:['look',p]]:
   parse(text,wrap(['cell',cb]),'cell-callback-state-failure-lookahead-and-backtracking')
for pref,suff in [('', ''),('P','S'),(r'\emph{','}'),(r'\label{p}',r'\label{s}'),('😀','é')]:
 ps=[[tokens(pref),tokens(suff)]]
 for text in ['x & y\\\\tail',r'\verb|a&b| & C\\',r'$a&b$ & C\\','\\end{tabular}']:
  parse(text,['raw',['row',raw('tabular'),ps,cell,cell]],'raw-cell-prefix-suffix-position-rewriting-and-protected-ampersands')
# Every represented original AST value is supplied independently to the generic
# compaction function and row repair, preserving the appropriate Walkable type.
docs=[x['json'] for x in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if x['accepted']]
shapes=collections.defaultdict(list)
inline_tags={'Str','Emph','Strong','Cite','Space','SoftBreak','Span','Note','Math','Link','Image','Code','Quoted','Underline','Superscript','Subscript','SmallCaps','Strikeout','LineBreak','RawInline'}
block_tags={'Plain','Para','LineBlock','CodeBlock','RawBlock','BlockQuote','OrderedList','BulletList','DefinitionList','Header','HorizontalRule','Table','Figure','Div'}
def visit(v):
 if isinstance(v,list):
  if v and all(isinstance(x,dict) and x.get('t') in inline_tags for x in v):shapes['inlines'].append(v)
  if v and all(isinstance(x,dict) and x.get('t') in block_tags for x in v):shapes['blocks'].append(v)
  for x in v:visit(x)
 elif isinstance(v,dict):
  if v.get('t') in inline_tags:shapes['inline'].append(v)
  if v.get('t') in block_tags:shapes['block'].append(v)
  if v.get('t')=='Table':
   a,c,cols,h,bs,f=v['c'];shapes['head'].append(h);shapes['body']+=bs;shapes['foot'].append(f)
   for row in h[1]+[r for b in bs for r in b[2]+b[3]]+f[1]:shapes['row'].append(row);shapes['cell']+=row[1]
  for x in v.values():visit(x)
for doc in docs:shapes['document'].append(doc);visit(doc)
for kind,values in shapes.items():
 for index,value in enumerate(values):add(['compact',kind,value],'all-original-ast-values-through-compactifyTable',source=kind,index=index)
for index,value in enumerate(shapes['blocks']):add(['compact','many-blocks',value],'original-builder-many-blocks-compaction-dictionary',index=index)
for value in shapes['head']:add(['fix-head',value],'all-original-table-head-row-repair')
for value in shapes['body']:add(['fix-body',value],'all-original-table-body-row-repair')
attr=['',[],[]]
def c(w=1,h=1,text='x'):return [attr,{'t':'AlignDefault'},h,w,[{'t':'Para','c':[{'t':'Str','c':text}]}]]
for hang_len in range(4):
 for hang in itertools.product([None,[1,1],[1,2],[2,3]],repeat=hang_len):
  for cells in [[],[c()],[c(2),c()],[c(1,3),c(),c()],[c(0),c(-1),c(2,2)]]:add(['fix-row',list(hang),cells],'finite-overhang-empty-padding-and-malformed-cell-widths')
rng=random.Random(9301302)
for index in range(1500):
 rows=[[attr,[c(rng.choice([-2,0,1,2,3,5]),rng.choice([-2,0,1,2,3,5]),str(j)) for j in range(rng.randrange(7))]] for _ in range(rng.randrange(8))]
 add(['fix-rows',rows],'mixed-malformed-and-spanning-table-row-repair',index=index)
for w,h in itertools.product([-9223372036854775808,-1,0,1,9223372036854775807],[0,1,2,3,9223372036854775807]):
 add(['fix-row-prefix',10,[None,None,None],[c(w,h),c()]],'int64-overhang-width-height-and-overflow-boundaries')
for hang in [[[9223372036854775807,3],[1,3]], [[-9223372036854775808,2],[1,3]], [[0,0],[-1,-9223372036854775808]]]:
 add(['fix-row-prefix',10,hang,[c(2,3),c()]],'int64-occupied-overhang-sum-and-height-overflow-boundaries')
# Caption traversal and numbering retain labels/removal through nested values.
base_table={'t':'Table','c':[attr,[None,[]],[[{'t':'AlignLeft'},{'t':'ColWidthDefault'}]],[attr,[]],[[attr,0,[],[[attr,[c()]]]]],[attr,[]]]}
for label,caption,chapters,number in itertools.product([None,'id'],[None,[[{'t':'Str','c':'short'}],[{'t':'Para','c':[{'t':'Str','c':'long'}]}]]],[False,True],[[0],[2,4],[],[9223372036854775807]]):
 changes=initial()+[['caption',caption],['last-label',label],['chapters',chapters],['header',[3,5]],['table',number]]
 parse('', ['caption',[base_table,{'t':'BlockQuote','c':[base_table]}]],'caption-nested-traversal-label-removal-and-numbering',changes)
inputs=ask(['inputs'])
for name,text in inputs:parse(''.join(map(chr,text)),['component'],'all-original-reader-inputs-through-table-component',source=name)
command_inputs=0;actual_files=set()
for file in command_files:
 text=(root/'upstream/pandoc'/file).read_text()
 for index,m in enumerate(re.finditer(r'^% pandoc ([^\n]*)\n(.*?)^\^D\n',text,re.M|re.S)):
  if re.search(r'(?:-f\s*|--from(?:=|\s+))latex\b',m[1]) and re.search(pattern,m[2]):
   parse(m[2],['component'],'original-command-inputs-through-table-component',source=file,index=index);command_inputs+=1;actual_files.add(file)
for kind,n in itertools.product(range(4),[1500,10000,100000]):add(['stress',kind,n],'deep-finite-row-repair-compaction-and-alignment')
for index,doc in enumerate(docs):
 for label in [None,'id']:
  changes=initial()+[['last-label',label],['chapters',True],['header',[3,9]],['table',[3,7]]]
  parse('',['caption',doc['blocks']],'all-original-block-trees-caption-traversal-and-label-state',changes,source=index)
for caption in [None,[None,[{'t':'Plain','c':[{'t':'Span','c':[['id',[],[['label','id']]],[]]},{'t':'Space'},{'t':'Str','c':'caption'}]}]]]:
 table=json.loads(json.dumps(base_table))
 table['c'][0]=['old',['class'],[['key','value']]]
 table['c'][4][0][3][0][1][0][4]=[{'t':'Para','c':[{'t':'Span','c':[['id',[],[['label','id']]],[]]},{'t':'Space'},{'t':'SoftBreak'},{'t':'Str','c':'body'}]}]
 parse('',['caption',[table]],'caption-removes-matching-label-and-adjacent-spaces',initial()+[['last-label','id'],['caption',caption]])
for ch in [' ','\t','\r','\n','\v','\f','\u0085','\u00a0','\u1680','\u2000','\u2003','\u2028','\u2029','\u202f','\u205f','\u3000','😀']:
 for value in [ch,ch+'x'+ch,ch+'\n','\n'+ch+'\n',ch+'12'+ch]:add(['shared-text',raw(value)],'shared-ascii-trimming-versus-unicode-whitespace')
 parse('{*{'+ch+'bad'+ch+'}{l}}',['aligns'],'repetition-error-retains-non-ascii-whitespace')
for start in range(0,0x110000,0x10000):add(['shared-text-chars',start,min(0x10000,0x110000-start)],'every-char-through-shared-whitespace-and-trimming')
proc.stdin.close();assert proc.wait()==0
if target.exists():
 with gzip.open(target,'rt') as old:
  next(old)
  for index,line in enumerate(old):assert cs[index]==json.loads(line),index
m=dict(sources=hashes(),sourceLinks=links(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),registries=registries,originalAstShapes={k:len(v) for k,v in shapes.items()},originalReaderInputs=len(inputs),originalCommandInputs=command_inputs,originalCommandInputFiles=len(actual_files),hashedCommandFiles=len(command_files),verifiedOriginalBuildFiles=len(files))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for c in [m,*cs]:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original Table/compactifyTable observations',m['caseHash'],flush=True)
