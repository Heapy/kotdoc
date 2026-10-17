"""Exact complete CLI comparisons against immutable original observations."""
import argparse,pathlib,json,base64,subprocess,tempfile,os,shlex,hashlib,concurrent.futures,time,gzip
p=argparse.ArgumentParser();p.add_argument('--classpath',required=True);p.add_argument('--report',type=pathlib.Path,required=True);p.add_argument('--affected-only',action='store_true');a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[2];r=root/'tools/haskell-rst-reader-reference/reader-fixtures/cli';java='/Users/yoda/.sdkman/candidates/java/21.0.12-librca/bin/java';rows=[];pending=[]
for record in map(json.loads,(r/'commands85-original.jsonl').read_text().splitlines()):
 b=record['binding'];args=shlex.split(b['command'])[1:];target='html'
 for n,v in enumerate(args):
  if v in ['-t','--to']:target=args[n+1]
  elif v.startswith('-t')and len(v)>2:target=v[2:]
 if target not in ['native','html','html5']or'--citeproc'in args:pending.append(dict(id=b['id'],command=b['command'],reason='unported writer or citeproc application dependency'));continue
 rows.append(dict(name=b['id'],args=args,files={},input=base64.b64encode(b['stdin'].encode()).decode(),exitCode=record['exitCode'],stdout=record['stdout'],stderr=record['stderr'],cwd=str(root/'upstream/pandoc/test')))
for file in ['app-original.json','tabstop-app-original.json','old-cli-original.json']:rows+=json.loads((r/file).read_text())
for format in ['dokuwiki-reader','muse-reader']:
 for filename,indices in [('cli-options.json',[5,6,7,13]),('cli-warnings.json',[0,1])]:
  source=json.loads((root/'core/src/test/resources'/format/filename).read_text())
  for i in indices:rows.append(dict(source[i],name=f'{format}-regression/{filename}/{i}',files=source[i].get('files',{})))
for q in map(json.loads,gzip.decompress((root/'core/src/test/resources/muse-writer/commands.jsonl.gz').read_bytes()).splitlines()):
 rows.append(dict(name='Muse4172/'+str(q['number']),args=shlex.split(q['command'])[1:],files={},input=base64.b64encode(q['input'].encode()).decode(),exitCode=0,stdout=base64.b64encode(q['expected'].encode()).decode(),stderr='',cwd=str(root/'upstream/pandoc/test')))
if a.affected_only:rows=[q for q in rows if q["name"].startswith(("io/","Old/"))]
def check(q):
 with tempfile.TemporaryDirectory()as tmp:
  for name,value in q['files'].items():(pathlib.Path(tmp)/name).write_bytes(base64.b64decode(value))
  out=subprocess.run([java,'-Xmx512m','-Xss256k','-cp',a.classpath,'io.heapy.kotdoc.cli.MainKt',*q['args']],input=base64.b64decode(q['input']),capture_output=True,cwd=(root/'upstream/pandoc/test') if q.get('cwd') else tmp,env=dict(os.environ,SOURCE_DATE_EPOCH='0'))
  actual=dict(exitCode=out.returncode,stdout=base64.b64encode(out.stdout).decode(),stderr=base64.b64encode(out.stderr).decode());expected={k:q[k]for k in actual};return dict(name=q['name'],pass_=actual==expected,actual=actual,expected=expected)
results=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=3)as pool:
 for x in pool.map(check,rows):
  results.append(x);print(('PASS 'if x['pass_']else'FAIL ')+x['name'],flush=True)
  if not x['pass_']:print(json.dumps(x,ensure_ascii=False),flush=True)
report=dict(count=len(results),passed=sum(x['pass_']for x in results),failed=sum(not x['pass_']for x in results),pending=pending,results=results);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:report[k]for k in ['count','passed','failed']}),flush=True);raise SystemExit(bool(report['failed']))
