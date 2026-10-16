#!/usr/bin/env python3
"""Extract source declarations/field types; never derive expected outputs."""
from pathlib import Path
import re,json,hashlib
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def split_top(text,separator=','):
    result=[];start=0;depth=0
    for i,c in enumerate(text):
        if c in '([{':depth+=1
        elif c in ')]}':depth-=1
        elif c==separator and depth==0:result.append(text[start:i].strip());start=i+1
    result.append(text[start:].strip());return result
class Types:
    def __init__(self,text):self.tokens=re.findall(r'[A-Za-z_][A-Za-z_0-9.\']*|[][(),]',text);self.at=0
    def peek(self):return self.tokens[self.at]if self.at<len(self.tokens)else None
    def atom(self):
        t=self.peek();self.at+=1
        if t=='[':
            item=self.expression();assert self.peek()==']';self.at+=1;return {'list':item}
        if t=='(':
            fields=[self.expression()]
            while self.peek()==',':self.at+=1;fields.append(self.expression())
            assert self.peek()==')';self.at+=1
            return fields[0]if len(fields)==1 else {'tuple':fields}
        assert t not in {None,']',')',','},t
        return {'name':t}
    def expression(self):
        head=self.atom();args=[]
        while self.peek()not in {None,']',')',','}:args.append(self.atom())
        return {'apply':head['name'],'args':args}if args else head
    def fields(self):
        result=[]
        while self.peek()!=None:result.append(self.atom())
        return result

def main():
    p=ROOT/'upstream/pandoc-types/src/Text/Pandoc/Definition.hs';source=p.read_text();cleaned=re.sub(r'--[^\n]*',lambda m:' '*len(m.group()),source);types=[]
    for m in re.finditer(r'(?ms)^(data|newtype)\s+(\w+)\s*=\s*(.*?)\bderiving\b',cleaned):
        kind,name,body=m.groups();body=re.sub(r'--[^\n]*','',body);constructors=[]
        for constructor in split_top(body,'|'):
            name_match=re.match(r'(\w+)\s*(.*)',constructor,re.S);ctor,fields=name_match.groups();labels=[]
            if fields.strip().startswith('{'):
                parsed=[]
                for field in split_top(fields.strip()[1:-1]):
                    label,typ=field.split('::');labels.append(label.strip());parsed.append(Types(typ).expression())
            else:parsed=Types(fields).fields()
            constructors.append(dict(name=ctor,fields=parsed,labels=labels))
        types.append(dict(name=name,newtype=kind=='newtype',line=source[:m.start()].count('\n')+1,constructors=constructors))
    aliases=[dict(name=m.group(1),type=Types(m.group(2)).expression(),line=source[:m.start()].count('\n')+1)for m in re.finditer(r'^type (\w+)\s*=([^\n]+)',source,re.M)]
    output=dict(source=str(p.relative_to(ROOT)),sha256=hashlib.sha256(p.read_bytes()).hexdigest(),types=types,aliases=aliases)
    assert len(types)==23 and len(aliases)==5
    assert sum(len(t['constructors'])for t in types)==77
    (HERE/'definition-schema.json').write_text(json.dumps(output,indent=2)+'\n')
    print(len(types),'source types,',sum(len(t['constructors'])for t in types),'constructors,',len(aliases),'aliases')
if __name__=='__main__':main()
