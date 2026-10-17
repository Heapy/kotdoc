"""Int64 Header levels: source replicate negative is empty, never narrow to Int32."""
import pathlib,json,subprocess,base64,gzip
h=pathlib.Path(__file__).resolve().parent;r=pathlib.Path('/tmp/kotdoc-org-writer');p=h/'fixtures/cli-header-int64.jsonl.gz';assert not p.exists()
exe='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc';records=[]
for row in map(json.loads,(r/'header-int64-observations.jsonl').read_text().splitlines()):
 q=row['request'];data=json.dumps(q['document']).encode();args=['-f','json','-t','org'];proc=subprocess.run([exe,'--emulate',*args],input=data,capture_output=True,check=True);b64=lambda b:base64.b64encode(b).decode();records.append(dict(name=q['name'],args=args,input=b64(data),files={},created={},exitCode=proc.returncode,stdout=b64(proc.stdout),stderr=b64(proc.stderr)))
p.write_bytes(gzip.compress(''.join(json.dumps(x)+'\n'for x in records).encode(),mtime=0));print(len(records),'original App negative Int64 levels')
