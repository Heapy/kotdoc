#!/usr/bin/env python3
"""Input construction only; expected results are always the original binary."""
import json,itertools
from pathlib import Path
HERE=Path(__file__).resolve().parent
Q=lambda s:json.dumps(s,ensure_ascii=True)
def meta(entries):return 'Meta {unMeta = fromList ['+','.join('('+Q(k)+','+v+')'for k,v in entries)+']}'
empty=meta([]);default={'spec':{'type':'bool'},'value':True,'key':[ord(x)for x in 'title'],'meta':empty,'document':'Pandoc ('+empty+') []','otherMeta':empty,'otherDocument':'Pandoc ('+empty+') []','blocks':'[]','inlines':'[]','authors':'[]','metas':'[]','documents':'[]','count':'1'}
rows=[]
def add(op,**kw):r=dict(default,operation=op,**kw);r['case']=f'{op}-{len(rows):05d}';rows.append(r)
values=['MetaString ""','MetaString "a"','MetaBool False','MetaBool True','MetaList []','MetaMap (fromList [])','MetaInlines []','MetaInlines [Str "a",Space,Str "b"]','MetaBlocks []','MetaBlocks [Plain [Str "plain"]]','MetaBlocks [Para [Str "para"]]','MetaBlocks [Plain [],Para []]','MetaBlocks [HorizontalRule]','MetaList [MetaString "s",MetaInlines [Str "i"],MetaBlocks [Para [Str "p"]],MetaBool False,MetaBlocks [Plain [Str "l"]]]']
metas=[empty]+[meta([(k,v)])for k in ['title','author','date','other']for v in values]
for m in metas:
 for op in ['isNullMeta','docTitle','docAuthors','docDate']:add(op,meta=m)
 for k in ['title','author','date','other','missing','']:add('lookupMeta',meta=m,key=list(map(ord,k)))
 for op in ['deleteMetaMeta','deleteMetaPandoc']:
  for k in ['title','author','date','missing']:add(op,meta=m,document=f'Pandoc ({m}) [Para [Str "body"]]',key=list(map(ord,k)))
for order in itertools.permutations(['MetaString "s"','MetaInlines [Str "i"]','MetaBlocks [Plain [Str "l"]]','MetaBlocks [Para [Str "p"]]']):add('docAuthors',meta=meta([('author','MetaList ['+','.join(order)+']')]))
recipes=[({'type':'meta'},v)for v in values]+[({'type':'inlines'},x)for x in ['[]','[Space]','[Str "a",SoftBreak,Strong [Str "b"]]']]+[({'type':'blocks'},x)for x in ['[]','[HorizontalRule]','[Plain [Str "a"],Para []]']]+[({'type':'bool'},x)for x in [False,True]]
for points in [[],[0],[10,13,9],[97,32,98],[0x7f,0x80,0x7ff,0x800],[0xd7ff,0xd800,0xdfff,0xe000],[0xffff,0x10000,0x10ffff],[0x1f642,97],[0xfffd]]:
 for kind in ['text','string']:recipes.append(({'type':kind},points))
leaves=list(recipes)
for spec,val in leaves:
 for xs in [[],[val],[val,val]]:recipes.append(({'type':'list','element':spec},xs))
 for kind in ['mapText','mapString']:
  for pairs in [[],[([97],val)],[([0xd800],val),([0xdfff],val),([0xfffd],val)],[([0xe000],val),([0x10000],val),([97],val),([97],val)]]:recipes.append(({'type':kind,'element':spec},pairs))
for spec,val in list(recipes)[len(leaves):]:
 recipes.append(({'type':'list','element':spec},[val]))
for spec,val in recipes:
 add('convert',spec=spec,value=val)
 for op in ['setMetaMeta','setMetaPandoc']:add(op,spec=spec,value=val,meta=meta([('title','MetaBool False'),('other','MetaString "kept"')]),document=f'Pandoc ({meta([("title","MetaBool False"),("other","MetaString "+Q("kept"))])}) [Para [Str "body"]]')
for left,right in itertools.product(metas[::4],repeat=2):
 add('metaAppend',meta=left,otherMeta=right)
 add('pandocAppend',document=f'Pandoc ({left}) [Para [Str "left"]]',otherDocument=f'Pandoc ({right}) [Para [Str "right"]]')
for x in ['[]','[Str "a"]','[Space,Strong [Str "b"]]']:
 for op in ['setTitle','setDate']:add(op,inlines=x)
 add('setAuthors',authors='['+x+','+x+']')
for bs in ['[]','[HorizontalRule]','[Para [],Plain [Str "a"]]']:add('doc',blocks=bs)
for op in ['nullMeta','metaEmpty','pandocEmpty','metaConcat','pandocConcat']:add(op)
for m in metas[::4]:
 for n in ['-2','-1','0','1','2','3','4','5','8','15','16','18446744073709551617',str(2**200)]:
  add('metaStimes',meta=m,count=n)
  if int(n)<=16:add('pandocStimes',document=f'Pandoc ({m}) [Para [Str "body"]]',count=n)
 for others in [[],[empty],[metas[1],metas[2]],[m,m,m]]:
  ms='['+','.join(others)+']';ds='['+','.join('Pandoc ('+v+') [Plain []]'for v in others)+']'
  add('metaConcat',metas=ms);add('pandocConcat',documents=ds)
  add('metaSconcat',meta=m,metas=ms);add('pandocSconcat',document=f'Pandoc ({m}) [Para []]',documents=ds)
for kind in ['mapText','mapString']:
 for pairs in itertools.permutations([([0xfffd],False),([0xd800],True),([0xdfff],False)]):
  add('convert',spec={'type':kind,'element':{'type':'bool'}},value=pairs)
for n in [256,4096]:
 add('convert',spec={'type':'list','element':{'type':'bool'}},value=[True,False]*(n//2))
 nested='MetaList ['*n+'MetaBool True'+']'*n
 add('convert',spec={'type':'meta'},value=nested)
 add('docAuthors',meta=meta([('author','MetaList ['+','.join(['MetaString "s"']*n)+']')]))
 add('docAuthors',meta=meta([('author','MetaList ['+','.join(['MetaBool True']*n+['MetaInlines [Str "last"]'])+']')]))
for left,right in itertools.product(metas[::8],repeat=2):
 add('metaMappend',meta=left,otherMeta=right)
 add('pandocMappend',document=f'Pandoc ({left}) [Para []]',otherDocument=f'Pandoc ({right}) [Plain []]')
def hq(points):return '"'+''.join(chr(n)if 32<=n<127 and n not in [34,92]else chr(92)+str(n)+chr(92)+'&'for n in points)+'"'
for key in [[],[0],[97,0,98],[10,13],[0xd800],[0xdfff],[0xfffd],[0xe000],[0x10000],[0x1f642],[0x10ffff]]:
 m='Meta {unMeta = fromList [('+hq(key)+',MetaString "old")]}'
 for op in ['setMetaMeta','setMetaPandoc','deleteMetaMeta','deleteMetaPandoc','lookupMeta']:
  add(op,key=key,meta=m,document=f'Pandoc ({m}) [Para [Str "body"]]')
add('metaConcat',metas='['+','.join([empty]*4096)+']')
add('pandocConcat',documents='['+','.join(['Pandoc ('+empty+') []']*4096)+']')
add('metaSconcat',metas='['+','.join([empty]*4096)+']')
add('pandocSconcat',documents='['+','.join(['Pandoc ('+empty+') []']*4096)+']')
HERE.joinpath('requests.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=True,separators=(',',':'))+'\n'for r in rows));print(len(rows),'source inputs')
