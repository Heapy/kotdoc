#!/usr/bin/env python3
"""Read-only source/fixture authentication and original-module replay. No regeneration."""
import argparse,gzip,hashlib,json,pathlib,subprocess,sys
sys.setrecursionlimit(100000)
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');p.add_argument('--oracle',default='/tmp/kotdoc-org-reader/final-oracle');p.add_argument('--helpers',default='/tmp/kotdoc-org-reader/helper-oracle');p.add_argument('--effects',default='/tmp/kotdoc-org-reader/effect-oracle');p.add_argument('--metadata',default='/tmp/kotdoc-org-reader/meta-helper-show-oracle2');a=p.parse_args()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def rows(name):return [json.loads(line)for line in gzip.open(HERE/'fixtures'/name,'rt')]
if a.check_source:
 for pinfile in ['source-pins.json','dependency-pins.json']:
  for path,expected in json.loads((HERE/pinfile).read_text()).items():assert sha(ROOT/path)==expected,path
 subprocess.run([sys.executable,str(HERE/'prepare.py'),'--check-source'],cwd=ROOT,check=True)
 profile=json.loads((HERE/'runtime-profile.json').read_text())
 for entry in [profile['plan'],profile['privateRegistration'],profile['copiedOriginalBinary'],profile['originalCli'],profile['textMappingSource'],*profile['packages'],*profile['oracleBinaries'],*profile['compiledLibraryArtifacts']]:
  path=pathlib.Path(entry['path']);path=path if path.is_absolute()else ROOT/path
  assert sha(path)==entry['sha256'],str(path)
 assert profile['textProfile']=='text-2.1.2-b3a1'

 # A final immutable manifest, when present, authenticates the tools and payloads
 # as well as source inputs. Historical manifests are never rewritten here.
 manifest=HERE/'manifest.json'
 if manifest.exists():
  pinned=json.loads(manifest.read_text())
  for path,expected in pinned['files'].items():assert sha(ROOT/path)==expected,path
  for name,count in pinned['counts'].items():assert len(gzip.decompress((HERE/'fixtures'/name).read_bytes()).splitlines())==count,name
 print('read-only source authentication PASS',flush=True)
if a.verify_oracle:
 original=rows('original312.jsonl.gz');r=subprocess.run([a.oracle,'--original-tests'],stdout=subprocess.PIPE,check=True);actual=[json.loads(x)for x in r.stdout.splitlines()];assert actual==original,'original312';print('original312 PASS',flush=True)
 for file,binary,wrapped in [('helpers.jsonl.gz',a.helpers,True),('expanded.jsonl.gz',a.oracle,False),('profile.jsonl.gz',a.oracle,False),('boundary.jsonl.gz',a.oracle,False),('effects.jsonl.gz',a.effects,True),('meta-collections.jsonl.gz',a.metadata,True),('meta-collections-extra.jsonl.gz',a.metadata,True),('deep.jsonl.gz',a.oracle,False),('deep-tree-show.jsonl.gz',a.oracle,False)]:
  records=rows(file);payload=''.join(json.dumps(row['request'],separators=(',',':'))+'\n'for row in records).encode()
  command=[binary]+(['+RTS','-K64m','-RTS']if not wrapped else [])
  r=subprocess.run(command,input=payload,stdout=subprocess.PIPE,check=True);outputs=[json.loads(x)for x in r.stdout.splitlines()];assert len(outputs)==len(records),file
  for i,(row,out)in enumerate(zip(records,outputs)):
   if wrapped:assert out==row,(file,i)
   else:
    assert out.pop('name')==row['request']['name'],(file,i,'name');assert out==row['expected'],(file,i)
  print(file,len(records),'PASS',flush=True)
