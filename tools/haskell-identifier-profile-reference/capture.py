#!/usr/bin/env python3
"""Independent pinned Shared/format observations; never derive expected output from Kotlin."""
import pathlib,json,gzip,subprocess,hashlib,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'tools/haskell-identifier-profile-reference/work-in-progress/fixtures';OUT.mkdir(parents=True,exist_ok=True)
ORACLE=pathlib.Path('/tmp/kotdoc-identifier-profile/oracle')
def dump(name,rows):
 p=OUT/(name+'.jsonl.gz');assert not p.exists(),p;p.write_bytes(gzip.compress(('\n'.join(json.dumps(r,ensure_ascii=False,separators=(',',':'))for r in rows)+'\n').encode(),mtime=0));print(name,len(rows),flush=True)
def run(binary,requests):
 p=subprocess.run([str(binary)],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in requests)+'\n').encode(),capture_output=True,check=True);rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(requests);return [{'request':q,'expected':e}for q,e in zip(requests,rs)]
def s(v):return {'t':'Str','c':v}
def doc(blocks):return {'pandoc-api-version':[1,23,1,2],'meta':{},'blocks':blocks}
lower=json.loads(subprocess.check_output([str(ORACLE),'--lowercase-mappings']));assert len(lower)==1433
p=OUT/'lowercase-mappings.json';assert not p.exists();p.write_text(json.dumps(lower,ensure_ascii=False)+'\n')
old=dict(lower);new=None
allmapped=''.join(chr(n)for n in sorted(old))
# Explicit Unicode17 mapping additions absent from text2.1.2, sourced from text2.1.3 table.
resource=ROOT/'core/src/main/resources/io/heapy/kotdoc'
for p in resource.glob('*case*'):
 try:
  data=json.loads(p.read_text())
  if isinstance(data,dict) and 'lower'in data and len(data['lower'])==1460: new={int(k,16):v for k,v in data['lower'].items()};break
 except (ValueError,UnicodeError):pass
assert new is not None,'text2.1.3 mapping table not found'
deltas=[cp for cp in sorted(set(new)|set(old))if new.get(cp,chr(cp))!=old.get(cp,chr(cp))];assert len(deltas)==27
(OUT/'delta-points.json').write_text(json.dumps(deltas)+'\n')
extensions=[[],['ascii_identifiers'],['gfm_auto_identifiers'],['ascii_identifiers','gfm_auto_identifiers']]
shapes=[[],[s('123 ._ Ā Ᲊ é İ Σ \u0301 👩‍🔬')],[s('a\t b\n c\u00a0d\u2003e')],[s(''),{'t':'SoftBreak'},{'t':'LineBreak'}],[{'t':t,'c':[s('Ᲊ Ä'),{'t':'Space'},s('end')]}for t in ['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps']],*[ [ {'t':'Quoted','c':[{'t':t},[s('Ᲊ É')]]} ] for t in ['SingleQuote','DoubleQuote']],*[ [ {'t':t,'c':[['',[],[]],[s('Ᲊ é')],['https://example.com','title']]} ]for t in ['Link','Image']], [{'t':'Code','c':[['',[],[]],'Ᲊ_CODE']}],[{'t':'Math','c':[{'t':'InlineMath'},'Ᲊ_math']}],[{'t':'RawInline','c':['html','<x>Ᲊ</x>']}],[{'t':'Note','c':[{'t':'Para','c':[s('Ᲊ note')]}]}],[{'t':'Cite','c':[[],[s('Ᲊ cite')]]}],*[ [{'t':'Span','c':[attr,[s('Ᲊ 😄')]]}] for attr in [['',['emoji'],[['data-emoji','smile']]],['id',['emoji'],[['data-emoji','smile']]],['',['emoji','x'],[['data-emoji','smile']]],['',['emoji'],[['data-emoji','smile'],['x','y']]],['',[],[]]]],[s(allmapped)]]
qs=[]
for j,ex in enumerate(extensions):
 for i,xs in enumerate(shapes):qs.append({'name':f'helper/{j}/{i}','inlines':xs,'used':[],'extensions':ex})
 for cp in deltas:qs.append({'name':f'delta/{j}/{cp:x}','inlines':[s(chr(cp))],'used':[],'extensions':ex})
 for used in [['section'],['alpha','alpha-1','alpha-3'],['alpha']+[f'alpha-{n}'for n in range(1,60001)]]:qs.append({'name':f'collision/{j}/{len(used)}','inlines':[s('alpha')]if used[0]=='alpha'else [],'used':used,'extensions':ex})
dump('helper',run(ORACLE,qs))
for fmt in ['dokuwiki','muse']:
 qs=[]
 for i,ex in enumerate(extensions):
  for cp in deltas:
   header=('====== '+chr(cp)+' ======\n')if fmt=='dokuwiki'else('* '+chr(cp)+'\n')
   qs.append({'name':f'{fmt}/delta/{i}/{cp:x}','input':header+header,'extensions':['auto_identifiers']+ex})
  for enabled in [[],['auto_identifiers']]:qs.append({'name':f'{fmt}/mapping/{i}/{len(enabled)}','input':('====== 'if fmt=='dokuwiki'else'* ')+allmapped+(' ======\n'if fmt=='dokuwiki'else'\n'),'extensions':enabled+ex})
 dump(fmt+'-reader',run(pathlib.Path('/tmp/kotdoc-'+fmt+'-reader/oracle'),qs))
qs=[]
for i,ex in enumerate(extensions):
 for cp in deltas:
  for auto in [False,True]:qs.append({'name':f'muse-writer/{i}/{cp:x}/{auto}','document':doc([{'t':'Header','c':[1,[chr(cp),[],[]],[s(chr(cp))]]}]*2),'options':{'wrap':'WrapAuto','columns':72,'extensions':ex+(['auto_identifiers']if auto else []),'referenceLocation':'EndOfDocument','dpi':96,'tabStop':4}})
dump('muse-writer',run(pathlib.Path('/tmp/kotdoc-muse-writer/oracle'),qs))
