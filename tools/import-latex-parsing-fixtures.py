#!/usr/bin/env python3
"""Capture original LaTeX tokens/state/scoping; no Kotlin expected outputs."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-latex-parsing-reference';target=root/'core/src/test/resources/latex-parsing/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_LATEX_PARSING_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
src=(root/'upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs').read_text()
assert (folder/'OriginalParsing.hs').read_text()==src.replace('module Text.Pandoc.Readers.LaTeX.Parsing','module OriginalParsing',1).replace('import Text.Pandoc.Class.PandocMonad (PandocMonad, report)','import Text.Pandoc.Class (PandocMonad, report)',1)
src=(root/'upstream/pandoc/test/Tests/Readers/LaTeX.hs').read_text()
assert (folder/'OriginalLaTeXInputs.hs').read_text()==src.replace('module Tests.Readers.LaTeX (tests)','module OriginalLaTeXInputs (tests)',1).replace('import Test.Tasty\n','import TokenInputs\n',1).replace('import Test.Tasty.HUnit (HasCallStack)','import GHC.Stack (HasCallStack)',1).replace('import Tests.Helpers\n','',1)
fixtures=subprocess.check_output(['git','ls-files','test/*.latex','test/**/*.latex','test/*.tex','test/**/*.tex','test/*.sty','test/**/*.sty','test/*.cls','test/**/*.cls'],cwd=root/'upstream/pandoc').decode().splitlines()
sources=['tools/import-latex-parsing-fixtures.py','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX/Parsing.hs','upstream/pandoc/src/Text/Pandoc/Readers/LaTeX.hs','upstream/pandoc/src/Text/Pandoc/TeX.hs','upstream/pandoc/src/Text/Pandoc/Sources.hs','upstream/pandoc/src/Text/Pandoc/Class.hs','upstream/pandoc/src/Text/Pandoc/Parsing/Capabilities.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/test/Tests/Readers/LaTeX.hs','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal','licenses/pandoc-GPL-2.0.md','licenses/Parsec-BSD-2-Clause.txt','licenses/text-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt']+['upstream/pandoc/'+f for f in fixtures]
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'vendor/text-2.1.2'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original LaTeX parsing observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
modules=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX']
for name in modules:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+name+',\n' for name in modules))
tracked=[x for x in subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0') if x]
for name in tracked:assert (args.prepared/'pandoc'/name).read_bytes()==(changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()),name
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req[:2]
 return json.loads(line)
def add(req,group,**extra):
 value=ask(req);assert group=='deferred-demand' or not (isinstance(value,dict) and 'error' in value and 'ok' not in value),(req,value)
 cs.append(dict(request=req,expected=value,group=group,**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'LaTeX observations',flush=True)
def raw(s):return list(map(ord,s))
pos=[raw('source'),2,3]
def tok(t,s,p=pos):return [p,t,raw(s)]
def tokens(s,p=pos):return ask(['tokens',p,raw(s)])[0]
original=ask(['original-inputs'])
for label,s in original:add(['tokens',pos,s],'all-original-reader-unit-inputs',source=label)
for fixture in fixtures:add(['tokens',pos,raw((root/'upstream/pandoc'/fixture).read_text())],'all-original-latex-tex-sty-cls-files',source=fixture)
for start in range(0,0x110000,65536):add(['unicode',start,min(65536,0x110000-start)],'every-haskell-char-word-and-control-sequence-witnesses')
for n in range(5):
 for xs in itertools.product('a0#^\\\n \t',repeat=n):add(['tokens',pos,raw(''.join(xs))],'exhaustive-short-tokenizer-boundaries')
mode_inputs=['\\foo@bar:nn_baz','\\@','\\foo\n','\\foo \t x','\\makeatletter\\@foo\\makeatother\\@foo','\\ExplSyntaxOn\\foo_bar:n\\ExplSyntaxOff\\foo_bar:n']
for prefix in ['','\\makeatletter','\\ExplSyntaxOn','\\makeatletter\\ExplSyntaxOn','\\ExplSyntaxOn\\makeatletter\\ExplSyntaxOff','\\makeatletter\\ExplSyntaxOn\\makeatother']:
 for suffix in mode_inputs+[''.join(x) for x in itertools.product('@:_a9',repeat=3)]:add(['tokens',pos,raw(prefix+suffix)],'at-letter-and-expl-syntax-mode-transitions')
for n in [0,1,9,10,99,2**63-1,2**63,2**64-1,2**64,10**100,10**1000]:
 for prefix in ['#','##','###','####']:
  for z in ['','0','000']:add(['tokens',pos,raw(prefix+z+str(n)+'x')],'argument-indices-including-int64-overflow')
for a in range(128):
 for b in [None,48,57,65,70,97,102,103,127,128]:add(['tokens',pos,raw('^^'+chr(a)+('' if b is None else chr(b)))],'one-and-two-character-caret-escapes')
for line,col in itertools.product([-2,0,1,2**63-1,-2**63],repeat=2):
 for s in ['a\tb\n😀x','\\ \n  \nx','\\\n \nx','#1##23','^^aa^^aF','%comment\nnext']:
  add(['tokens',[[0xd800,0xdc00],line,col],raw(s)],'raw-source-names-and-int64-position-boundaries')
for s in mode_inputs+['abc','\\ \n \nx','##x###1','^^aaé','%x\ny']:
 for i in range(len(s)+1):
  chunks=[[pos,raw(s[:i])],[[raw('second'),10,5],raw(s[i:])]]
  add(['sources',chunks],'tokenizer-state-resets-at-every-source-chunk')
  add(['get-input',[raw('current'),-1,8],chunks],'current-position-overrides-only-first-source-chunk')
for chunks in [[],[[pos,[]]],[[pos,[]],[pos,raw('x')]],[[pos,raw('a')],[pos,[]],[pos,raw('b')]]]:add(['sources',chunks],'empty-source-chunks')
synthetic=[tok(['CtrlSeq',raw('foo')],'\\foo'),tok(['Word'],'bar'),tok(['Word'],''),tok(['Symbol'],'a'),tok(['Spaces'],' '),tok(['CtrlSeq',raw('@')],'\\@'),tok(['Arg',1],'#1')]
for n in range(4):
 for xs in itertools.product(synthetic,repeat=n):add(['untokenize',list(xs)],'synthetic-control-word-separators-and-empty-texts')
for a,b in itertools.product([False,True],repeat=2):
 for left,right in itertools.product([[],synthetic[:1],synthetic[:3]],repeat=2):add(['stream',a,left,b,right],'stream-semigroup-flags-and-tail-reset')
for xs in [[],[0],[1],[2,3],[-1,0,1],[2**63-1],[0,2**63-1],[0,-2**63]]:
 for level,p in itertools.product(range(-2,8),[0,10,11]):add(['dotted',xs,level,p],'dotted-number-render-increment-show-and-equality')
ths=[[synthetic[:1],i,se,sy,n,ns] for i,se,sy,n,ns in itertools.product(range(3),[None,'s'],[None,'x'],[False,True],[[],[1],[2,3]])]
for i,t in enumerate(ths):
 for u in [t,ths[(i+1)%len(ths)],ths[(i+17)%len(ths)]]:add(['theorem',t,u],'theorem-field-equality')
def il(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
strx=il('Str','x');meta=il('MetaString','v');bs=[il('Para',[strx])]
base=ask(['state',[]]);opts=base['options'].copy();opts.update({'columns':-1,'tab-stop':0,'standalone':True,'strip-comments':True})
changes=[['options',opts],['meta','k',meta],['quote',0],['macro','a',1],['push-macros'],['macro','b',2],['include','one'],['include','two'],['log','first'],['identifier','é'],['verbatim',True],['math',True],['caption',[[strx],bs]],['list-item',True],['table-cell',True],['header',[2,3]],['figure',[4]],['table',[5,6]],['note-num',-2**63],['footnote',3,bs],['theorem','th',ths[-1]],['theorem-style',2],['last-label','label'],['label','a',[strx]],['chapters',True],['toggle','a',True],['file','a','contents'],['enable-raw',False],['raw',synthetic[:3],3,2],['ligatures',False],['exclusion','upper',['ß','é']]]
add(['state',[]],'all-state-defaults')
for i in range(1,len(changes)+1):add(['state',changes[:i]],'all-state-fields-and-instance-updates')
for more in [[['pop-macros']],[['clear-macros']],[['pop-macros'],['pop-macros'],['pop-macros']],[['drop-include'],['drop-include'],['drop-include']],[['delete-meta','k']],[['log','second']]]:add(['state',changes+more],'scope-tail-preservation-includes-meta-and-log-order')
initial_tokens=tokens('a b c')
actions=[['state'],['consume',1],['then',['consume',1],['state']],['then',['change',['verbatim',False]],['state']],['then',['change',['enable-raw',True]],['state']],['then',['mark',synthetic[0]],['state']],['fail'],['then',['consume',1],['fail']],['pair',['consume',1],['state']],['quote',1,['state']],['from',synthetic[:2],['pair',['consume',1],['state']]],['reset-caption'],['add-meta','k',il('MetaList',[meta])]]
wrappers=[lambda x:x,lambda x:['verbatim',x],lambda x:['disable',x],lambda x:['raw',x],lambda x:['raw',['raw',x]],lambda x:['from',synthetic[:2],x],lambda x:['or',['try',['disable',['then',['consume',1],['fail']]]],x]]
for initial in [[],changes,[['verbatim',True]],[['enable-raw',False]],[['raw',synthetic[:2],2,1]]]:
 for action,wrap in itertools.product(actions,wrappers):add(['parse',initial,initial_tokens,wrap(action)],'scoping-raw-capture-local-input-restoration-and-failure')
for current,header,chapters,which in itertools.product([[],[0],[1],[2,3],[0,2**63-1],[1,2,3]],[[],[2],[3,4]],[False,True],range(3)):
 init=[['header',header],['figure',current],['table',current],['chapters',chapters]]
 if which==0:init[0]=['header',current]
 add(['parse',init,[],['number',which]],'chapter-aware-next-number-without-mutation')
for k in range(9):add(['demand',k],'deferred-demand')
for kind in range(5):
 for depth in [1500,10000,100000]:add(['stress',kind,depth],'deep-finite-token-source-stream-and-untokenize')
for depth in [1500,10000,100000]:add(['deep-scopes',depth],'deep-nested-raw-scopes')
for depth in [1500,10000,100000]:add(['macro-stack',depth],'deep-persistent-macro-stack-push-update-pop')
proc.stdin.close();assert proc.wait()==0
m=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalUnitInputs=len(original),originalFiles=len(fixtures),verifiedOriginalBuildFiles=len(tracked),exposedModules=modules)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as z:
  for value in [m,*cs]:z.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'LaTeX observations',m['caseHash'],flush=True)
