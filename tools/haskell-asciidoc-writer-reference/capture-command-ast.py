#!/usr/bin/env python3
"""Original command inputs -> original reader AST -> copied original writer.
This provides writer evidence only; unavailable Kotlin readers remain explicit.
"""
import pathlib,json,subprocess,gzip,shlex
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer')
EXE='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc'
rows=[]
for r in json.loads((HERE/'fixtures/commands-inventory.json').read_text()):
 args=shlex.split(r['command'])[1:];jsonargs=[];at=0
 while at<len(args):
  x=args[at];at+=1
  if x in ['-t','--to','-w','--write']:jsonargs+=['-t','json'];at+=1
  elif x.startswith(('--to=','--write=')):jsonargs+=['-t','json']
  else:jsonargs.append(x)
 d=json.loads(subprocess.check_output([EXE,'--emulate',*jsonargs],input=r['input'].encode(),cwd=ROOT/'upstream/pandoc/test'))
 q=dict(name=r['file']+'#'+str(r['number']),document=d,options=dict(wrap='WrapPreserve'if'--wrap=preserve'in args else'WrapAuto',columns=72,extensions=['auto_identifiers']))
 a=json.loads(subprocess.check_output([str(TMP/'oracle')],input=(json.dumps(q)+'\n').encode()))
 # App ensures a final newline, while the library writer can return empty.
 if '--verbose'not in args:assert a['output']+(''if a['output'].endswith('\n')else'\n')==r['expected'],(q['name'],'canonical command output differs')
 rows.append(dict(request=q,expected=a,command=r))
p=HERE/'fixtures/command-ast.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(r)+'\n').encode()for r in rows),mtime=0));print(len(rows),'original command AST writer expectations captured; full reader/App routes separate')
