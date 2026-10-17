#!/usr/bin/env python3
import json,pathlib,subprocess,itertools
r=pathlib.Path('/tmp/kotdoc-rst-reader');qs=[]
pattern='%c|%Y-%y-%C|%G-%g-%f-%V-%u|%U-%W-%j|%H:%M:%S|%s|%q|%Q|%z|%Ez'
for day,pico in itertools.product(['-1000000000000000000000','-678942','-678941','0','40587','51603','57753','1000000000000000000000'],['-86400000000000001','-1','0','1','86399999999999999','86400000000000000','86400000000000001','86401000000000000']):qs.append(dict(name='calendar/'+str(len(qs)),kind='date',input=pattern,utc=[day,pico]))
path=r/'calendar-observations.jsonl';assert not path.exists()
p=subprocess.run([str(r/'full-oracle')],input=('\n'.join(json.dumps(q)for q in qs)+'\n').encode(),capture_output=True,check=True);rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(qs)
path.write_text(''.join(json.dumps({'request':q,'expected':e},ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(qs,rs)));print(len(rs),'raw UTC BigInteger day/picosecond calendar observations')
