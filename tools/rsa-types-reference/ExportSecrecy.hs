{-# LANGUAGE OverloadedStrings,GADTs #-}
module Main where
import qualified OriginalSecrecySpec as S
import qualified Crypto.PubKey.RSA.Types as R
import Crypto.Debug(debugShow)
import Data.List(isInfixOf)
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
main=L.putStrLn$encode$object["public".=object["size".=R.public_size S.rsaPub,"n".=show(R.public_n S.rsaPub),"e".=show(R.public_e S.rsaPub)],"secrets".=map show[S.d1,S.d2,S.d3,S.d4,S.d5,S.d6],"rows".=concatMap rows[False,True]]
 where rows pair=let name=if pair then"KeyPair"else"PrivateKey";shown=if pair then show(R.KeyPair S.rsaPriv)else show S.rsaPriv;revealed=if pair then debugShow(R.KeyPair S.rsaPriv)else debugShow S.rsaPriv;ss=[S.d1,S.d2,S.d3,S.d4,S.d5,S.d6];roundTrip=if pair then(read revealed::R.KeyPair)==R.KeyPair S.rsaPriv else(read revealed::R.PrivateKey)==S.rsaPriv in [object["type".=name,"assertion".=("showHides"::String),"expected".=map show[x|x<-ss,show x`isInfixOf`shown]],object["type".=name,"assertion".=("debugReveals"::String),"expected".=map show[x|x<-ss,not(show x`isInfixOf`revealed)]],object["type".=name,"assertion".=("readDebug"::String),"expected".=roundTrip]]
