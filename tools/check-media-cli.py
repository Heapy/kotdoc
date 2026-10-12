#!/usr/bin/env python3
"""Installed CLI media extraction parity; --import uses independent Pandoc 3.12.1."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
source=root/'upstream/pandoc'
target=root/'core/src/test/resources/media/cli.json'
parser=argparse.ArgumentParser();parser.add_argument('--import',dest='regenerate',action='store_true');args=parser.parse_args()
paths=sorted(subprocess.check_output(['git','-C',str(source),'ls-files','test/*.fb2','test/**/*.fb2'],text=True).splitlines())
if args.regenerate:
    assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
    cases=[]
    for path in paths:
        for dest in ['media','media.zip']:
            for to in ['json','fb2']:
                cases.append({'name':f'{path} -> {to} extract={dest}','path':path,'sha256':hashlib.sha256((source/path).read_bytes()).hexdigest(),
                              'args':['-f','fb2','-t',to,'--extract-media='+dest]})
    image={'t':'Image','c':[['',[],[]],[{'t':'Str','c':'alt'}],['a.png','']]}
    document={'pandoc-api-version':[1,23,1,2],'meta':{},'blocks':[{'t':'Para','c':[image]}]}
    for flags in [[],['--resource-path','first'],['--resource-path','first:second'],['--resource-path','first','--resource-path','second'],
                  ['--resource-path=second:first'],['--resource-path=:first'],['--resource-path=first:'],['--resource-path='],
                  ['--extract-media=unused','--extract-media=out'],['--extract-media=out/./nested/']]:
        cases.append({'name':'resource flags '+str(flags),'document':document,
                      'local':{'a.png':'00','first/a.png':'01','second/a.png':'02'},
                      'args':['-f','json','-t','json','--extract-media=out',*flags]})
else:
    cases=json.loads(target.read_text())
    assert {c['path'] for c in cases if 'path' in c}==set(paths)
    for c in cases:
        if 'path' in c:assert hashlib.sha256((source/c['path']).read_bytes()).hexdigest()==c['sha256']
executable='pandoc' if args.regenerate else str(root/'cli/build/install/cli/bin/cli')
for case in cases:
    with tempfile.TemporaryDirectory() as directory:
        work=Path(directory)
        for p,h in case.get('local',{}).items():
            dest=work/p;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(bytes.fromhex(h))
        cmd=[executable,*case['args']]
        if 'path' in case:
            cmd.append(str(source/case['path']));stdin=None
        else:stdin=json.dumps(case['document']).encode()
        result=subprocess.run(cmd,input=stdin,capture_output=True,cwd=work,timeout=30)
        assert result.returncode==0,(case['name'],result.stderr)
        actual={'stdout':result.stdout.decode(),'stderr':result.stderr.decode(),
                'files':{str(p.relative_to(work)):p.read_bytes().hex() for p in sorted(work.rglob('*')) if p.is_file() and str(p.relative_to(work)) not in case.get('local',{})}}
        if args.regenerate:case['expected']=actual
        else:
            for key,value in actual.items():assert value==case['expected'][key],f'{case["name"]}: {key} differs'
if args.regenerate:target.write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n')
print(f'{"Imported" if args.regenerate else "Verified"} {len(cases)} CLI media conversions including all {len(paths)} FB2 source files')
