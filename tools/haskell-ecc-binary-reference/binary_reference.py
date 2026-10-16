"""Development-only ctypes observer of the complete instrumented pinned C source."""
import ctypes as C

class Reference:
    def __init__(self,path):
        self.lib=C.CDLL(str(path))
        P8=C.POINTER(C.c_uint8);P64=C.POINTER(C.c_uint64);P32=C.POINTER(C.c_uint32);U32=C.c_uint32
        self.lib.kotdoc_source_f2m_mul.argtypes=[P8,P8,P8,P8,P8,U32,P8,U32,P8,U32]
        self.lib.kotdoc_source_f2m_mul.restype=C.c_int
        self.lib.kotdoc_binary_helper.argtypes=[U32,P64,U32,U32,P32,U32,C.c_uint64,U32]
        self.lib.kotdoc_binary_helper.restype=C.c_int
        self.lib.kotdoc_binary_reset.argtypes=[U32]
        self.lib.kotdoc_binary_stats.argtypes=[P64,P64,P64,P64,P64,P64,P64,P32]
    def stats(self):
        ops=(C.c_uint64*10)();ladder=(C.c_uint64*10)();h=C.c_uint64();reads=C.c_uint64();sizes=(C.c_uint64*16)();frees=(C.c_uint64*16)();cleared=(C.c_uint64*16)();counts=(C.c_uint32*2)()
        self.lib.kotdoc_binary_stats(ops,ladder,C.byref(h),C.byref(reads),sizes,frees,cleared,counts)
        return dict(ops=list(map(str,ops)),ladderOps=list(map(str,ladder)),hash=str(h.value),reads=str(reads.value),allocations=list(map(str,sizes[:counts[0]])),frees=frees[:counts[1]],cleared=cleared[:counts[1]])
    def observe(self,r):
        self.lib.kotdoc_binary_reset(int(r.get('fail',0)))
        if r['op']=='helper':
            n=r['n'];width=2*n+4;space=(C.c_uint64*(4*width))(*([0xa5a5a5a5a5a5a5a5]*(4*width)))
            for i,key in enumerate(['a','b','t','out']):
                for j,v in enumerate(r[key]):space[i*width+2+j]=int(v)
            terms=(C.c_uint32*len(r['terms']))(*r['terms'])
            ret=self.lib.kotdoc_binary_helper(r['kind'],space,n,r['m'],terms,len(terms),int(r['arg']),r.get('alias',0))
            return dict(ret=ret,space=list(map(str,space)),stats=self.stats())
        def buffer(bs):return (C.c_uint8*(len(bs)+7))(*([17,34,51]+bs+[68,85,102,119]))
        def ptr(a):return C.cast(C.byref(a,3),C.POINTER(C.c_uint8))
        ins={k:buffer(r[k]) for k in ['fx','b','x','y','k']};flen=len(r['b'])
        ox=ins['x'] if r.get('alias')=='point' else buffer([165]*flen)
        oy=ins['y'] if r.get('alias')=='point' else ox if r.get('alias')=='same' else buffer([165]*flen)
        ret=self.lib.kotdoc_source_f2m_mul(ptr(ox),ptr(oy),ptr(ins['x']),ptr(ins['y']),ptr(ins['k']),len(r['k']),ptr(ins['b']),flen,ptr(ins['fx']),len(r['fx']))
        return dict(ret=ret,x=list(ox),y=list(oy),inputs={k:list(a) for k,a in ins.items()},stats=self.stats())
