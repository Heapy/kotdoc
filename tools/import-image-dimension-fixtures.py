#!/usr/bin/env python3
# Original examples: Tests.ImageSize, © 2025 John MacFarlane, GPL-2.0-or-later.
"""Original Pandoc SVG/EPS/PDF size and LaTeX dimension-rendering captures.

Integer fields travel through tostring, preserving even dimensions beyond 64 bits.
Separate typed tests also check arbitrary-precision numeric operations.
"""
import argparse,gzip,hashlib,json,os,random,subprocess,tempfile,zlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/images/dimensions.jsonl.gz'
sources=['tools/import-image-dimension-fixtures.py','upstream/pandoc/src/Text/Pandoc/ImageSize.hs','upstream/pandoc/test/Tests/ImageSize.hs',
 'upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/src/Text/Pandoc/Writers/LaTeX.hs',
 'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Image.hs','upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/ImageSize.hs']
sources += [str(p.relative_to(root)) for p in sorted((root/'vendor/ghc-9.12.2').rglob('*')) if p.is_file()]
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');args=p.parse_args()
if args.check_source:
    with gzip.open(target,'rt') as f: manifest=json.loads(next(f));cases=[json.loads(line) for line in f]
    assert manifest['sources']==hashes and manifest['cases']==len(cases)
    assert manifest['caseHash']==hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
    print(f'Verified {len(cases)} document-image and dimension captures');raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
cases=[]
def image(name,kind,data,dpi=96,expected=None):
    if isinstance(data,str):data=data.encode()
    c={'name':name,'kind':kind,'hex':data.hex(),'dpi':str(dpi)}
    if expected is not None:c['assertSize']=list(map(str,expected))
    cases.append(c)
def svg(attrs):return '<svg xmlns="http://www.w3.org/2000/svg" '+attrs+'></svg>'
def eps(coords):return '%!PS EPSF\n%%BoundingBox: '+coords+'\n'
def pdf(coords):return '%PDF-1.4\n<</MediaBox ['+coords+']>>'
def stream(data):return b'%PDF-1.5\n<</Type /ObjStm>>\nstream\n'+data+b'\nendstream\n'
for coords,expect in [('0 0 612 792',[612,792,72,72]),('10 20 110 220',[100,200,72,72]),('-10 -10 90 190',[100,200,72,72])]:image('original eps '+coords,'eps',eps(coords),expected=expect)
compressed=bytes([120,156,179,177,209,247,77,77,201,76,116,202,175,80,136,54,80,48,80,48,52,48,80,48,50,48,136,181,179,3,0,104,186,6,232])
image('original pdf MediaBox','pdf',pdf('0 0 612 792'),expected=[612,792,72,72])
image('original pdf compressed','pdf',stream(compressed),expected=[100,200,72,72])
image('original pdf corrupt','pdf',stream(b'NOT ZLIB DATA'))
image('original pdf corrupt then MediaBox','pdf',stream(b'NOT ZLIB DATA')+b'<</MediaBox [0 0 300 400]>>',expected=[300,400,72,72])
image('original pdf empty then MediaBox','pdf',b'%PDF-1.5\n<</Type /ObjStm>>\nstream\nendstream<</MediaBox [0 0 25 50]>>',expected=[25,50,72,72])
for attrs,expect in [('width="50" height="60"',[50,60,96,96]),('viewBox="0 0 100 200"',[100,200,96,96]),('viewBox="0,0,100,200"',[100,200,96,96]),('viewBox="0, 0, 210.5, 297.3"',[210,297,96,96]),('',None)]:image('original svg '+attrs,'svg',svg(attrs),expected=expect)
# Each unit, numeric grammar boundaries and text whitespace. LaTeX's image
# option value directly exposes dimension Show/showInInch/showFl.
lengths=['','0','1','3','3.5','0.5','.5','3.','3..5','+3','-3',' 3','3e2','1E-2','0x10','0b10','NaN','Infinity','3 cm','3cm ',
 '1.234565','1.234575','1.999995','0.000005','0.000015','99999.999995','0.0000001','100000000000000000000000', '0.'+'0'*323+'5', '9'*310]
units=['','px','cm','mm','in','inch','pt','pc','em','%','CM','ch',' cm','\tcm','\u00a0cm','\u0085cm','\u2028cm','\u2003cm']
for n in lengths:
    for unit in units:
        value=n+unit
        if '"' not in value:image('svg length '+repr(value),'svg',svg('width="'+value+'" height="1"'))
        cases.append({'name':'latex length '+repr(value),'kind':'dimension','width':value,'dpi':'96'})
for dpi in [-9223372036854775808,-1,0,1,72,96,300,9223372036854775807]:
    for value in ['0px','1px','1cm','2.54cm','72pt','1in','1em','50%','999999999999999999999999999999px']:
        image('svg dpi '+str(dpi)+' '+value,'svg',svg('width="'+value+'" height="1in"'),dpi)
        cases.append({'name':'latex dpi '+str(dpi)+' '+value,'kind':'dimension','width':value,'dpi':str(dpi)})
for value in ['0','-0','-1','+1','1.5','-1.5','.5','1.','1e2','1e-2','0x10','0o10','0b10','(3)','((3))','(-3)','-(3)','NaN','Infinity','-Infinity','1e999999999999999999999','0e999999999999999999999','1e-999999999999999999999','1_000','١٢','9'*100]:
    image('svg viewbox '+value,'svg',svg('viewBox="0 0 '+value+' 2"'))
    image('eps numeric '+value,'eps',eps('0 0 '+value+' 2'))
    image('pdf numeric '+value,'pdf',pdf('0 0 '+value+' 2'))
for space in [' ','\t','\r','\n','\v','\f','\u0085','\u00a0','\u2003','\u2028',',',',,']:
    image('svg whitespace '+repr(space),'svg',svg('viewBox="'+space.join(['0','0','4','5'])+'"'))
    image('eps whitespace '+repr(space),'eps',eps(space.join(['0','0','4','5'])))
    image('pdf whitespace '+repr(space),'pdf',pdf(space.join(['0','0','4','5'])))
for attrs in ['width="20%" viewBox="0 0 30 40"','width="20" viewBox="0 0 30 40"','height="20" viewBox="0 0 30 40"',
 'width="bad" width="30" height="40"','width="30" width="bad" height="40"','x:width="30" height="40" xmlns:x="urn:x"',
 'WIDTH="30" height="40"','viewbox="0 0 30 40"','viewBox="0 0 30 40 50"','viewBox="bad 0 30 40"','width="&#51;0" height="4&#48;"',
 'width="3\r0" height="40"','width="30" height="40" viewBox="bad"']:
    image('svg attributes '+attrs,'svg',svg(attrs))
for prefix in [b'',b'\xef\xbb\xbf',b'\xef\xbb\xbf'*2,b'\xef\xbb\xbf'*3,'\ufeef'.encode()]:
    image('svg BOM '+prefix.hex(),'svg',prefix+svg('width="30" height="40"').encode())
for value in [b'<svg width="30" height="40"/>junk',b'<svg width="30" height="40"/><svg/>',b'<svg width="30" height="40">',
 b'<svg width="30" height="40">\xff</svg>',b'<svg width="30" height="40">\xc2\r\xa0</svg>',b'<html width="30" height="40"><svg/></html>',
 b'<?xml version="1.0"?><html width="30" height="40"><svg/></html>',b'<!-- c --><svg width="30" height="40"/>']:
    # Some inputs do not pass imageType; retain format captures without claiming
    # that image.size directly exposes the private svgSize on these inputs.
    image('svg syntax '+value.hex(),'svg',value)
for value in ['%!PS EPSF\n\n%%BoundingBox: 0 0 4 5','%!PS EPSF\nbody\n%%BoundingBox: 0 0 4 5',
 '%!PS EPSF\n%%BoundingBox: bad\n%%BoundingBox: 0 0 4 5','%!PS EPSF\n%%BoundingBox:extra 0 0 4 5',
 '%!PS EPSF\n%%BoundingBox:0 0 4 5','%!PS EPSF\r%%BoundingBox: 0 0 4 5']:
    image('eps syntax '+value,'eps',value)
# PDF parser alternatives/backtracking, compressed streams and malformed trailers.
for body in [b'none',b'/MediaBox [0 0 7 8]',b'/MediaBox [-1 0 7 8] /MediaBox [0 0 9 10]',stream(zlib.compress(b'/MediaBox [0 0 11 12]'))]:
    data=zlib.compress(body)
    for payload in [data,data[:-1],data[:2]+b'bad',data+b'trailing',data+zlib.compress(b'/MediaBox [0 0 99 99]')]:
        for after in [b'',b'/MediaBox [0 0 21 22]']:
            image('pdf compressed '+str(len(cases)),'pdf',stream(payload)+after)
for payload in [b'/MediaBox [0 0 13 14]',b'bad',b'']:
    for newline in [b'\n',b'\r',b'\r\n',b'\n\r\n']:
        for opening in [b'stream',b' stream',b'stream ',b'Xstream']:
            image('pdf raw stream '+str(len(cases)),'pdf',b'%PDF/Type /ObjStm'+newline+opening+newline+payload+newline+b'endstream')
for header in [b'/Type/ObjStm',b'/Type /ObjStm',b'/Type\t/ObjStm',b'/Type/ObjStmstream\n',b'/Type /ObjStm/MediaBox [0 0 31 32]']:
    image('pdf stream header '+str(len(cases)),'pdf',b'%PDF'+header+b'\nstream\n'+compressed+b'\nendstream')
for text in ['/MediaBox [0 0 3.9 4.9]','/MediaBox [1.9 2.9 5.1 7.1]','/MediaBoxX [0 0 1 2]','/MediaBox [0 0 1 2 3] /MediaBox [0 0 8 9]',
 '/MediaBox [0 0 1 2','/MediaBox [0 0 1 2]/MediaBox [0 0 8 9]','/MediaBox [0 0 1./MediaBox [0 0 8 9]']:
    image('pdf syntax '+text,'pdf','%PDF'+text)
for byte in range(256):
    separator=bytes([byte])
    image(f'eps byte separator {byte}','eps',b'%!PS EPSF\n%%BoundingBox: '+separator.join([b'0',b'0',b'4',b'5']))
    image(f'pdf byte separator {byte}','pdf',b'%PDF/MediaBox ['+separator.join([b'0',b'0',b'4',b'5'])+b']')
rng=random.Random(0x554e4954)
for i in range(350):
    value=str(rng.randrange(0,10**15)/10**rng.randrange(0,10))
    unit=rng.choice(['px','cm','mm','in','pt','pc','em','%'])
    dpi=rng.choice([0,1,72,96,300])
    image(f'svg seeded {i}','svg',svg(f'width="{value}{unit}" height="2" viewBox="0 0 50 60"'),dpi)
    cases.append({'name':f'latex seeded {i}','kind':'dimension','width':value+unit,'dpi':str(dpi)})
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory);request=temp/'request.json';request.write_text(json.dumps(cases))
    script=temp/'oracle.lua';script.write_text('''
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_IMAGE_REQUEST')))
 local cases=pandoc.json.decode(f:read('*a'),false);f:close()
 local result={}
 for _,c in ipairs(cases) do
  local row={}
  if c.kind=='dimension' then
   local img=pandoc.Image({},'image.png','',pandoc.Attr('',{},{width=c.width}))
   row.latex=pandoc.write(pandoc.Pandoc{pandoc.Para{img}},'latex',{dpi=tonumber(c.dpi)})
  else
   local bytes=c.hex:gsub('..',function(pair) return string.char(tonumber(pair,16)) end)
   row.format=pandoc.image.format(bytes) or false
   if row.format==c.kind then
    local ok,value=pcall(pandoc.image.size,bytes,{dpi=tonumber(c.dpi)})
    if ok then row.size={tostring(value.width),tostring(value.height),tostring(value.dpi_horz),tostring(value.dpi_vert)}
    else row.error=tostring(value) end
   end
  end
  result[#result+1]=row
 end
 return pandoc.json.encode(result)
end
''')
    isolated=[];normal=[]
    for index,c in enumerate(cases):
        try:
            if c['kind']=='svg':bytes.fromhex(c['hex']).removeprefix(b'\xef\xbb\xbf').removeprefix(b'\xef\xbb\xbf').replace(b'\r',b'').decode('utf-8')
            normal.append(index)
        except UnicodeDecodeError:isolated.append(index)
    command=['pandoc','-f','native','-t',str(script)]
    env={**os.environ,'KOTDOC_IMAGE_REQUEST':str(request)}
    request.write_text(json.dumps([cases[i] for i in normal]))
    captured=json.loads(subprocess.check_output(command,input=b'[]',env=env))
    results=[None]*len(cases)
    for i,r in zip(normal,captured):results[i]=r
    for i in isolated:
        request.write_text(json.dumps([cases[i]]))
        proc=subprocess.run(command,input=b'[]',capture_output=True,env=env)
        assert proc.returncode!=0 and b'Invalid UTF-8 stream' in proc.stderr
        results[i]={'exception':'invalid-utf8','exitCode':proc.returncode,'stderr':proc.stderr.decode()}
assert len(results)==len(cases)
for c,r in zip(cases,results):
    c.update(r)
    if 'assertSize' in c:assert c.pop('assertSize')==c['size'],c
manifest={'sources':hashes,'oracle':'pandoc 3.12.1','cases':len(cases),'caseHash':hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()}
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} cases: {sum("latex" in c for c in cases)} dimension renderings, {sum("size" in c for c in cases)} sizes, {sum("error" in c for c in cases)} errors')
