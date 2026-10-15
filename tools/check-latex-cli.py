#!/usr/bin/env python3
"""Installed LaTeX reader checks against all original units and fixed goldens."""
import gzip,json,os,subprocess,tempfile,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];cli=root/'cli/build/install/cli/bin/cli'
with gzip.open(root/'core/src/test/resources/latex-reader/data.jsonl.gz','rt') as f:
 manifest=json.loads(next(f));cases=[json.loads(line) for line in f]
count=0
for c in cases:
 if 'nativeGolden' not in c:continue
 opts=c['request'][1];fmt='latex'
 default={'auto_identifiers','latex_macros','smart'};extensions=set(opts['extensions'])
 fmt+=''.join('+'+x for x in sorted(extensions-default))+''.join('-'+x for x in sorted(default-extensions))
 args=[str(cli),'-r',fmt,'-w','native']
 if c.get('nativeStandalone'):args.append('-s')
 source=''.join(map(chr,c['request'][2][0][1])).encode()
 r=subprocess.run(args,input=source,capture_output=True,cwd=root)
 assert r.returncode==0,(c.get('name',c.get('source')),r.returncode,r.stderr)
 expected=c['nativeGolden'].encode()+b'\n'
 assert r.stdout==expected,(c.get('name',c.get('source')),r.stdout,expected,r.stderr)
 count+=1
assert count==92,count
r=subprocess.run([str(cli),'--list-input-formats'],capture_output=True,cwd=root);assert r.returncode==0 and b'latex\n' in r.stdout
count+=1
with tempfile.TemporaryDirectory(prefix='kotdoc-latex-cli-') as tmp:
 base=Path(tmp);(base/'includes').mkdir();(base/'includes/x.tex').write_text('Included')
 env=dict(os.environ,TEXINPUTS=str(base/'includes'),TZ='UTC',SOURCE_DATE_EPOCH='0')
 for source,args,expected in [
  (b'\\input{x}',[],b'[ Para [ Str "Included" ] ]\n'),
  (b'\\today',[],b'[ Para [ Str "1970-01-01" ] ]\n'),
  (b'\\includegraphics{foo}',['--default-image-extension=png'],b'[ Para\n    [ Image ( "" , [] , [] ) [ Str "image" ] ( "foo.png" , "" )\n    ]\n]\n')]:
  r=subprocess.run([str(cli),'-f','latex','-t','native',*args],input=source,capture_output=True,cwd=tmp,env=env)
  assert r.returncode==0 and r.stdout==expected,(source,r.returncode,r.stdout,expected,r.stderr)
  count+=1
 for input_name,golden_name,args in [('latex-reader.latex','latex-reader.native',['-f','latex+raw_tex','-s']),('lhs-test.latex+lhs','lhs-test.native',['-f','latex+lhs'])]:
  source=root/'upstream/pandoc/test'/input_name;golden=root/'upstream/pandoc/test'/golden_name
  r=subprocess.run([str(cli),*args,'-t','native',str(source)],capture_output=True,cwd=root,env=env)
  assert r.returncode==0 and r.stdout==golden.read_bytes(),(input_name,r.returncode,r.stderr)
  count+=1
 for source in [b'\xff',b'\xef\xbb\xbf\r\xff']:
  r=subprocess.run([str(cli),'-f','latex','-t','native'],input=source,capture_output=True,cwd=root)
  assert r.returncode==92 and r.stdout==b'' and b'UTF-8 decoding error' in r.stderr,(r.returncode,r.stdout,r.stderr)
  count+=1
with tempfile.TemporaryDirectory(prefix='kotdoc-latex-graphicspath-') as tmp:
 base=Path(tmp);(base/'pics').mkdir()
 image=(root/'upstream/pandoc/test/fb2/test-small.png').read_bytes()
 (base/'pics/pixel.png').write_bytes(image)
 r=subprocess.run([str(cli),'-f','latex','-t','html','--extract-media','media.zip'],input=b'\\section{Example}\n\\graphicspath{{pics/}}\\includegraphics{pixel.png}',capture_output=True,cwd=tmp)
 assert r.returncode==0 and r.stderr==b'',(r.returncode,r.stderr)
 assert b'<h1 id="example">Example</h1>' in r.stdout and b'<img ' in r.stdout,r.stdout
 with zipfile.ZipFile(base/'media.zip') as z:
  names=z.namelist();assert len(names)==1,names;assert z.read(names[0])==image
 count+=1
print('Verified',count,'installed LaTeX CLI cases, including all 90 original units, both original fixed reader goldens, includes, dates, graphicspath/media extraction and typed UTF-8 errors')
