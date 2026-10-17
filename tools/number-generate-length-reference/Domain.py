def domain(r):
 op=r['op'];bits=r.get('bits',0);n=len(r['chunks'][0]);access=[]
 if op=='params'and bits>0:
  if r.get('top')=='bottom':return dict(defined=True,access=[],stops='top-bottom',actual=n)
  bit=(bits-1)%8
  if r.get('top')=='highest':access.append(0)
  if r.get('top')=='two':access +=[0,1]if bit==0 else[0]
  access.append(0)
  if r.get('oddBottom'):return dict(defined=all(i<n for i in access),access=access,stops='odd-bottom',actual=n)
  if r.get('odd'):access.append((bits+7)//8-1)
 return dict(defined=all(0<=i<n for i in access),access=access,actual=n)
