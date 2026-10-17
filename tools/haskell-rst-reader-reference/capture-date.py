#!/usr/bin/env python3
import json,pathlib,subprocess,itertools
r=pathlib.Path('/tmp/kotdoc-rst-reader');qs=[]
def add(pattern,time='1970-01-01 00:00:00 UTC'):qs.append({'name':f'date/{len(qs)}','kind':'date','input':pattern,'timestamp':time})
for spec in '%tncRTXrPpHIk lMSqQszZu waAYyCBbhmdDejGgfVUWDFx'.replace(' ',''):
 for modifier in ['','_','-','0','^','#']:
  for alt in ['', 'E']:add('%'+modifier+alt+spec)
for spec in ['Y','e','q','Q','A','z','Z']:
 for modifier in ['','-','_','0']:
  for width in ['0','1','3','14']:add('%'+modifier+width+spec)
for time in ['2000-02-29 12:34:56.123456789012 UTC','1969-12-31 23:59:59.1 UTC','2024-12-31 23:01:02.00001 UTC','0001-01-01 00:00:00 UTC','2016-01-01 01:02:03 UTC']:
 add('%c|%D|%F|%G-%g-%f-%V-%u|%U-%W|%Y-%y-%C|%s|%q|%Q',time)
for pattern in ['%','%_','%012','%E','%_12E','%%x','%#A %^B','a%?b','%😀','%Oa','%::z','%0-3d']:
 add(pattern)
path=r/'date-observations.jsonl';assert not path.exists()
p=subprocess.run([str(r/'full-oracle')],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in qs)+'\n').encode(),capture_output=True,check=True)
rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(qs)
path.write_text(''.join(json.dumps({'request':q,'expected':o},ensure_ascii=False,separators=(',',':'))+'\n'for q,o in zip(qs,rs)))
print(len(rs),'independent time1.14 UTC formatting observations')
