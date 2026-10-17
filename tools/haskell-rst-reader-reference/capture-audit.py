#!/usr/bin/env python3
import json,pathlib,subprocess,itertools
r=pathlib.Path('/tmp/kotdoc-rst-reader');defaults=json.loads((r/'default-options.json').read_text());qs=[]
def add(name,text='',**kw):qs.append(dict(name=name,input=text,**kw))
deltas=json.loads(pathlib.Path('tools/haskell-identifier-profile-reference/work-in-progress/fixtures/delta-points.json').read_text())
for cp in deltas:
 for flags in [[],['auto_identifiers'],['auto_identifiers','ascii_identifiers'],['auto_identifiers','gfm_auto_identifiers']]:
  word=chr(cp)+' Name';text=f'{word}\n===========\n\n{word}\n===========\n\n_`{word}` {word}_\n'
  add(f'profile/{cp}/{flags}',text,extensions=flags)
for author in ['', ';',';;','; A','A;','A;; B',' A ; B ; C ', '*A*; B','``A;B``','A\n  B']:
 for key in ['author','authors','Author']:
  add(f'metadata/{len(qs)}',f':{key}: {author}\n:author: X\n:authors: Y\n:Version: *one*\n\n',options=dict(defaults,standalone=True))
for prefix in ['', 'Title\n=====\n\n','Title\n=====\n\nSubtitle\n--------\n\n']:
 for suffix in ['', 'same\n====\n\n','other\n----\n\n','    nested\n    ------\n\n']:
  add(f'title/{len(qs)}',prefix+':date: now\n\npara\n\n'+suffix,options=dict(defaults,standalone=True))
for top in ['=== ===','=== =====','=== === ===']:
 for mid in ['', '--- ---\n','-------\n','---- --\n','-- ----\n']:
  add(f'simple/{len(qs)}',top+'\na   b\n'+mid+'x   y\n'+top+'\n\nend\n')
for head in ['+---+---+','+===+===+']:
 for cols in [0,-1,4,80,2147483648,9223372036854775807]:
  add(f'grid/{len(qs)}','+---+---+\n| H | J |\n'+head+'\n| a | b |\n+---+---+\n\n',options=dict(defaults,columns=cols))
for raw in ['x,y\n','\ufeffx,y\r\n','"bad\n']:
 for key in ['file','url']:
  add(f'csv-file/{len(qs)}',f'.. csv-table:: title\n   :{key}: file.csv\n\n',files=[['file.csv',list(raw.encode())]])
for value in ['-1','0','1','2','9223372036854775807','9223372036854775808','18446744073709551617']:
 add(f'headerrows/{value}','.. list-table::\n   :header-rows: '+value+'\n\n   * - a\n     - b\n   * - c\n     - d\n')
for start,end in itertools.product(['-7','-1','0','1','2','7'],repeat=2):
 add(f'include-range/{start}/{end}',f'.. include:: inc.rst\n   :start-line: {start}\n   :end-line: {end}\n   :literal:\n\n',files=[['inc.rst',list(b'one\ntwo\nthree\n')]])
for name in ['with space.rst','bad\nfile.rst','é😀.rst']:
 add('include-name/'+name,'.. include:: '+name+'\n\n')
for fields in ['', '   :start-after: absent\n','   :end-before: two\n','   :start-after: one\n   :end-before: three\n']:
 add(f'include-select/{len(qs)}','.. include:: inc.rst\n'+fields+'\n',files=[['inc.rst',list(b'one\n\ntwo\n\nthree\n')]])
add('include-invalid-utf8','.. include:: invalid.rst\n\n',files=[['invalid.rst',[255]]])
add('raw-invalid-utf8','.. raw:: html\n   :file: invalid.rst\n\n',files=[['invalid.rst',[255]]])
for src in ['.. |sub| container::\n\n   first\n\n   second\n\n','.. |sub| compound::\n\n   * one\n   * two\n\n','.. |sub| line-block::\n\n   a\n   b\n\n']:
 add('sub-blocks/'+str(len(qs)),src+'text |sub| here\n')
for format in ['%18446744073709551617q','%18446744073709551616Q','%9223372036854775808q','%18446744073709551617Eq','%-18446744073709551617z']:
 add('date-width/'+str(len(qs)),format,kind='date')
path=r/'audit-observations.jsonl';assert not path.exists()
p=subprocess.run([str(r/'full-oracle')],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in qs)+'\n').encode(),capture_output=True,check=True)
rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(qs)
path.write_text(''.join(json.dumps({'request':q,'expected':o},ensure_ascii=False,separators=(',',':'))+'\n'for q,o in zip(qs,rs)));print(len(rs),'additional source/profile/state/table/runtime audit observations')
