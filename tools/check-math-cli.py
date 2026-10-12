#!/usr/bin/env python3
"""Independent CLI math-option parity, including deprecated flags and FB2 output."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/options/math-cli.json'
parser=argparse.ArgumentParser();parser.add_argument('--import',dest='regenerate',action='store_true');args=parser.parse_args()
sources=['upstream/pandoc/src/Text/Pandoc/Options.hs','upstream/pandoc/src/Text/Pandoc/App/CommandLineOptions.hs','upstream/pandoc/src/Text/Pandoc/Writers/FB2.hs']
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
if args.regenerate:
    assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
    cases=[]
    for method in ['plain','mathml','gladtex','mathjax','katex','webtex:missing-']:
        cases.append({'args':['-f','native','-t','fb2','--math-method='+method], 'input':'[Para [Math InlineMath "x + y",Space,Math DisplayMath "x^2"]]'})
    methods=['plain','mathml','webtex','mathjax','katex','gladtex','unknown','','WEBTEX','webtex:true','webtex=','webtex:','webtex:https://example.org?q=a:b=c','plain:anything','mathml=ignored']
    methods += ['webtex:'+s for s in ['Привет','é','😀','a\nb','a\tb','a\bb','a\fb','a\rb','a\vb','a\ab','\x01','\x7f','\\','"']]
    for value in methods:
        for flag in [['--math-method',value],['--math-method='+value]]:
            cases.append({'args':['-f','native','-t','native',*flag],'input':'[]'})
    for name in ['mathml','gladtex','webtex','mathjax','katex']:
        cases.append({'args':['-f','native','-t','native','--'+name],'input':'[]'})
        if name in ['webtex','mathjax','katex']:
            for value in ['','true','https://example.org/x','Привет']:
                cases.append({'args':['-f','native','-t','native','--'+name+'='+value],'input':'[]'})
    for flags in [['--mathml','--mathml'],['--webtex=missing-','--math-method=plain'],['--math-method=plain','--webtex=missing-'],['--mathjax','--math-method=unknown']]:
        cases.append({'args':['-f','native','-t','fb2',*flags],'input':'[Para [Math InlineMath "x"]]'})
else:
    data=json.loads(target.read_text());assert data['sources']==hashes;cases=data['cases']
exe='pandoc' if args.regenerate else str(root/'cli/build/install/cli/bin/cli')
for c in cases:
    p=subprocess.run([exe,*c['args']],input=c['input'].encode(),capture_output=True,timeout=30,cwd=root)
    actual={'stdout':p.stdout.decode(),'stderr':p.stderr.decode(),'exit':p.returncode}
    if args.regenerate:c['expected']=actual
    else:assert actual==c['expected'],(c['args'],actual,c['expected'])
if args.regenerate:target.write_text(json.dumps({'sources':hashes,'cases':cases},ensure_ascii=False,indent=2)+'\n')
print(f'{"Imported" if args.regenerate else "Verified"} {len(cases)} CLI math-option cases')
