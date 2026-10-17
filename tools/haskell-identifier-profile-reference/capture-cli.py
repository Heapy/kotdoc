#!/usr/bin/env python3
"""Capture exact original application boundaries, with no Kotlin expectations."""
import base64,gzip,json,pathlib,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'tools/haskell-identifier-profile-reference/work-in-progress/fixtures'
cli='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc'
points=json.loads((OUT/'delta-points.json').read_text());rows=[]
def capture(name,args,inp):
 p=subprocess.run([cli,'--emulate',*args],input=inp.encode(),capture_output=True,check=False)
 rows.append(dict(name=name,args=args,input=base64.b64encode(inp.encode()).decode(),exitCode=p.returncode,stdout=base64.b64encode(p.stdout).decode(),stderr=base64.b64encode(p.stderr).decode()))
for ext in ['', '+ascii_identifiers','+gfm_auto_identifiers','+ascii_identifiers+gfm_auto_identifiers']:
 for auto in ['+auto_identifiers','-auto_identifiers']:
  for fmt in ['dokuwiki','muse']:
   inp=''.join((('====== '+chr(cp)+' ======\n')if fmt=='dokuwiki'else('* '+chr(cp)+'\n'))*2 for cp in points)
   capture(fmt+'/'+ext+auto,['-f',fmt+ext+auto,'-t','json'],inp)
  inp='[ '+', '.join('Header 1 ('+json.dumps(chr(cp),ensure_ascii=False)+',[],[]) [Str '+json.dumps(chr(cp),ensure_ascii=False)+']'for cp in points for _ in range(2))+' ]'
  capture('muse-writer/'+ext+auto,['-f','native','-t','muse'+ext+auto],inp)
for ext in ['', '+ascii_identifiers','+gfm_auto_identifiers','+ascii_identifiers+gfm_auto_identifiers']:
 inp='[ '+', '.join('Header 1 ('+json.dumps(chr(cp),ensure_ascii=False)+',[],[]) [Str '+json.dumps(chr(cp),ensure_ascii=False)+']'for cp in points)+' ]'
 capture('rst-control/'+ext,['-f','native','-t','rst'+ext],inp)
# Retain full original command expectations, not merely writer AST probes.
for sub,filename,numbers in [('muse-writer','4172.md',None),('muse-reader','5107.md',None),('rst-writer','8182.md',None)]:
 path=ROOT/'core/src/test/resources'/sub/'commands.jsonl.gz'
 for r in map(json.loads,gzip.decompress(path.read_bytes()).splitlines()):
  if r['file'].endswith(filename):
   import shlex
   args=shlex.split(r['command'])[1:];p=subprocess.run([cli,'--emulate',*args],input=r['input'].encode(),capture_output=True,cwd=ROOT/'upstream/pandoc/test');combined=(''.join('2> '+l+'\n'for l in p.stderr.decode().splitlines())+p.stdout.decode()).replace('\r','')+(f'=> {p.returncode}\n'if p.returncode else'');assert combined==r['expected'];rows.append(dict(name='command/'+filename+'/'+str(r['number']),args=args,input=base64.b64encode(r['input'].encode()).decode(),exitCode=p.returncode,stdout=base64.b64encode(p.stdout).decode(),stderr=base64.b64encode(p.stderr).decode()))
p=OUT/'cli.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(('\n'.join(json.dumps(r,ensure_ascii=False)for r in rows)+'\n').encode(),mtime=0));print(len(rows),'original exact application cases captured')
