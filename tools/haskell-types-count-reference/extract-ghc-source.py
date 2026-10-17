#!/usr/bin/env python3
"""Extract the visible original source from immutable installed GHC Haddock HTML."""
from html.parser import HTMLParser
from pathlib import Path
class Source(HTMLParser):
 def __init__(self):super().__init__(convert_charrefs=True);self.stack=[];self.out=[]
 def handle_starttag(self,tag,attrs):self.stack.append((tag,dict(attrs)))
 def handle_endtag(self,tag):
  for i in range(len(self.stack)-1,-1,-1):
   if self.stack[i][0]==tag:self.stack=self.stack[:i];break
 def handle_data(self,data):
  if any(t=='pre'for t,a in self.stack)and not any('annottext'in a.get('class','').split()for t,a in self.stack):self.out.append(data)
def source(data):parser=Source();parser.feed(data);return ''.join(parser.out)
if __name__=='__main__':
 root=Path(__file__).resolve().parent
 for module in ['Num','Enum']:
  actual=source((root/f'OriginalGhc{module}.html').read_text());assert actual==(root/f'OriginalGhc{module}.hs').read_text()
 print('Both original GHC visible source extractions byte-exact PASS')
