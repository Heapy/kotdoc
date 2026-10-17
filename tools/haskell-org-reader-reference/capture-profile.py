#!/usr/bin/env python3
"""Dev-only exact pinned reader observations; never Kotlin-derived expectations."""
import gzip,json,pathlib,subprocess
HERE=pathlib.Path(__file__).resolve().parent
OUT=HERE/'fixtures/profile.jsonl.gz'
if OUT.exists():raise SystemExit('refusing to replace independent expectations')
PROFILE=HERE.parent/'haskell-identifier-profile-reference/work-in-progress/fixtures'
base=['auto_identifiers','citations','special_strings','task_lists']
requests=[]
for cp,_ in json.loads((PROFILE/'lowercase-mappings.json').read_text()):
 requests.append(dict(name=f'profile/lower/{cp}',input=f'* X{chr(cp)}\n* X{chr(cp)}\n',extensions=base))
for cp in json.loads((PROFILE/'delta-points.json').read_text()):
 for flags in [[],base,base+['ascii_identifiers'],base+['gfm_auto_identifiers']]:
  requests.append(dict(name=f'profile/delta/{cp}/{flags}',input=f'* X{chr(cp)} /title/\n:PROPERTIES:\n:UNNUMBERED: NIL\n:END:\n* X{chr(cp)} /title/\n',extensions=flags))
raw=''.join(json.dumps(q,separators=(',',':'))+'\n'for q in requests).encode()
r=subprocess.run(['/tmp/kotdoc-org-reader/oracle'],input=raw,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
answers=[json.loads(x)for x in r.stdout.splitlines()];assert len(answers)==len(requests)
with gzip.GzipFile(filename=str(OUT),mode='wb',mtime=0)as f:
 for q,a in zip(requests,answers):
  assert q['name']==a.pop('name');f.write((json.dumps(dict(request=q,expected=a),separators=(',',':'))+'\n').encode())
print(f'captured {len(requests)} independent reader profile observations')
