#!/usr/bin/env python3
"""Read-only exact App checker, also usable against an isolated draft jar."""
import argparse,base64,gzip,json,os,pathlib,subprocess,tempfile,hashlib,time
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
p=argparse.ArgumentParser();p.add_argument('--classpath',required=True);p.add_argument('--report',required=True);p.add_argument('--java',default='/Users/yoda/.sdkman/candidates/java/21.0.12-librca/bin/java');p.add_argument('--only',default='');p.add_argument('--fixtures',nargs='+',default=['app.jsonl.gz','app-io.jsonl.gz']);a=p.parse_args()
rows=[];pending=[]
for filename in a.fixtures:
 for line in gzip.open(HERE/'fixtures'/filename,'rt'):
  q=json.loads(line)
  if a.only and a.only not in q['name']:continue
  args=q['args'];out='html'
  for i,arg in enumerate(args):
   if arg in ['-t','--to','-w','--write']:out=args[i+1]
   if arg.startswith('--to='):out=arg.split('=',1)[1]
  if out.split('+')[0].split('-')[0]not in ['native','json','html','html4','html5','org','rst','dokuwiki','muse']:
   pending.append(dict(name=q['name'],reason='missing writer '+out));continue
  with tempfile.TemporaryDirectory()as tmp:
   directory=ROOT/'upstream/pandoc/test'if q.get('cwd')=='test'else pathlib.Path(tmp)
   for file,raw in q['files'].items():path=directory/file;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(base64.b64decode(raw))
   proc=subprocess.run([a.java,'-Xmx512m','-Xss256k','-cp',a.classpath,'io.heapy.kotdoc.cli.MainKt',*args],input=base64.b64decode(q['input']),capture_output=True,cwd=directory,env=dict(os.environ,SOURCE_DATE_EPOCH='0',LANG='en_US.UTF-8'))
   expected=(q['exitCode'],base64.b64decode(q['stdout']),base64.b64decode(q['stderr']));actual=(proc.returncode,proc.stdout,proc.stderr);match=actual==expected
   rows.append(dict(name=q['name'],match=match,exitCode=proc.returncode,stdout=base64.b64encode(proc.stdout).decode(),stderr=base64.b64encode(proc.stderr).decode()))
   print(q['name'], 'PASS'if match else'FAIL',flush=True)
   if not match:print('EXPECTED',repr(expected),'ACTUAL',repr(actual),flush=True)
report=dict(results=rows,pending=pending,passed=sum(x['match']for x in rows),executed=len(rows),finished=time.time());pathlib.Path(a.report).write_text(json.dumps(report,ensure_ascii=True,indent=2)+'\n');print('TOTAL',report['passed'],'/',len(rows),'PENDING',len(pending),flush=True)
raise SystemExit(0 if all(x['match']for x in rows)else 1)
