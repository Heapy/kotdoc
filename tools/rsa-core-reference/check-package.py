#!/usr/bin/env python3
from pathlib import Path
from zipfile import ZipFile
R=Path(__file__).resolve().parents[2]
with ZipFile(R/'core/build/libs/core-0.1.0-SNAPSHOT.jar')as z:
 names=z.namelist();classes=[n for n in names if n.startswith('io/heapy/kotdoc/crypto/CryptoRsa')and n.endswith('.class')]
 assert 'io/heapy/kotdoc/crypto/CryptoRsa.class'in classes
 assert 'io/heapy/kotdoc/crypto/CryptoRsa$Blinder.class'in classes
 notices=[n for n in names if n.endswith('crypton-BSD-3-Clause.txt')];assert len(notices)==1,notices
 assert z.read(notices[0])==(R/'licenses/crypton-BSD-3-Clause.txt').read_bytes()
 for source in (R/'licenses').iterdir():
  if source.is_file():
   matches=[n for n in names if n.endswith('/'+source.name)]
   assert len(matches)==1,(source,matches)
   assert z.read(matches[0])==source.read_bytes(),source
 assert not any('rsa-core-reference' in n or n.endswith('.hs')for n in names)
 print('PASS RSA',len(classes),'classes, exact crypton notice, no oracle runtime packaged')
