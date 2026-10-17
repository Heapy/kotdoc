{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified OriginalPssSpec as S
import qualified Crypto.PubKey.RSA as R
import Crypto.Number.Serialize(os2ip,i2ospOf_)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
fields k=["size".=R.public_size(R.private_pub k),"n".=show(R.public_n(R.private_pub k)),"e".=show(R.public_e(R.private_pub k)),"d".=show(R.private_d k),"p".=show(R.private_p k),"q".=show(R.private_q k),"dP".=show(R.private_dP k),"dQ".=show(R.private_dQ k),"qinv".=show(R.private_qinv k),"algorithm".=String "SHA1"]
row label req expected=object["sourceCase".=label,"request".=object req,"expected".=expected]
groups=[("internal",S.rsaKeyInt,[S.vectorInt]),("1024",S.rsaKey1,S.vectorsKey1),("1025",S.rsaKey2,S.vectorsKey2),("1026",S.rsaKey3,S.vectorsKey3),("1031",S.rsaKey8,S.vectorsKey8)]
cases=concat[[row(label++"/sign/"++show i)(fields key++["op".=String "signWithSalt","salt".=B.unpack(S.salt v),"message".=B.unpack(S.message v)])(object["bytes".=B.unpack(S.signature v)]),row(label++"/verify/"++show i)(fields key++["op".=String "verify","message".=B.unpack(S.message v),"signature".=B.unpack(S.signature v)])(object["valid".=True])]|(label,key,vs)<-groups,(i,v)<-zip[0::Int ..]vs]
rangeCases=let key=S.rsaKey1;k=R.public_size(R.private_pub key);n=R.public_n(R.private_pub key);(v,s)=head[(v,S.signature v)|v<-S.vectorsKey1,os2ip(S.signature v)+n<2^(8*k)]in[row("range/"++name)(fields key++["op".=String "verify","message".=B.unpack(S.message v),"signature".=B.unpack sig])(object["valid".=expected])|(name,sig,expected)<-[("itself",s,True),("plusN",i2ospOf_ k(os2ip s+n),False),("equalN",i2ospOf_ k n,False)]]
main=L.putStrLn(encode(cases++rangeCases))
