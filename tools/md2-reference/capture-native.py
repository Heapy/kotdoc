from pathlib import Path
import ctypes as C,json,random,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',default='/tmp/kotdoc-md2-oracle.dylib');p.add_argument('--trace',default='/tmp/kotdoc-md2-trace.dylib');p.add_argument('--verify',action='store_true');a=p.parse_args();lib=C.CDLL(a.oracle);tr=C.CDLL(a.trace);ptr=C.c_void_p
for l,prefix in [(lib,'crypton_md2_'),(tr,'observed_md2_')]:
 getattr(l,prefix+'init').argtypes=[ptr];getattr(l,prefix+'update').argtypes=[ptr,ptr,C.c_uint32];getattr(l,prefix+'finalize').argtypes=[ptr,ptr]
lib.md2_chunk.argtypes=[ptr,ptr];tr.md2_observed_chunk.argtypes=[ptr,ptr];lib.md2_counter.argtypes=[ptr,C.c_uint64,C.c_uint32];assert lib.md2_size()==56
tr.md2_trace_copy.argtypes=[C.c_int,ptr]
rng=random.Random(6132767)
def b(n):return list(rng.randbytes(n))
def observe(r,trace=False):
 l=tr if trace else lib;prefix='observed_md2_'if trace else'crypton_md2_';raw=(C.c_uint8*128)(*([165]*16+r['context']+[90]*16));ctx=C.byref(raw,16);assert C.addressof(raw)%8==0
 if trace:tr.md2_trace_reset()
 if r.get('init'):getattr(l,prefix+'init')(ctx)
 snapshots=[]
 for chunk in r.get('chunks',[]):
  data=(C.c_uint8*(len(chunk)+6))(*([39]*3+chunk+[73]*3));getattr(l,prefix+'update')(ctx,C.byref(data,3),len(chunk));assert list(data)==[39]*3+chunk+[73]*3;snapshots.append(list(raw)[16:112])
 if r['op']=='chunk':
  data=(C.c_uint8*16)(*r['input']);(tr.md2_observed_chunk if trace else lib.md2_chunk)(ctx,data)
 if r['op']=='aliasUpdate':getattr(l,prefix+'update')(ctx,C.byref(raw,16+r['alias']),16)
 outputs=[]
 for i in range(r.get('finalize',0)):
  out=(C.c_uint8*30)(*([51]*30));target=C.byref(raw,16+r['outAlias'])if'outAlias'in r else C.byref(out,7);getattr(l,prefix+'finalize')(ctx,target);outputs.append(list(raw)[16+r['outAlias']:32+r['outAlias']]if'outAlias'in r else list(out)[7:23]);assert list(out)[:7]==[51]*7 and list(out)[23:]==[51]*7
 assert list(raw)[:16]==[165]*16 and list(raw)[112:]==[90]*16
 result=dict(context=list(raw)[16:112],snapshots=snapshots,outputs=outputs,guards=True)
 if trace:
  count=tr.md2_trace_count();assert count<4096;states=[]
  for i in range(count):
   out=(C.c_uint8*48)();tr.md2_trace_copy(i,out);states.append(dict(kind='round'if tr.md2_trace_kind(i)==1 else'checksum',index=tr.md2_trace_index(i),values=list(out)[:tr.md2_trace_length(i)]))
  result['trace']=states
 return result
requests=[]
for i in range(100):
 for chunks in [[],[b(i%18)],[b(1),[],b(15),b(16),b(17),b(i%65)],[b(1024)]]:
  requests.append(dict(op='update',context=b(96),init=True,chunks=chunks,finalize=2))
for counter in [0,1,7,15,16,17,31,2**63-1,2**63,2**64-17,2**64-16,2**64-1]:
 for length in [0,1,7,15,16,17,31,32,33,65]:
  context=b(96);context[:8]=list(counter.to_bytes(8,'little'));requests.append(dict(op='update',context=context,init=False,chunks=[b(length)],finalize=1))
for i in range(100):requests.append(dict(op='chunk',context=b(96),init=False,input=b(16),finalize=0))
for alias in [0,8,24,40]:
 for i in range(20):
  context=b(96);context[:8]=list((16*i).to_bytes(8,'little'));requests.append(dict(op='aliasUpdate',context=context,init=False,alias=alias,finalize=1))
for alias in [0,8,40,56,80]:
 for i in range(20):requests.append(dict(op='update',context=b(96),init=True,chunks=[b(i)],finalize=1,outAlias=alias))
rows=[dict(request=r,expected=observe(r))for r in requests]
traceRequests=[dict(op='chunk',context=b(96),init=False,input=b(16),finalize=0)for i in range(25)]+[dict(op='update',context=b(96),init=True,chunks=[b(i)],finalize=1)for i in [0,1,15,16,17,31,32,33]]
traces=[dict(request=r,expected=observe(r,True))for r in traceRequests]
counters=[]
for n in [0,1,15,16,2**32-1,2**63-1,2**63,2**64-17,2**64-16,2**64-1]:
 for length in [0,1,15,16,17,2**31-1,2**31,2**32-1]:
  out=(C.c_uint8*8)();lib.md2_counter(out,n,length);counters.append(dict(counter=str(n),length=str(length),expected=list(out)))
for name,value in [('NativeObservations.json',rows),('TraceObservations.json',traces),('CounterObservations.json',counters)]:
 data=json.dumps(value,separators=(',',':'))+'\n';f=D/name
 if a.verify:assert f.read_text()==data,name
 else:f.write_text(data)
print('PASS',len(rows),'originalC rows;',len(traces),'fullround/checksum traces;',len(counters),'virtualunsignedcounter observations')
