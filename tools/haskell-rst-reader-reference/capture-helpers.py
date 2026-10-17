#!/usr/bin/env python3
"""Input recipes and independent original CSV/gridtables output capture."""
import json,pathlib,subprocess,itertools
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=pathlib.Path('/tmp/kotdoc-rst-reader');qs=[]
log=(OUT/'original-grid-tests.log').read_text();assert 'All 27 tests passed' in log
inputs=[json.loads(line.split('__INPUT__',1)[1])for line in log.splitlines()if '__INPUT__'in line];assert len(inputs)==26
for i,t in enumerate(inputs):qs.append(dict(name=f'original-grid/{i}',kind='grid'if t.startswith('+')else'lines',input=t))
qs.append(dict(name='original-grid/rows',kind='grid-rows',input=''))
base=['','\n','\n\n','a','a\n','a\r\n','a\r','a,b\nc,d\n',',','""','"",','a,',' a, b\t, c ','a\t b\t c\n','"a,b",c','"a\nb",c\n','"a""b", c','"a\\"b",c','"unterminated','a,"b"tail','\r\n','  \n','a\nb\n\n','a;"b;c"\n','Ᲊ,魚,😀\n']
for opts in [{},{'delimiter':';'},{'delimiter':'\t'},{'delimiter':' '},{'delimiter':',' ,'quote':None},{'quote':"'"},{'keepSpace':True},{'escape':'\\'},{'escape':'"'},{'quote':None,'escape':'\\'}]:
 for i,t in enumerate(base):qs.append(dict(name=f'csv/{len(qs)}/{i}',kind='csv',input=t,**opts))
for text in ['x','a\u0301','\u0301a','魚','😀','Ᲊ','']:
 # Source grid display-width cases, including zero-width content.
 fromwidth={'魚':2,'😀':2,'a\u0301':1,'\u0301a':1,'Ᲊ':1,'x':1,'':0}[text]
 t='+'+'-'*(fromwidth+2)+'+---+\n| '+text+' | b |\n'+'+'+'-'*(fromwidth+2)+'+---+\n'
 for kind in ['grid','grid-trace']:qs.append(dict(name=f'grid-unicode/{kind}/{text}',kind=kind,input=t))
for top in ['+---+---+','+:--+--:+','+:--:+:--:+']:
 for middle in ['+---+---+','+===+===+']:
  t=top+'\n| a | b |\n'+middle+'\n| c | d |\n+---+---+\n'
  qs.append(dict(name=f'grid-separators/{len(qs)}',kind='grid',input=t))
for t in ['','+\n','+---+\n','+---+\nnope\n','+--\n|a|\n','+---+\r\n| a |\r\n+---+\r\n']:
 qs.append(dict(name=f'grid-errors/{len(qs)}',kind='grid',input=t))
p=subprocess.run([str(OUT/'helper-oracle')],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in qs)+'\n').encode(),capture_output=True,check=True);values=[json.loads(l)for l in p.stdout.splitlines()];assert len(qs)==len(values)
path=OUT/'helper-observations.jsonl';assert not path.exists();path.write_text('\n'.join(json.dumps(dict(request=q,expected=e),ensure_ascii=False)for q,e in zip(qs,values))+'\n');print(len(qs),'independent helper observations,including all27 original gridtables leaves')
