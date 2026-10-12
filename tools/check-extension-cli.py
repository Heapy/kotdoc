#!/usr/bin/env python3
"""CLI extension-modifier parity, including original exit codes and diagnostics."""
import argparse
import json
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/options/cli.json'
parser=argparse.ArgumentParser();parser.add_argument('--import',dest='regenerate',action='store_true');args=parser.parse_args()
if args.regenerate:
    assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
    cases=[]
    input_docs={'native':'[Para [Str "hello"]]', 'json':'{"pandoc-api-version":[1,23,1,2],"meta":{},"blocks":[{"t":"Para","c":[{"t":"Str","c":"hello"}]}]}',
                'fb2':(root/'upstream/pandoc/test/fb2/reader/notes.fb2').read_text()}
    suffixes=['','+east_asian_line_breaks','-east_asian_line_breaks','+east_asian_line_breaks-east_asian_line_breaks','-east_asian_line_breaks+east_asian_line_breaks','+smart','+','++','+z+table_attributes+header_attributes','+lhs']
    for format,doc in input_docs.items():
        for suffix in suffixes:
            cases.append({'args':['-f',format+suffix,'-t','native'],'input':doc})
            cases.append({'args':['-f',format,'-t','native'+suffix],'input':doc})
        cases.append({'args':['--from='+format.upper(),'--to=NATIVE'],'input':doc})
    for bad in ['', '+smart','-smart']:
        cases.append({'args':['-f',bad,'-t','native'],'input':'[]'})
        cases.append({'args':['-f','native','-t',bad],'input':'[]'})
else:cases=json.loads(target.read_text())
exe='pandoc' if args.regenerate else str(root/'cli/build/install/cli/bin/cli')
for c in cases:
    p=subprocess.run([exe,*c['args']],input=c['input'].encode(),capture_output=True,timeout=30)
    actual={'stdout':p.stdout.decode(),'stderr':p.stderr.decode(),'exit':p.returncode}
    if args.regenerate:c['expected']=actual
    else:assert actual==c['expected'],(c['args'],actual,c['expected'])
if args.regenerate:target.write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n')
print(f'{"Imported" if args.regenerate else "Verified"} {len(cases)} CLI format modifier cases')
