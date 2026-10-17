#!/usr/bin/env python3
import json,pathlib,subprocess,itertools
r=pathlib.Path('/tmp/kotdoc-rst-reader');attr=['',[],[]];strx={'t':'Str','c':'x'};p={'t':'Para','c':[strx]};plain={'t':'Plain','c':[strx]};a={'t':'AlignDefault'};cell=[attr,a,1,1,[p]];row=[attr,[cell]];table={'t':'Table','c':[attr,[None,[p]],[[a,{'t':'ColWidthDefault'}]],[attr,[row]],[[attr,0,[row],[row]]],[attr,[row]]]}
blocks=[p,plain,{'t':'Header','c':[1,attr,[strx]]},{'t':'LineBlock','c':[[strx],[],[strx]]},{'t':'CodeBlock','c':[['id',['code'],[['k','v']]],'a\nb']},{'t':'RawBlock','c':['html','<x>']},{'t':'BlockQuote','c':[p]},{'t':'Div','c':[attr,[p]]},{'t':'Figure','c':[attr,[None,[{'t':'Plain','c':[{'t':'Str','c':'IGNORED'}]}]],[p]]},{'t':'BulletList','c':[[p],[p]]},{'t':'OrderedList','c':[[2,{'t':'Decimal'},{'t':'Period'}],[[p],[p]]]},{'t':'DefinitionList','c':[[[strx],[[p],[p]]]]},table,{'t':'HorizontalRule'}]
qs=[]
for n,b in enumerate(blocks):
 for context in [[],[plain],[{'t':'HorizontalRule'}]]:
  doc={'pandoc-api-version':[1,23,1,2],'meta':{},'blocks':context+[b]+context};qs.append({'name':f'flatten/{len(qs)}','kind':'flatten','input':json.dumps(doc,ensure_ascii=False)})
for depth in [0,1,256,4096]:qs.append({'name':f'flatten/deep/{depth}','kind':'flatten-deep','input':str(depth)})
p=subprocess.run([str(r/'full-oracle'),'+RTS','-K128m','-RTS'],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in qs)+'\n').encode(),capture_output=True,check=True);rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(qs);out=r/'flatten-observations.jsonl';assert not out.exists();out.write_text(''.join(json.dumps({'request':q,'expected':e},ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(qs,rs)));print(len(rs),'full Shared block flattening observations, including4096Div')
