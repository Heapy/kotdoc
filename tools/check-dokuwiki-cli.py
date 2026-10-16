#!/usr/bin/env python3
"""Read-only installed DokuWiki CLI checks against immutable original expectations."""
import argparse,base64,gzip,json,pathlib,shlex,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def records(path):return [json.loads(x) for x in gzip.decompress(path.read_bytes()).splitlines()]
def main():
 p=argparse.ArgumentParser();p.add_argument('--input-only',action='store_true');p.add_argument('--warning-only',action='store_true');p.add_argument('--boundary-only',action='store_true');p.add_argument('--cli',default=str(ROOT/'cli/build/install/cli/bin/cli'));p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/dokuwiki-reader');a=p.parse_args()
 def run(args,payload=b''):
  result=subprocess.run([a.cli,*args],input=payload,capture_output=True,cwd=ROOT/'upstream/pandoc/test');assert result.returncode==0,(args,result.stderr);return result.stdout
 for file in (['cli-warnings.json'] if a.warning_only else ['cli-warnings.json','cli-input.json']):
  for r in json.loads((a.fixture_dir/file).read_text()):
   with tempfile.TemporaryDirectory() as tmp:
    for name,value in r['files'].items():(pathlib.Path(tmp)/name).write_bytes(base64.b64decode(value))
    result=subprocess.run([a.cli,*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=tmp)
    assert result.returncode==r['exitCode'] and result.stdout==base64.b64decode(r['stdout']) and result.stderr==base64.b64decode(r['stderr']),(r['args'],r['input'],result.returncode,result.stdout,result.stderr)
 print('22 exact original warning/error/trace observations passed'+('' if a.warning_only else '; 35 App.Input boundary observations passed'),flush=True)
 if a.input_only or a.warning_only:return
 for r in json.loads((a.fixture_dir/'cli-options.json').read_text()):
  result=subprocess.run([a.cli,*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=ROOT/'upstream/pandoc/test')
  assert result.returncode==r['exitCode'] and result.stdout==base64.b64decode(r['stdout']) and result.stderr==base64.b64decode(r['stderr']),(r['args'],result.stdout,result.stderr)
 print('16 original application option observations passed',flush=True)
 if a.boundary_only:return
 commands=records(a.fixture_dir/'commands.jsonl.gz');count=0
 for r in commands:
  args=shlex.split(r['command'])[1:]
  if 'muse' in args or 'rst' in args:print('PENDING dependency:',pathlib.Path(r['file']).name,r['number']);continue
  assert run(args,r['input'].encode())==r['expected'].encode(),(r['file'],r['number']);count+=1
 assert count==10
 for r in records(a.fixture_dir/'original115.jsonl.gz'):
  name=r['name'];fmt='dokuwiki-smart-auto_identifiers'
  if name.startswith('math groups and comments/'):
   group=name.split('/')[1]
   if group not in ['dollars','double dollars']:continue # Original CLI registry rejects these 32 library-only cases.
   fmt+='+'+('tex_math_dollars' if group in ['dollars','double dollars'] else 'tex_math_single_backslash' if group in ['parentheses','brackets'] else 'tex_math_double_backslash')
  assert json.loads(run(['-f',fmt,'-t','json'],r['input'].encode()))==r['expected'],name
 goldens=[('testsuite.native','writer.dokuwiki',True),('tables.native','tables.dokuwiki',False),('dokuwiki_inline_formatting.native','dokuwiki_inline_formatting.dokuwiki',True),('dokuwiki_multiblock_table.native','dokuwiki_multiblock_table.dokuwiki',True),('dokuwiki_external_images.native','dokuwiki_external_images.dokuwiki',True)]
 for source,golden,standalone in goldens:
  args=['-f','native','-t','dokuwiki','--columns=80']+(['-s'] if standalone else [])+[source]
  assert run(args)==(ROOT/'upstream/pandoc/test'/golden).read_bytes(),golden
 for kind in ['--list-input-formats','--list-output-formats']:assert 'dokuwiki' in run([kind]).decode().splitlines()
 print('Installed CLI: 10 original commands, 83 CLI-applicable original reader ASTs (32 original registry-rejected library cases), 5 original writer goldens, 16 complete application option cases including both registry rejection combinations, format listings passed; 3 Muse/RST commands pending')
if __name__=='__main__':main()
