#!/usr/bin/env python3
"""Construct source inputs only. Expected output is emitted by original Haskell."""
import json,itertools,random
from pathlib import Path
H=Path(__file__).resolve().parent
MIN=-(2**63);MAX=2**63-1
attr='("id",["class"],[("key","value")])'
def cell(h=1,w=1,label='a'):return f'Cell ("{label}",[],[]) AlignLeft (RowSpan ({h})) (ColSpan ({w})) [Plain [Str "{label}"]]'
def row(cs):return 'Row ("row",[],[]) ['+','.join(cs)+']'
def rs(rows):return '['+','.join(rows)+']'
base={'width':2,'rows':'[]','cells':'[]','hang':[],'pad':False,'limit':16,'head':'TableHead ("head",[],[]) []','body':'TableBody ("body",[],[]) (RowHeadColumns 0) [] []','foot':'TableFoot ("foot",[],[]) []','attr':attr,'alignment':'AlignRight','rowSpan':1,'colSpan':1,'blocks':'[Para [Str "body"]]','caption':'Caption Nothing [Plain [Str "caption"]]','specs':'[(AlignLeft,ColWidthDefault),(AlignRight,ColWidth 0.4)]','bodies':'[]','headers':'[]','simpleRows':'[]'}
rows=[]
def add(op,**kw):r=dict(base,operation=op,**kw);r['case']=f'{op}-{len(rows):06d}';rows.append(r)
cells=[cell(1,1),cell(2,2,'b'),cell(3,3,'c')]
streams=[[],cells[:1],cells[:2],cells,[cell(0,0)],[cell(MIN,MAX)]]
for n in range(5):
 for hang in itertools.product([-1,0,1,2,3],repeat=n):
  for stream in streams:
   for pad in [False,True]:add('place',hang=list(hang),cells='['+','.join(stream)+']',pad=pad)
for h,w in itertools.product([MIN,-2,-1,0,1,2,MAX],repeat=2):
 for hang in [[1],[1,1],[2,1,1,2],[1,1,1,1],[MAX,1]]:add('place',hang=hang,cells='['+cell(h,w)+','+cell(1,1,'last')+']',pad=True)
 for op in ['cellWith','cell']:add(op,rowSpan=h,colSpan=w)
variants=[[],[row([])],[row(cells)],[row([cell(0,0)]),row([cell(2,3)])],[row([cell(MAX,MAX)]),row([cell(MIN,MIN)])],[row(cells),row(cells[:1]),row(cells[::-1])]]
rng=random.Random(611)
for i in range(100):variants.append([row([cell(rng.choice([MIN,-1,0,1,2,3,MAX]),rng.choice([MIN,-1,0,1,2,3,MAX]),f'c{k}')for k in range(rng.randrange(5))])for j in range(rng.randrange(5))])
for section in variants:
 rowsText=rs(section);add('clip',rows=rowsText)
 for width in [-3,-1,0,1,2,3,4,7]:
  head=f'TableHead ("head",[],[]) {rowsText}';foot=f'TableFoot ("foot",[],[]) {rowsText}'
  add('head',width=width,head=head);add('foot',width=width,foot=foot)
  for rhc in [MIN,-1,0,1,2,4,MAX]:add('body',width=width,body=f'TableBody ("body",[],[]) (RowHeadColumns ({rhc})) {rowsText} {rowsText}')
for section in variants[:20]:
 st=rs(section);head=f'TableHead ("head",[],[]) {st}';body=f'TableBody ("body",[],[]) (RowHeadColumns 1) {st} {st}';foot=f'TableFoot ("foot",[],[]) {st}'
 for op in ['tableWith','table']:add(op,head=head,bodies='['+body+']',foot=foot)
for op in ['simpleCell','emptyCell']:add(op)
for nh,nr,nc in itertools.product(range(4),range(4),range(4)):
 block='[Plain [Str "cell"]]';headers='['+','.join([block]*nh)+']';simpleRows='['+','.join('['+','.join([block]*nc)+']'for i in range(nr))+']';add('simpleTable',headers=headers,simpleRows=simpleRows)
H.joinpath('requests.jsonl').write_text(''.join(json.dumps(r,separators=(',',':'))+'\n'for r in rows));print(len(rows),'source table inputs')
