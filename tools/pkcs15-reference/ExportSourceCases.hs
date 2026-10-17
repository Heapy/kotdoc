{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified OriginalPkcs15Spec as S
import qualified Crypto.PubKey.RSA as R
import Crypto.PubKey.RSA.Prim(ep)
import Crypto.Number.Serialize(os2ip,i2osp,i2ospOf_)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Either
keyFields v=["size".=S.size v,"n".=show(S.n v),"e".=show(S.e v),"d".=show(S.d v),"p".=show(S.p v),"q".=show(S.q v),"dP".=show(S.dP v),"dQ".=show(S.dQ v),"qinv".=show(S.qinv v),"algorithm".=String "SHA1"]
view(Left e)=object["error".=show e]
view(Right b)=object["bytes".=B.unpack(b::B.ByteString)]
row label req expected=object["sourceCase".=label,"request".=object req,"expected".=expected]
signCases=[row("signature/"++show i)(keyFields v++["op".=String "sign","message".=B.unpack(S.msg v)])(view(S.sig v))|(i,v)<-zip[0::Int ..]S.vectorsSHA1]
verifyCases=[row("verify/"++show i)(keyFields v++["op".=String "verify","message".=B.unpack(S.msg v),"signature".=B.unpack s])(object["valid".=True])|(i,v)<-zip[0::Int ..](filter S.vectorHasSignature S.vectorsSHA1),let Right s=S.sig v]
malleabilityCases=concat[[row("malleability/"++show i++"/"++label)(keyFields v++["op".=String "verify","message".=B.unpack(S.msg v),"signature".=B.unpack sig])(object["valid".=expected])|(label,sig,expected)<-[("itself",s,True),("leading",B.cons 0 s,False),("trailing",B.snoc s 0,False),("plusN",i2osp(os2ip s+S.n v),False),("empty",B.empty,False)]]|(i,v)<-zip[0::Int ..](filter S.vectorHasSignature S.vectorsSHA1),let Right s=S.sig v]
block n payload=B.concat[B.pack[0,2],B.replicate n 255,B.singleton 0,payload]
unpadCases=[row("unpad/"++label)["op".=String "unpad","input".=B.unpack input](view expected)|(label,input,expected)<-[("shortest",block 8 "hello",Right "hello"),("longer",block 40 "hello",Right "hello"),("emptyMessage",block 8 "",Right ""),("oneMessage",block 8 "x",Right "x"),("first",B.cons 1(B.drop 1(block 8 "hello")),Left R.MessageNotRecognized),("second",B.concat[B.pack[0,1],B.drop 2(block 8 "hello")],Left R.MessageNotRecognized),("seven",block 7 "hello",Left R.MessageNotRecognized),("noPadding",block 0 "hello",Left R.MessageNotRecognized),("earlyZero",B.concat[B.pack[0,2,255,255,0],"hello"],Left R.MessageNotRecognized),("missingZero",B.concat[B.pack[0,2],B.replicate 40 255],Left R.MessageNotRecognized),("empty",B.empty,Left R.MessageNotRecognized),("one",B.singleton 0,Left R.MessageNotRecognized),("two",B.pack[0,2],Left R.MessageNotRecognized)]]
cipherCases=let v=head S.vectorsSHA1;k=S.size v;modulus=S.n v;(m,c)=head[(message,cipher)|i<-[1..200],let message=B.append "message "(B.replicate i 120),let encoded=B.concat[B.pack[0,2],B.replicate(k-3-B.length message)255,B.singleton 0,message],let cipher=ep(S.vectorToPublic v)encoded,os2ip cipher+modulus<2^(8*k)]in[row("cipherRange/"++label)(keyFields v++["op".=String "decrypt","cipher".=B.unpack cipher])(view expected)|(label,cipher,expected)<-[("itself",c,Right m),("plusN",i2ospOf_ k(os2ip c+modulus),Left R.MessageSizeIncorrect),("equalN",i2ospOf_ k modulus,Left R.MessageSizeIncorrect)]]
main=L.putStrLn(encode(signCases++verifyCases++malleabilityCases++unpadCases++cipherCases))
