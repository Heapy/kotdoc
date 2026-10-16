#!/usr/bin/env python3
"""Capture complete original application observations; refuses expected overwrite."""
import argparse,base64,json,os,pathlib,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--original-cli',required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();assert not a.output.exists()
cases=[(['-f','dokuwiki','-t','json'],b'\xef\xbb\xbf=== title ===\r\n\r\ntext\r\n'),(['-f','dokuwiki','-t','json','--tab-stop=8'],b'\t* item\n'),(['-f','dokuwiki','-t','json','--preserve-tabs'],b'\t* item\n'),(['-f','dokuwiki+smart','-t','json'],b'"quoted" -- ...'),(['-f','dokuwiki+tex_math_dollars','-t','json'],b'$x$'),(['-f','dokuwiki','-t','dokuwiki'],b'=== title ===\n\n**text**'),(['-f','dokuwiki','-t','dokuwiki','-s','-V','title=Example'],b'body'),(['-f','dokuwiki','-t','dokuwiki','--template=default.dokuwiki','-V','title=Example'],b'body'),(['-f','dokuwiki','-t','dokuwiki','--wrap=none','--columns=10'],b'one two three four five six'),(['-f','dokuwiki','-t','dokuwiki','--wrap=auto','--columns=10'],b'one two three four five six'),(['-f','dokuwiki','-t','json','--trace'],b'a\n\nb\n')]
cases += [(['-t','json','writer.dokuwiki'],b''),(['-t','dokuwiki','--columns=80','-s','testsuite.native'],b''),(['-f','dokuwiki'],b'=== heading ===\nbody')]
cases += [(['-f','dokuwiki-smart-auto_identifiers+'+ext,'-t','json'],b'') for ext in ['tex_math_single_backslash','tex_math_double_backslash']]
records=[]
for args,payload in cases:
 r=subprocess.run([a.original_cli,'--emulate',*args],input=payload,capture_output=True,cwd=ROOT/'upstream/pandoc/test',env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
 records.append(dict(exitCode=r.returncode,args=args,input=base64.b64encode(payload).decode(),stdout=base64.b64encode(r.stdout).decode(),stderr=base64.b64encode(r.stderr).decode()))
a.output.write_text(json.dumps(records,indent=2)+'\n')
