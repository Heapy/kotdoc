#!/usr/bin/env python3
"""Original Pandoc Native reader observations; never generates expectations in Kotlin."""
import argparse,gzip,hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'core/src/test/resources/native'
sys.setrecursionlimit(30000)
def sha(b):return hashlib.sha256(b).hexdigest()
def cases():
 out=[]
 def add(name,text):out.append({'name':name,'input':text})
 def roots(s):return [s,'['+s+']','Plain ['+s+']','[Plain ['+s+']]','Pandoc (Meta {unMeta=fromList []}) [Plain ['+s+']]']
 literals=['"\\X41"','"\\O101"','"\\xD800"','"\\128512\\&1"',"['a','b']", "(['a','\\X1f600'])",'[]','([])','(([]))','"a\\\n \\b"','"\\^@\\DEL"','"raw\nnewline"']
 for i,literal in enumerate(literals):
  for j,text in enumerate(roots('Str '+literal)):
   add(f'lexical-five-roots/{i}/{j}',text)
 for i,text in enumerate(['[]','[ ]','([])','(([]))','[Plain []]','Plain []','[Space]','Space','Pandoc Meta{unMeta=fromList []} []','[Str "a",Space,Str "b"]']):
  for j,tail in enumerate(['',' \t\n\r\v\f','\u2003\u00a0',' trailing',' -- comment',' {- comment -}']):add(f'fallback-eof/{i}/{j}',text+tail)
 for i,text in enumerate(['',' ','Unknown','()','[','[Space,]','[Plain [],Space]','Str','Str "x" "y"','(Str) "x"','Str "\\q"','Str "\\1114112"','Str [1]','Str [\'a\',]','Pandoc Meta{unMeta=fromList []} [] []','Pandoc Meta{bad=fromList []} []','MetaString "x"','Many {unMany=fromList []}','[Para []] --comment\n','--comment\nSpace']):add('source-rejection/'+str(i),text)
 for depth in [1,2,3,8]:
  text='('*depth+"['a']"+')'*depth
  for i,root in enumerate(roots('Str '+text)):add(f'ambiguous-first-result/{depth}/{i}',root)
 for count in [16,31,32,64,256,1024]:
  value='MetaList ['+','.join("MetaString (['a'])" for _ in range(count))+']'
  for i,tail in enumerate(['',' \u2003']):add(f'large-first-result/{count}/{i}','Pandoc Meta{unMeta=fromList [("x",'+value+')]} []'+tail)
 for depth in [32,128,256]:
  for i,text in enumerate(roots('Emph ['*depth+'Str "deep"'+']'*depth)):add(f'deep-five-roots/{depth}/{i}',text)
 for i,text in enumerate(['Str "x"','Str \'a\'','[]','([Space])']):
  for j,prefix in enumerate(['\u2003','\u00a0','\u0085','{- comment -}','-- comment\n']):add(f'leading-space-comment/{i}/{j}',prefix+text)
 # ToSources Text filters raw CR globally; escaped backslash-r remains lexical content.
 for i,inline in enumerate(['S\rtr "a"','Str "a\rb"','Str "\\x4\r1"','Str "\\O1\r01"','Str "a\\rb"',"Str ['a'\r,'b']",'Str\r"a"','S\rt\rr [\r]','Str (\r[\r])','Str "a\r\nb"']):
  for j,text in enumerate(roots(inline)):add(f'ToSources-raw-CR-five-roots/{i}/{j}',text)
 for i,text in enumerate(['\r','\r\n','Pan\rdoc Meta{unMe\rta=fromLi\rst []} []','Hea\rder 1\r2 ("",[],[]) []','Header 1\r.0 ("",[],[]) []','Str "a\\\r\nb"','Str "\\\rr"',"Str ['\\r']",'Str "a"\rtrailing','[Sp\race]','--co\rmment\nSpace']):add('ToSources-raw-CR-boundary/'+str(i),text)
 return out
SOURCES=['tools/import-native-read-integration-fixtures.py','upstream/pandoc/src/Text/Pandoc/Readers/Native.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/Sources.hs','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/src/Text/Pandoc/Definition.hs','vendor/ghc-9.12.2/GHC/Internal/Read.hs','core/src/main/kotlin/io/heapy/kotdoc/formats/NativeReader.kt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocRead.kt','core/src/test/kotlin/io/heapy/kotdoc/formats/NativeReadIntegrationTest.kt']
def hashes():return {p:sha((ROOT/p).read_bytes()) for p in SOURCES}
def main():
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--draft',action='store_true');a=p.parse_args()
 payload=DEST/'read-integration.jsonl.gz';manifest=DEST/'read-integration-manifest.json'
 if a.check_source:
  raw=gzip.decompress(payload.read_bytes());m=json.loads(manifest.read_text());rows=[json.loads(x) for x in raw.splitlines()]
  assert m['sources']==hashes() and m['payloadSha256']==sha(raw)
  assert [{'name':r['name'],'input':r['input']} for r in rows]==cases()
  assert len(rows)==m['cases'] and all(r['returncode']==0 or r['stderr']=="couldn't read native\n" for r in rows)
  print(f'Original Native integration {len(rows)} cases: source/matrix/raw CLI responses verified');return
 version=subprocess.check_output(['pandoc','--version'],text=True);assert version.splitlines()[0]=='pandoc 3.12.1'
 rows=[]
 for case in cases():
  result=subprocess.run(['pandoc','-f','native','-t','json'],input=case['input'],capture_output=True,text=True,timeout=30)
  rows.append(dict(case,returncode=result.returncode,stdout=result.stdout,stderr=result.stderr))
 raw=('\n'.join(json.dumps(r,ensure_ascii=True,separators=(',',':')) for r in rows)+'\n').encode()
 if a.draft:
  draft=Path('/tmp/kotdoc-native-integration-draft/resources/native');draft.mkdir(parents=True,exist_ok=True);(draft/'read-integration.jsonl.gz').write_bytes(gzip.compress(raw,mtime=0));print('Draft original Native',len(rows));return
 payload.write_bytes(gzip.compress(raw,mtime=0));manifest.write_text(json.dumps({'sources':hashes(),'payloadSha256':sha(raw),'cases':len(rows),'oracleVersion':version,'command':['pandoc','-f','native','-t','json']},indent=2)+'\n')
 print('Captured',len(rows),'original Native cases;',sum(r['returncode']==0 for r in rows),'accepted')
if __name__=='__main__':main()
