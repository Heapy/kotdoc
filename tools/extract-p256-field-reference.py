#!/usr/bin/env python3
"""Extract pinned field constants/product/reducer expressions into reviewable static Kotlin.

Only a small integer-expression subset is translated. Independent expectations
are produced by separately compiled, unchanged C headers, never by this tool.
"""
import re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def original(bits):return (ROOT/f'vendor/crypton-2.0.1/cbits/include{bits}/p256/p256_gf.h').read_text()
def clean(s):return re.sub(r'/\*.*?\*/|//[^\n]*','',s,flags=re.S)
def body(s,name):
 start=s.index('{',s.index(name+'('));level=1;at=start+1
 while level:
  level+=(s[at]=='{')-(s[at]=='}');at+=1
 return s[start+1:at-1]

def tables():
 out=['// Copyright 2013 The Android Open Source Project.','// Full notice: licenses/crypton-p256-BSD-3-Clause.txt.','// Generated from both pinned p256_gf.h headers; verify with extract-p256-field-reference.py --check.','package io.heapy.kotdoc.crypto','','internal object CryptoP256FieldTables {','    fun get(bits: Int, name: String): LongArray = when (bits) {']
 for bits in [32,64]:
  s=original(bits);out.append(f'        {bits} -> when (name) {{')
  names={'one':'kOne','zero':'kZero','p':'kP','2p':'k2P','precomputed':'kPrecomputed','zeroOffset':'zero31' if bits==32 else 'zero53','r':'kR','rInv':'kRInv'}
  n=9 if bits==32 else 5
  for name,cname in names.items():
   if name=='zeroOffset':
    if bits==32:values=[(1<<31)-8,(1<<30)-4,(1<<31)-4,(1<<30)+(1<<13)-4,(1<<31)-4,(1<<30)-4,(1<<31)+(1<<24)-4,(1<<30)-(1<<27)-4,(1<<31)-4]
    else:values=[(1<<53)-8,(1<<54)-(1<<52)+(1<<48)-4,(1<<53)-4+1,(1<<54)-(1<<52)+(1<<41)-4,(1<<53)-(1<<21)-4+1]
   else:
    if name in ['r','rInv']:v=re.search(r'#define '+('kRDigits' if name=='r' else 'kRInvDigits')+r'\s*\{([^}]+)\}',s)[1]
    else:v=re.search(r'\b'+cname+r'(?:\[[^]]+\])?\s*=\s*\{([^}]+)\}',s)[1]
    values=[int(x.strip(),0) for x in v.split(',') if x.strip()]
    size=(256//bits if name in ['r','rInv'] else n*60 if name=='precomputed' else n)
    values+= [0]*(size-len(values));assert len(values)==size
   literal=','.join(f'0x{x:x}uL.toLong()' if x>=1<<63 else f'0x{x:x}L' for x in values)
   out.append(f'            "{name}" -> longArrayOf({literal})')
  out+=['            else -> error(name)','        }']
 out+=['        else -> error("unsupported digit profile $bits")','    }','}'];return '\n'.join(out)+'\n'

# Explicit integer expression tree; parentheses preserve C precedence in Kotlin.
class Expr:
 def __init__(self,text,bits,types):
  self.ts=re.findall(r'0x[0-9a-fA-F]+|[A-Za-z_]\w*|\d+|<<|>>|\+=|-=|\|=|&=|\*=|==|!=|<=|>=|\+\+|\S',text);self.at=0;self.bits=bits;self.types=types
 def peek(self):return self.ts[self.at] if self.at<len(self.ts) else None
 def pop(self):v=self.peek();self.at+=1;return v
 def parse(self,minp=0):
  t=self.pop()
  if t=='(':
   if self.peek() in ['limb','u32','u64','u128'] and self.ts[self.at+1]==')':
    kind={'limb':self.bits,'u32':32,'u64':64,'u128':128}[self.pop()];self.pop();node=('cast',kind,self.parse(12))
   else:node=self.parse();assert self.pop()==')'
  elif t in ['-','~']:node=('unary',t,self.parse(12))
  elif t[0].isdigit():node=('literal',int(t,0))
  else:node=('name',t)
  if self.peek()=='(' and node[0]=='name':
   self.pop();arg=self.parse();assert self.pop()==')';node=('call',node[1],arg)
  if self.peek()=='[':
   self.pop();idx=[];level=1
   while level:
    x=self.pop();level+=(x=='[')-(x==']')
    if level:idx.append(x)
   node=('index',node,''.join(idx))
  prec={'|':1,'^':2,'&':3,'==':4,'!=':4,'<':5,'>':5,'<=':5,'>=':5,'<<':6,'>>':6,'+':7,'-':7,'*':8}
  while self.peek() in prec and prec[self.peek()]>=minp:
   op=self.pop();rhs=self.parse(prec[op]+1);node=('binary',op,node,rhs)
  return node
 def kind(self,n):
  tag=n[0]
  if tag=='literal':return 'literal'
  if tag=='name':return self.types.get(n[1],self.bits)
  if tag=='index':return self.kind(n[1])
  if tag=='cast':return n[1]
  if tag=='call':return self.bits
  if tag=='unary':return self.kind(n[2])
  a=self.kind(n[2]);b=self.kind(n[3])
  if a=='index' or b=='index':return 'index'
  if n[1] in ['<<','>>']:return a
  return max(32 if a=='literal' else a,32 if b=='literal' else b)
 def render(self,n,want=None):
  tag=n[0];kind=self.kind(n);want=kind if want is None else want
  if tag=='literal':code=str(n[1]) if want=='index' else f'{n[1]}L';kind=32
  elif tag=='name':code={'in':'a','in2':'b','NLIMBS':'limbs','kBottom29Bits':'0x1fffffffL','kBottom28Bits':'0xfffffffL','kBottom51Bits':'0x7ffffffffffffL','kBottom52Bits':'0xfffffffffffffL'}.get(n[1],n[1])
  elif tag=='index':code=f'{self.render(n[1])}[{n[2]}]'
  elif tag=='call':code='ones('+self.render(n[2])+')'
  elif tag=='cast':
   code=self.render(n[2],n[1]);kind=n[1]
  elif tag=='unary':code=f'(-{self.render(n[2])})' if n[1]=='-' else f'({self.render(n[2])}.inv())'
  else:
   op=n[1];target=self.kind(n)
   if op in ['<<','>>']:
    code=f'({self.render(n[2],target)} {"shl" if op=="<<" else "ushr"} {self.render(n[3],"index")})'
   else:
    operator={'&':'and','|':'or','^':'xor'}.get(op,op)
    if target==128 and op=='*':
     # All source wide products are casts from original limbs.
     def narrow(x):return self.render(x[2] if x[0]=='cast' else x,64)
     code=f'Wide.product({narrow(n[2])}, {narrow(n[3])})'
    else:code=f'({self.render(n[2],target)} {operator} {self.render(n[3],target)})'
   if target==32:code=f'({code} and 0xffffffffL)'
  if want==128 and kind!=128:return f'Wide.of({code})'
  if want!=128 and kind==128:return f'({code}).low'
  if want==32 and kind!=32:return f'({code} and 0xffffffffL)'
  return code
 def kotlin(self):
  n=self.parse();assert self.peek() is None,(self.ts[self.at:],self.ts)
  return self.render(n)

def expr(s,bits,types):return Expr(s,bits,types).kotlin()
def product(bits,square):
 s=clean(body(original(bits),'felem_square' if square else 'felem_mul'))
 types={'in':bits,'in2':bits,'tmp':64 if bits==32 else 128,'x1x1':128,'x3x3':128,'x1y1':128,'x1y3':128,'x3y1':128,'x3y3':128}
 out=[]
 for name,rhs in re.findall(r'\b(x\d[xy]\d|tmp\[\d+\])\s*=\s*([^;]+);',s):
  value=expr(rhs,bits,types)
  out.append(('val ' if not name.startswith('tmp') else '')+name+' = '+value)
 assert sum(x.startswith('tmp') for x in out)==(17 if bits==32 else 9)
 return '\n'.join('            '+x for x in out)

# Reducer control flow is retained exactly; only the numeric expressions change syntax.
def reducer(bits):
 s=clean(body(original(bits),'felem_reduce_degree'))
 s=re.sub(r'(if\s*\([^\n]*\))\s*break;',r'\1 { break; }',s)
 s=re.sub(r'\blimb\s+[^;]+;','',s);s=re.sub(r'\bunsigned\s+[^;]+;','',s)
 types={'tmp':64 if bits==32 else 128,'tmp2':bits,'carry':bits,'x':bits,'xMask':bits,'xShiftedMask':bits,'i':'index','NLIMBS':'index'}
 # Tokenize statements and braces, preserving for parentheses.
 tokens=[];part='';depth=0
 for ch in s:
  if ch=='(':depth+=1
  if ch==')':depth-=1
  if ch in '{};' and depth==0:
   if part.strip():tokens.append(part.strip());part=''
   tokens.append(ch)
  else:part+=ch
 assert not part.strip()
 pos=0
 def statement(indent):
  nonlocal pos
  t=tokens[pos];pos+=1;pad=' '*indent
  if t=='{':
   lines=[]
   while tokens[pos]!='}':lines+=statement(indent)
   pos+=1;return lines
  if t.startswith('for '):
   start,cond,step=re.match(r'for\s*\((.*);(.*);(.*)\)',t).groups()
   assert start.strip() in ['i = 0','i = 2'];init=int(start.split('=')[1]);condition='true' if not cond.strip() else cond.strip()
   increment='i += 2' if '+=' in step else 'i++'
   lines=[pad+f'i = {init}',pad+f'while ({condition}) {{']+statement(indent+4)+[pad+'    '+increment,pad+'}'];return lines
  if t.startswith('if '):
   condition=re.match(r'if\s*\((.*)\)',t)[1]
   return [pad+f'if ({condition.replace("NLIMBS","limbs")}) {{']+statement(indent+4)+[pad+'}']
  if t==';':return []
  if t=='break':return [pad+'break']
  if t=='i++':return [pad+t]
  if t.startswith('felem_reduce_carry'):
   return [pad+'reduceCarry(out, carry, audit)']
  m=re.fullmatch(r'(tmp2\[[^]]+\]|out\[[^]]+\]|carry|x|xMask|xShiftedMask)\s*(=|\+=|-=|\|=|&=)\s*(.*)',t,flags=re.S)
  assert m,t
  lhs,op,rhs=m.groups()
  if op!='=':rhs=f'{lhs} {op[0]} ({rhs})'
  value=expr(rhs,bits,types)
  if bits==32:value=f'({value} and 0xffffffffL)'
  return [pad+lhs+' = '+value]
 result=[]
 while pos<len(tokens):result+=statement(8)
 # snapshots are outside the arithmetic expression transformations.
 result='\n'.join(result)
 result=result.replace('        i = 0\n        while (true)', '        audit?.snapshot("normalized", tmp2)\n        i = 0\n        while (true)',1)
 marker='            if (i+1 == limbs) {'
 assert marker in result
 result=result.replace(marker,'            audit?.snapshot("eliminate-even", tmp2)\n'+marker)
 # The odd phase always ends just before the +=2 loop step.
 marker='            i += 2'
 assert result.count(marker)==1
 result=result.replace(marker,'            audit?.snapshot("eliminate-odd", tmp2)\n'+marker)
 return result

def generated():
 pieces=[]
 for bits in [32,64]:
  typ='LongArray' if bits==32 else 'Array<Wide>'
  init='LongArray(17)' if bits==32 else 'Array(9) { Wide.of(0) }'
  pieces += [f'    private fun products{bits}(a: Region, b: Region, square: Boolean): {typ} {{',f'        val tmp = {init}','        if (square) {',product(bits,True),'        } else {',product(bits,False),'        }','        return tmp','    }']
 pieces += ['    fun reduceDegree(out: Region, low: LongArray, high: LongArray? = null, audit: Audit? = null) {','        audit?.enter(3)','        audit?.snapshot("product-low", low)','        if (bits == 32) reduceDegree32(out, low, audit) else {','            require(high != null)','            audit?.snapshot("product-high", high)','            reduceDegree64(out, Array(9) { Wide(low[it], high[it]) }, audit)','        }','    }']
 for bits in [32,64]:
  pieces += [f'    private fun reduceDegree{bits}(out: Region, tmp: {"LongArray" if bits==32 else "Array<Wide>"}, audit: Audit?) {{',f'        val tmp2 = LongArray({18 if bits==32 else 10})','        var carry: Long','        var x: Long',f'        var {"xMask" if bits==32 else "xShiftedMask"}: Long','        var i: Int',reducer(bits),'    }']
 return '\n'.join(pieces)+'\n'

def observed(bits):
 s=original(bits)
 names=['felem_reduce_carry','felem_sum','felem_diff','felem_reduce_degree','felem_square','felem_mul','felem_assign','felem_scalar_3','felem_scalar_4','felem_scalar_8','felem_is_zero_vartime','to_montgomery','from_montgomery']
 def snap(label,array,size,wide=False):
  if wide:
   return '{ uint64_t low['+str(size)+'],high['+str(size)+'];for(int z=0;z<'+str(size)+';z++){low[z]=(uint64_t)'+array+'[z];high[z]=(uint64_t)('+array+'[z]>>64);}field_snapshot("product-low",low,'+str(size)+');field_snapshot("product-high",high,'+str(size)+'); }'
  return '{ uint64_t v['+str(size)+'];for(int z=0;z<'+str(size)+';z++)v[z]='+array+'[z];field_snapshot("'+label+'",v,'+str(size)+'); }'
 for i,name in enumerate(names):
  start=s.index('{',s.index(name+'('));s=s[:start+1]+f'\n field_ops[{i}]++;'+s[start+1:]
 n=9 if bits==32 else 5;tn=17 if bits==32 else 9
 # Insert using exact original expressions and lexical function boundaries.
 def change(name,old,new):
  nonlocal s
  text=body(s,name);assert text.count(old)==1,(name,old)
  start=s.index('{',s.index(name+'('))+1;s=s[:start]+text.replace(old,new)+s[start+len(text):]
 text=body(s,'felem_reduce_carry');start=s.index('{',s.index('felem_reduce_carry('))+1
 s=s[:start]+text+'\n'+snap('carry','inout',n)+'\n'+s[start+len(text):]
 change('felem_reduce_degree','  limb tmp2[', '  '+snap('product-low','tmp',tn,bits==64)+'\n  limb tmp2[')
 change('felem_reduce_degree','  for (i = 0;; i += 2) {','  '+snap('normalized','tmp2',tn+1)+'\n  for (i = 0;; i += 2) {')
 change('felem_reduce_degree','    if (i+1 == NLIMBS)','    '+snap('eliminate-even','tmp2',tn+1)+'\n    if (i+1 == NLIMBS)')
 last='    tmp2[i + 10] += (x - 1) & xMask;' if bits==32 else '    tmp2[i + 6] += (x >> 2) - (x >> 34);'
 change('felem_reduce_degree',last,last+'\n    '+snap('eliminate-odd','tmp2',tn+1))
 change('felem_is_zero_vartime','  } while (carry);','    '+snap('zero-pass','tmp',n)+'\n  } while (carry);')
 change('to_montgomery','  crypton_p256_clear(&in_shifted);','  crypton_p256_clear(&in_shifted);\n  '+snap('to-cleared','in_shifted.a',256//bits))
 change('from_montgomery','  crypton_p256_clear(&tmp);','  crypton_p256_clear(&tmp);\n  '+snap('from-cleared-result','result.a',256//bits)+'\n  '+snap('from-cleared-tmp','tmp.a',256//bits))
 return s

def check_or_write(path,value,check):
 if check:assert path.read_text()==value,str(path)
 else:path.write_text(value)
if __name__=='__main__':
 check='--check' in sys.argv
 check_or_write(ROOT/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoP256FieldTables.kt',tables(),check)
 path=ROOT/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoP256Field.kt';s=path.read_text();marker='    // BEGIN PINNED EXPRESSIONS\n'
 prefix=s.split(marker)[0]
 check_or_write(path,prefix+marker+generated()+'}\n',check)
 for bits in [32,64]:check_or_write(ROOT/f'tools/p256-field-reference/ObservedField{bits}.h',observed(bits),check)
 print('Verified' if check else 'Extracted','pinned field constants, product expressions and reducer arithmetic')
