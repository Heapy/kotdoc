from pathlib import Path
import ctypes as C,json,random,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',default='/tmp/kotdoc-ripemd160-oracle.dylib');p.add_argument('--trace',default='/tmp/kotdoc-ripemd160-trace.dylib');p.add_argument('--verify',action='store_true');a=p.parse_args();lib=C.CDLL(a.oracle);tr=C.CDLL(a.trace);ptr=C.c_void_p
for l,prefix in [(lib,'crypton_ripemd160_'),(tr,'observed_crypton_ripemd160_')]:
 getattr(l,prefix+'init').argtypes=[ptr];getattr(l,prefix+'update').argtypes=[ptr,ptr,C.c_uint32];getattr(l,prefix+'finalize').argtypes=[ptr,ptr]
lib.ripemd_chunk.argtypes=[ptr,ptr];tr.ripemd_observed_chunk.argtypes=[ptr,ptr];lib.ripemd_counter.argtypes=[ptr,C.c_uint64,C.c_uint32];assert lib.ripemd_size()==96
tr.ripemd_trace_copy.argtypes=[C.c_int,ptr]
rng=random.Random(6132767)
def b(n):return list(rng.randbytes(n))
def observe(r,trace=False):
 l=tr if trace else lib;prefix='observed_crypton_ripemd160_'if trace else'crypton_ripemd160_';raw=(C.c_uint8*160)(*([165]*16+r['context']+[90]*16));ctx=C.byref(raw,16);assert C.addressof(raw)%8==0
 if trace:tr.ripemd_trace_reset()
 if r.get('init'):getattr(l,prefix+'init')(ctx)
 snapshots=[]
 for chunk in r.get('chunks',[]):
  data=(C.c_uint8*(len(chunk)+6))(*([39]*3+chunk+[73]*3));getattr(l,prefix+'update')(ctx,C.byref(data,3),len(chunk));assert list(data)==[39]*3+chunk+[73]*3;snapshots.append(list(raw)[16:144])
 if r['op']=='chunk':
  data=(C.c_uint8*64)(*r['input']);(tr.ripemd_observed_chunk if trace else lib.ripemd_chunk)(ctx,data)
 if r['op']=='aliasUpdate':getattr(l,prefix+'update')(ctx,C.byref(raw,16+r['alias']),64)
 outputs=[]
 for i in range(r.get('finalize',0)):
  out=(C.c_uint8*34)(*([51]*34));target=C.byref(raw,16+r['outAlias'])if'outAlias'in r else C.byref(out,7);getattr(l,prefix+'finalize')(ctx,target);outputs.append(list(raw)[16+r['outAlias']:36+r['outAlias']]if'outAlias'in r else list(out)[7:27]);assert list(out)[:7]==[51]*7 and list(out)[27:]==[51]*7
 assert list(raw)[:16]==[165]*16 and list(raw)[144:]==[90]*16
 result=dict(context=list(raw)[16:144],snapshots=snapshots,outputs=outputs,guards=True)
 if trace:
  count=tr.ripemd_trace_count();assert count<4096;states=[]
  for i in range(count):
   out=(C.c_int32*10)();tr.ripemd_trace_copy(i,out);states.append(dict(kind='round',index=i%160,values=list(out)))
  result['trace']=states
 return result
requests=[]
for i in range(100):
 for chunks in [[],[b(i%66)],[b(1),[],b(63),b(64),b(65),b(i%129)],[b(1024)]]:
  requests.append(dict(op='update',context=b(128),init=True,chunks=chunks,finalize=2))
for counter in [0,1,7,55,56,63,64,65,2**63-1,2**63,2**64-65,2**64-1]:
 for length in [0,1,7,55,56,63,64,65,127,129]:
  context=b(128);context[:8]=list(counter.to_bytes(8,'little'));requests.append(dict(op='update',context=context,init=False,chunks=[b(length)],finalize=1))
for i in range(100):requests.append(dict(op='chunk',context=b(128),init=False,input=b(64),finalize=0))
for alias in [0,8,64]:
 for i in range(20):
  context=b(128);context[:8]=list((64*i).to_bytes(8,'little'));requests.append(dict(op='aliasUpdate',context=context,init=False,alias=alias,finalize=1))
for alias in [0,8,72,76,80,84,88,96,108]:
 for i in range(20):requests.append(dict(op='update',context=b(128),init=True,chunks=[b(i)],finalize=1,outAlias=alias))
rows=[dict(request=r,expected=observe(r))for r in requests]
traceRequests=[dict(op='chunk',context=b(128),init=False,input=b(64),finalize=0)for i in range(25)]+[dict(op='update',context=b(128),init=True,chunks=[b(i)],finalize=1)for i in [0,1,55,56,63,64,65,127]]
traces=[dict(request=r,expected=observe(r,True))for r in traceRequests]
counters=[]
for n in [0,1,15,16,2**32-1,2**63-1,2**63,2**64-17,2**64-16,2**64-1]:
 for length in [0,1,15,16,17,2**31-1,2**31,2**32-1]:
  out=(C.c_uint8*8)();lib.ripemd_counter(out,n,length);counters.append(dict(counter=str(n),length=str(length),expected=list(out)))
for name,value in [('NativeObservations.json',rows),('TraceObservations.json',traces),('CounterObservations.json',counters)]:
 data=json.dumps(value,separators=(',',':'))+'\n';f=D/name
 if a.verify:assert f.read_text()==data,name
 else:f.write_text(data)
print('PASS',len(rows),'originalC rows;',len(traces),'full 160-step round traces;',len(counters),'virtualunsignedcounter observations')
