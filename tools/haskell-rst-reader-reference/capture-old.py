#!/usr/bin/env python3
import json,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-rst-reader');defaults=json.loads((r/'default-options.json').read_text());binary='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc';records=[]
for name,source,golden,exts,standalone in [('basic','rst-reader.rst','rst-reader.native',['smart','auto_identifiers'],True),('tables','tables.rst','tables-rstsubset.native',['auto_identifiers'],False),('lhs','lhs-test.rst+lhs','lhs-test.native',['literate_haskell','auto_identifiers'],False)]:
 q={'name':'old/'+name,'input':(root/'upstream/pandoc/test'/source).read_text(),'options':dict(defaults,standalone=standalone,columns=80),'extensions':exts}
 p=subprocess.run([binary,'--emulate','-f','native','-t','json'],input=(root/'upstream/pandoc/test'/golden).read_bytes(),capture_output=True,check=True)
 records.append({'request':q,'expected':{'name':q['name'],'document':json.loads(p.stdout),'logs':[]},'source':source,'golden':golden})
path=r/'old3.jsonl';assert not path.exists();path.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n'for x in records));print(len(records),'original Old fixture ASTs retained')
