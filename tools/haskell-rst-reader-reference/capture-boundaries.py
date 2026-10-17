#!/usr/bin/env python3
import pathlib,json,subprocess,itertools
r=pathlib.Path('/tmp/kotdoc-rst-reader');qs=[];defaults=json.loads((r/'default-options.json').read_text())
def add(name,text='',**kw):qs.append(dict(name='boundary/'+name,input=text,**kw))
for first in ['::',':',': :',':: ','::\t','..','.. ', '.. role::','.. include::']:
 for rest in ['', '\n','\n\n','\n\nplain\n','\n\n\tcode\n','\n\n> quoted\n']:
  add(str(len(qs)),first+rest)
for stop in [1,4,8]:
 for raw in ['::\n\n\tcode\n','- a\n\t- b\n\n','term\n\tbody\n','\t:x: y\n','| a\n|\tb\n']:
  add('tabs/'+str(len(qs)),raw,options=dict(defaults,**{'tab-stop':stop}))
for field in ['NaN','Infinity','-Infinity','1e999','0','-1','50%','nan','1cm']:
 add('dimensions/'+str(len(qs)),'.. image:: a.png\n   :width: 12px\n   :height: 3cm\n   :scale: '+field+'\n\n')
for epoch in ['0','-1','( 0x10 )','18446744073709551617','-18446744073709551617','invalid','1.5','  0  ']:
 add('epoch/'+str(len(qs)),'.. |date| date:: %c|%s|%Y\n\n|date|\n',environment=[['SOURCE_DATE_EPOCH',epoch]])
path=r/'boundary-observations.jsonl';assert not path.exists()
p=subprocess.run([str(r/'full-oracle')],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in qs)+'\n').encode(),capture_output=True);(r/'boundary-native.stderr').write_bytes(p.stderr);p.check_returncode();rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(qs);path.write_text(''.join(json.dumps({'request':q,'expected':e},ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(qs,rs)));print(len(rs),'source parser/error/dimension/epoch boundary observations')
