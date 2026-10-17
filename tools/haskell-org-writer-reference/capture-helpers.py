"""Independent direct private helper source observations."""
import itertools,json,pathlib,subprocess
r=pathlib.Path('/tmp/kotdoc-org-writer');qs=[]
for kind in['target','description','path','language','special','escape']:
 texts=['','c','commonlisp','r','bash','C','python','*#|','\u00ad–—’…','/x','./x','../x','#x','file:x',':x','abc:x','a_b:x','α:x','\u1c89:x','\U0001e6c0:x','x:y:z']
 if kind in['target','description']:texts+=[''.join(x)for n in range(1,6)for x in itertools.product(['x','[',']','\\'],repeat=n)]
 for text in texts:qs.append(dict(name=kind+'/'+str(len(qs)),kind=kind,input=text))
p=r/'helper-observations.jsonl';assert not p.exists();proc=subprocess.run([str(r/'helper-oracle')],input=''.join(json.dumps(q)+'\n'for q in qs).encode(),capture_output=True,check=True);rows=[json.loads(x)for x in proc.stdout.splitlines()];assert len(rows)==len(qs);p.write_text(''.join(json.dumps(dict(request=q,expected=e),separators=(',',':'))+'\n'for q,e in zip(qs,rows)));print(len(qs),'direct original helper cases')
