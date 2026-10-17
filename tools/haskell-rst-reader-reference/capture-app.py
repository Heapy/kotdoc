"""Independent original RST App observations; never derives expected from Kotlin."""
import json,pathlib,base64,subprocess,tempfile,os
root=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-rst-reader');binary='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc';cases=[]
for f in ['cli-input.json','cli-warnings.json']:
 for n,old in enumerate(json.loads((root/'core/src/test/resources/muse-reader'/f).read_text())):
  # Inputs are reused; outputs are captured afresh from the exact original RST App.
  args=[a.replace('muse','rst')for a in old['args']];files={k.replace('muse','rst'):v for k,v in old['files'].items()}
  cases.append(dict(name=f+'/'+str(n),args=args,files=files,input=old['input']))
for old in map(json.loads,(r/'original37.jsonl').read_text().splitlines()):cases.append(dict(name='original37/'+old['name'],args=['-f','rst','-t','json','-s'],files={},input=base64.b64encode(old['input'].encode()).decode()))
for fmt in ['rst','rst-auto_identifiers','rst+ascii_identifiers','rst+gfm_auto_identifiers','rst+smart','rst+old_dashes','rst+lhs','rst+raw_html','rst+tex_math_dollars']:
 cases.append(dict(name='option/'+fmt,args=['-f',fmt,'-t','json'],files={},input=base64.b64encode('HÉLLO world!\n============\n\nparagraph\n'.encode()).decode()))
for flags in [[],['--tab-stop=1'],['--tab-stop=8'],['--preserve-tabs'],['--trace'],['--default-image-extension=png'],['--toc'],['-s'],['--tab-stop=0'],['--tab-stop=-1']]:
 cases.append(dict(name='option/'+repr(flags),args=['-f','rst','-t','json',*flags],files={},input=base64.b64encode(b'\t- item\n\n.. image:: image\n\n').decode()))
for path in ['/kotdoc-rst-absent/file.csv','/tmp','/tmp/\0bad','/tmp/kotdoc-rst-reader/full-oracle/no.csv']:
 for label in ['csv-table','include','raw:: html']:
  text=('.. csv-table::\n   :file: '+path+'\n\n')if label=='csv-table'else('.. raw:: html\n   :file: '+path+'\n\n')if label.startswith('raw')else('.. include:: '+path+'\n\n')
  cases.append(dict(name='io/'+label+'/'+repr(path),args=['-f','rst','-t','json'],files={},input=base64.b64encode(text.encode()).decode()))
for args,files,payload in [(['document.rst','-t','json'],{'document.rst':base64.b64encode(b'Heading\n=======\n\nbody').decode()},b''),(['-f','rst'],{},b'Heading\n=======\n\nbody'),(['-t','json','--trace','document.rst'],{'document.rst':base64.b64encode(b'.. include:: child.rst\n\n').decode(),'child.rst':base64.b64encode(b'child\n').decode()},b'')]:cases.append(dict(name='default/'+str(len(cases)),args=args,files=files,input=base64.b64encode(payload).decode()))
p=r/'app-original.json';assert not p.exists();rows=[]
for case in cases:
 with tempfile.TemporaryDirectory()as tmp:
  for name,value in case['files'].items():(pathlib.Path(tmp)/name).write_bytes(base64.b64decode(value))
  out=subprocess.run([binary,'--emulate',*case['args']],input=base64.b64decode(case['input']),capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(root/'upstream/pandoc/data'),SOURCE_DATE_EPOCH='0'))
 rows.append(dict(**case,exitCode=out.returncode,stdout=base64.b64encode(out.stdout).decode(),stderr=base64.b64encode(out.stderr).decode()))
p.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n');print(len(rows),'original complete App observations',flush=True)
