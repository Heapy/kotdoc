"""Capture independent original-module responses. Refuses expectation replacement."""
import pathlib,json,subprocess
from requests import requests
r=pathlib.Path('/tmp/kotdoc-org-writer');p=r/'writer-observations.jsonl';assert not p.exists()
original=[json.loads(l)for l in(r/'original8.jsonl').read_text().splitlines()];qs=requests(original)
proc=subprocess.run([str(r/'oracle')],input=''.join(json.dumps(q,ensure_ascii=False)+'\n'for q in qs).encode(),capture_output=True,check=True)
rows=[json.loads(l)for l in proc.stdout.splitlines()];assert len(rows)==len(qs)
p.write_text(''.join(json.dumps(dict(request=q,expected=e),ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(qs,rows)));print(len(qs),'original module observations',flush=True)
