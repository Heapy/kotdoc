#!/usr/bin/env python3
"""Observe all original library cases through original application input normalization."""
import argparse,base64,gzip,json,os,pathlib,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--original-cli',required=True);p.add_argument('--fixture-dir',type=pathlib.Path,required=True);a=p.parse_args();out=a.fixture_dir/'cli-library.jsonl.gz';assert not out.exists()
rows=[]
for original in map(json.loads,gzip.decompress((a.fixture_dir/'original250.jsonl.gz').read_bytes()).splitlines()):
 args=['-f','muse-amuse-auto_identifiers'+''.join('+'+e for e in original['extensions']),'-t','json']
 result=subprocess.run([a.original_cli,'--emulate',*args],input=original['input'].encode(),capture_output=True,cwd=ROOT,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
 assert result.returncode==0,(original['name'],result.stderr)
 rows.append(dict(name=original['name'],args=args,input=base64.b64encode(original['input'].encode()).decode(),exitCode=result.returncode,stdout=base64.b64encode(result.stdout).decode(),stderr=base64.b64encode(result.stderr).decode(),sameAsLibrary=json.loads(result.stdout)==original['expected']))
with gzip.GzipFile(filename=str(out),mode='wb',mtime=0) as f:f.write(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows).encode())
print('250 original application cases captured;',sum(r['sameAsLibrary'] for r in rows),'match literal library AST')
