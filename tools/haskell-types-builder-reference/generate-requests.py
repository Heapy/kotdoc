#!/usr/bin/env python3
"""Generate only source inputs, never expected results."""
import itertools,json
from pathlib import Path
H=Path(__file__).resolve().parent
MIN=-(2**63);MAX=2**63-1
attr='("id",["class"],[("key","value")])'
inline=['Str "a"','Emph [Str "a"]','Underline [Str "a"]','Strong [Str "a"]','Strikeout [Str "a"]','Superscript [Str "a"]','Subscript [Str "a"]','SmallCaps [Str "a"]','Quoted SingleQuote [Str "a"]','Cite [] [Str "a"]','Code ("",[],[]) "code"','Space','SoftBreak','LineBreak','Math InlineMath "x"','RawInline (Format "html") "raw"','Link ("",[],[]) [Str "label"] ("url","title")','Image ("",[],[]) [] ("url","title")','Note [Para []]','Span ("",[],[]) [Str "a"]']
blocks=['Plain []','Para [Str "b"]','LineBlock [[Str "a"],[]]','CodeBlock ("",[],[]) "code"','RawBlock (Format "html") "raw"','BlockQuote [Plain []]','OrderedList (1,Decimal,Period) [[Plain []]]','BulletList [[Plain []]]','DefinitionList [([Str "a"],[[Plain []]])]','Header 2 ("",[],[]) []','HorizontalRule','Table ("",[],[]) (Caption Nothing []) [] (TableHead ("",[],[]) []) [] (TableFoot ("",[],[]) [])','Div ("",[],[]) []','Figure ("",[],[]) (Caption Nothing []) []']
def hs(xs):return '['+','.join(xs)+']'
base={'inlines':hs(inline),'blocks':hs(blocks),'points':[ord(x)for x in ' text\r\nvalue '],'format':[104,116,109,108],'url':[47,117,114,108],'title':[116,105,116,108,101],'attr':attr,'items':'[[Plain []],[],[Para [Str "a"]]]','lines':'[[Str "a"],[],[Space,Str "b"]]','caption':'Caption (Just [Str "short"]) [Plain [Str "long"]]','citations':'[Citation {citationId = "id", citationPrefix = [], citationSuffix = [], citationMode = NormalCitation, citationNoteNum = 1, citationHash = 2}]','listAttributes':'(2,UpperRoman,TwoParens)','definitions':'[([Str "term"],[[Plain []],[]]),([],[])]','short':'Just [Str "short"]','level':2,'index':0,'values':[]}
rows=[]
def add(op,**kw):rows.append(dict(base,operation=op,**kw))
exports=json.loads((H.parent/'haskell-types-public-api-reference/export-matrix.json').read_text())['exports']
ops=[x['source_export']for x in exports if x['id'].startswith('Builder/')and x['status']=='implementation_present_proof_partial']
assert len(ops)==49
for op in ops+['nullAttr']:
 add(op)
 add(op,inlines='[]',blocks='[]',points=[],items='[]',lines='[]',definitions='[]',short='Nothing',attr='("",[],[])')
 for value in inline:add(op,inlines=hs([value]))
 for value in blocks:add(op,blocks=hs([value]))
texts=[[],[32],[9],[10],[13],[32,13,10,9],[0,11,12,133,160,5760,8192,8195,8232,8233,8239,8287,12288],[0xd7ff,0xd800,0xdbff,0xdc00,0xdfff,0xe000,0xffff,0x10000,0x10ffff],[65,32,66,13,10,67],[65,9,66,32,67]]
for n in [1,2,3,4,5,6,7,8,15,16,31,32,64,128,256,512,1024]:texts.append(([65,32,10,9,66]*n))
for op in ['text','str','codeWith','code','math','displayMath','rawInline','codeBlockWith','codeBlock','rawBlock']:
 for points in texts:add(op,points=points,format=points,url=points,title=points)
for n in [0,1,2,3,4,5,6,7,8,9,15,16,17,31,32,33,40,50,64,80,100,128,130,256,512,1024]:
 for left,right in itertools.product([0,1,2,3,4,7],repeat=2):
  add('trimInlines',inlines=hs(['Space']*left+['Str "x"']*n+['SoftBreak']*right))
 for index in [MIN,-2,-1,0,1,2,3,n//2,n-1,n,n+1,MAX]:add('split',values=list(range(n)),index=index)
for op in ['header','headerWith']:
 for n in [MIN,-1,0,1,6,MAX]:add(op,level=n)
for op in ['linkWith','link','imageWith','image','simpleFigureWith','simpleFigure']:
 for points in texts:add(op,url=points,title=points)
for n in list(range(161))+[256,512]:
 for index in range(-1,n+2):add('split',values=list(range(n)),index=index)
add('moduleExample')
H.joinpath('requests.jsonl').write_text(''.join(json.dumps(x,separators=(',',':'))+'\n'for x in rows));print(len(rows),'original input requests')
