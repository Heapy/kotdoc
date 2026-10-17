#!/usr/bin/env python3
"""Bind Old golden ASTs to original App.Input tab conversion and original reader logs."""
import json,pathlib,subprocess
r=pathlib.Path('/tmp/kotdoc-rst-reader');old=[json.loads(l)for l in (r/'old3.jsonl').read_text().splitlines()];records=[]
def native(q):
 p=subprocess.run([str(r/'full-oracle')],input=(json.dumps(q,ensure_ascii=False)+'\n').encode(),capture_output=True,check=True);return json.loads(p.stdout)
for row in old:
 q=dict(row['request']);normalized=native(dict(q,kind='app-tabs'))['result'];q['input']=normalized
 result=native(q);assert result['document']==row['expected']['document'],row['source']
 records.append({'request':q,'expected':result,'source':row['source'],'golden':row['golden'],'appTransformation':'Text.Pandoc.App.Input.convertTabs -> Shared.tabFilter 4'})
path=r/'old3-app.jsonl';assert not path.exists();path.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n'for x in records));print('All 3 original normalized-reader ASTs equal original Old goldens')
