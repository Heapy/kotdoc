import pathlib,subprocess,base64,json,os,tempfile
r=pathlib.Path('/tmp/kotdoc-rst-reader');root=pathlib.Path(__file__).resolve().parents[2];binary='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc';rows=[]
for fmt in ['rst','muse','dokuwiki']:
 for value in ['0','-1','1','8','NaN','1.0','+1','0x8','0o10','0b1000','( 8 )',' 8\t','8x','9223372036854775807','9223372036854775808','18446744073709551617','-18446744073709551615','18446744073709551616','\u00a08\u00a0','\u20288']:
  q=dict(name='tabstop/'+fmt+'/'+repr(value),args=['-f',fmt,'-t','json','--tab-stop='+value],files={},input=base64.b64encode(b'plain\n').decode())
  p=subprocess.run([binary,'--emulate',*q['args']],input=b'plain\n',capture_output=True,cwd=root,env=dict(os.environ,pandoc_datadir=str(root/'upstream/pandoc/data')))
  rows.append(dict(**q,exitCode=p.returncode,stdout=base64.b64encode(p.stdout).decode(),stderr=base64.b64encode(p.stderr).decode()))
p=r/'tabstop-app-original.json';assert not p.exists();p.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n');print(len(rows),'original full App tab-stop observations')
