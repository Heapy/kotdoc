#!/usr/bin/env python3
"""Read-only source command bindings; original complete commands and reader ASTs remain distinct."""
import json,pathlib,subprocess,shlex,base64,concurrent.futures
root=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-rst-reader');rows=json.loads((r/'rst-input-commands-inventory.json').read_text());binary='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc'
def check(row):
 args=shlex.split(row['command'])[1:];p=subprocess.run([binary,'--emulate',*args],input=row['stdin'].encode(),capture_output=True,cwd=root/'upstream/pandoc/test');combined=(''.join('2> '+line+'\n'for line in p.stderr.decode().splitlines())+p.stdout.decode()).replace('\r','')+(f'=> {p.returncode}\n'if p.returncode else'')
 return dict(binding=row,exitCode=p.returncode,stdout=base64.b64encode(p.stdout).decode(),stderr=base64.b64encode(p.stderr).decode(),sourceGoldenMatches=combined==row['expected_combined'])
with concurrent.futures.ThreadPoolExecutor(max_workers=4)as pool:results=list(pool.map(check,rows))
path=r/'commands85-original.jsonl';assert not path.exists();path.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n'for x in results))
print(len(results),'original complete RST input commands observed;',sum(x['sourceGoldenMatches']for x in results),'source goldens matched',flush=True)
for x in results:
 if not x['sourceGoldenMatches']:print('ORIGINAL_GOLDEN_DIFFERENCE',x['binding']['id'],flush=True)
