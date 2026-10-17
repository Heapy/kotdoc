#!/usr/bin/env python3
from pathlib import Path
import json,hashlib,sys,re
R=Path(__file__).resolve().parents[1];V=R/'vendor/crypton-2.0.1';D=R/'tools/chacha-drg-reference';m=json.loads((V/'SOURCE.json').read_text());assert m['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914';out={}
for p in ['Crypto/Random/ChaChaDRG.hs','Crypto/Random/Probabilistic.hs','Crypto/Random/Types.hs','Crypto/Random.hs','Crypto/Random/SystemDRG.hs','Crypto/Random/Entropy.hs','Crypto/Number/Serialize.hs','tests/Utils.hs','tests/NumberSpec.hs']:
 data=(V/p).read_bytes();assert hashlib.sha256(data).hexdigest()==m['files'][p];out['original/'+p]=data
s=out['original/Crypto/Random/Probabilistic.hs'].decode();old='module Crypto.Random.Probabilistic (\n    probabilisticFrom,\n) where';assert s.count(old)==1
out['ObservedProbabilistic.hs']=s.replace(old,'module ObservedProbabilistic (probabilisticFrom, secret) where').encode()
c=s.replace(old,'module ControlledProbabilistic (probabilisticWith) where').replace('probabilisticFrom','probabilisticWith');assert c.count('=> seed')==1;c=c.replace('=> seed','=> ScrubbedBytes\n    -> seed').replace('probabilisticWith material f =','probabilisticWith secret material f =');c=c[:c.index('-- | Drawn once,')];out['ControlledProbabilistic.hs']=c.encode()
random=out['original/Crypto/Random.hs'].decode();assert random.count('module Crypto.Random (')==1;out['InsecureRandom.hs']=random.replace('module Crypto.Random (','module InsecureRandom (').replace('{-# LANGUAGE GeneralizedNewtypeDeriving #-}','{-# LANGUAGE GeneralizedNewtypeDeriving #-}\n#define INSECURE_ENTROPY').encode()
matrix=dict(ChaChaDRG=dict(exports=['ChaChaDRG','initialize','initializeWords'],private=['generate'],instances=['DRG','NFData'],wordSerialization='5 Word64 pokeElemOff host endian',nonpositive='empty/original state without state demand'),Probabilistic=dict(exports=['probabilisticFrom'],private=['secret'],seed='take40 SHA512(secret32 ++ material)',secret='lazy process lifetime unsafeDoIO getRandomBytes32 NOINLINE'),RandomTypes=dict(classes=['MonadRandom','DRG'],type='MonadPseudoRandom',instances=['Functor','Applicative','Monad','MonadRandom'],functions=['withDRG'],remaining=['MonadRandom IO actual OS entropy']),RandomSeed=dict(type='Seed ByteArrayAccess',constant=40,private=['seedLength','conditional scrubbedHash512'],functions=['seedNew','seedToInteger','seedFromInteger','seedFromBinary','drgNew','drgNewSeed','drgNewTest','withRandomBytes'],profiles=['ordinary entropy40','INSECURE_ENTROPY entropy64 SHA512 take40'],remaining=['SystemDRG/getSystemDRG','OS Entropy backends']),originalTests=dict(dedicated=[] ,consumers=['tests/Utils.hs withTestDRG actual TestDRG generator','tests/NumberSpec.hs','tests/AFISSpec.hs','tests/PubKey/ElGamalSpec.hs','tests/PubKey/RabinSpec.hs'],notClaimedComplete=['all consumer suites until Kotlin executed']),remaining=['Prime9exports420primes','other signatures/trust/chain','full613/Pandoc623/calibre624-626'])
out['SourceMatrix.json']=(json.dumps(matrix,indent=2)+'\n').encode()
for name,data in out.items():
 p=D/name
 if '--check'in sys.argv:assert p.read_bytes()==data,name
 else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
print('PASS exact DRG/Probabilistic/Types/Seed sources and two mechanical observers')
