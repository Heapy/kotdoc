import pathlib,json,subprocess,base64
root=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-rst-reader');binary='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc';rows=[]
for source,golden,args in [('rst-reader.rst','rst-reader.native',['-r','rst+smart','-w','native','-s','--columns=80']),('tables.rst','tables-rstsubset.native',['-r','rst','-w','native','--columns=80']),('lhs-test.rst+lhs','lhs-test.native',['-r','rst+lhs','-w','native'])]:
 p=subprocess.run([binary,'--emulate',*args,source],capture_output=True,cwd=root/'upstream/pandoc/test');assert p.returncode==0;assert p.stdout==(root/'upstream/pandoc/test'/golden).read_bytes(),source
 rows.append(dict(name='Old/'+source,args=[*args,source],files={},input='',cwd=str(root/'upstream/pandoc/test'),exitCode=p.returncode,stdout=base64.b64encode(p.stdout).decode(),stderr=base64.b64encode(p.stderr).decode(),golden=golden))
p=r/'old-cli-original.json';assert not p.exists();p.write_text(json.dumps(rows,indent=2)+'\n');print('3 original complete Old CLI outputs exactly match verbatim goldens')
