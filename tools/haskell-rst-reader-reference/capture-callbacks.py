"""Original generic PandocMonad callback order/demand, independent of Kotlin."""
import pathlib,json,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-rst-reader');rows=[]
files=[['a.rst',list(b'child\n')],['date.rst',list(b'.. date:: %s\n\n')],['a.csv',list(b'a,b\nc,d\n')],['bad.rst',[255]],['loop.rst',list(b'.. include:: loop.rst\n\n')]]
cases=['plain','.. date:: %s\n\n','.. |d| date:: %s\n\n|d| |d|\n','.. |d| date:: %s\n\nunused\n','.. [1] .. date:: %s\n\nunused\n','.. [1] .. date:: %s\n\nref [1]_\n','.. [C] .. date:: %s\n\nunused\n','.. raw:: html\n   :file: a.rst\n\n','.. raw:: html\n   :file: missing.rst\n\n','.. raw:: html\n   :file: bad.rst\n\n','.. csv-table::\n   :file: a.csv\n\n','.. csv-table::\n   :file: missing.csv\n\n','.. csv-table::\n   :url: https://example.invalid/a.csv\n\n','.. csv-table::\n   :file: data:text/csv,a%2Cb%0Ac%2Cd%0A\n\n']
for f in ['a.rst','date.rst','bad.rst','missing.rst','loop.rst']:
 for flag in ['', '   :literal:\n','   :code: text\n']:cases.append('.. include:: '+f+'\n'+flag+'\n')
cases += ['.. unknown::\n\n   .. date:: %s\n\n','.. |d| replace:: child\n\n.. include:: a.rst\n\n|d|\n','.. date:: %s\n\n.. include:: date.rst\n\n.. date:: %s\n\n']
for text in cases:
 rows.append(dict(name='callbacks/'+str(len(rows)),input=text,files=files,fetchProfile=True,callbackTrace=True))
for value in ['0','invalid','18446744073709551617']:rows.append(dict(name='callbacks/'+str(len(rows)),input='.. date:: %s\n\n',files=[],fetchProfile=True,callbackTrace=True,environment=[['SOURCE_DATE_EPOCH',value]]))
p=r/'callback-observations.jsonl';assert not p.exists();out=subprocess.run([str(r/'callback-oracle')],input=''.join(json.dumps(q)+'\n'for q in rows).encode(),capture_output=True,check=True);expected=[json.loads(l)for l in out.stdout.splitlines()];assert len(expected)==len(rows);p.write_text(''.join(json.dumps(dict(request=q,expected=e),ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(rows,expected)));print(len(rows),'original callback demand/order observations')
