{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
import qualified Text.Pandoc.Builder as B
import qualified Text.ParserCombinators.ReadPrec as P
import Text.Read(Read(..))
import Control.Exception
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Char(ord)
import Data.List(nub,inits)
import System.IO

data NoElem=NoElem deriving Show
instance Read NoElem where readPrec=P.pfail
baseLexemes=["","1","0","0x","0Xf","0o7","0O8","0b1","0B2","0x1G","1.","1.0","1.e","1e","1E+","1e-2","1e+Z","99999999999999999999999999999","-1","+1","..","::","=","->","@","~","abc","_x'","Many","ManyX","éclair","中","١",";","[","]","{","}","(",")","\"","\"a","\"a\"","\"\\","\"\\&","\"\\&\"","\"\\ ","\"\\  \\\"","\"\\x","\"\\X41\"","\"\\o77\"","\"\\1114112\"","\"\\1114111\"","\"\\SO","\"\\SOH\"","\"\\NUL\"","\"\\^A\"","\"\\^?\"","'","''","'a","'a'","'\\","'\\&'","'\\x41'","'\\X41'","'\\SO'","'\\SOH'","'\\^_'","'\\1114112'","\"a\n\"","--comment"," {-x-}","\t1"]
asciiNames=["SOH","SO","NUL","STX","ETX","EOT","ENQ","ACK","BEL","BS","HT","LF","VT","FF","CR","SI","DLE","DC1","DC2","DC3","DC4","NAK","SYN","ETB","CAN","EM","SUB","ESC","FS","GS","RS","US","SP","DEL"]
quoted e=["\"\\"++e++"\"", "'\\"++e++"'"]
escapeLexemes=concatMap quoted(asciiNames++map(:[])"abfnrtv\\\"'"++map(\c->['^',c])(['@'..'_']++"?`"))
lexemes=baseLexemes++escapeLexemes++["\""++replicate 4096 'a'++"\"",replicate 4096 '1',"0x"++replicate 4096 'f']
contexts=["","Many ","Many {unMany = fromList "]
tails=["empty","bottom","bad-head","bad-next"]
input s t=case t of
 "empty"->s
 "bottom"->s++error "lex-tail"
 "bad-head"->s++[error "lex-head"]
 "bad-next"->s++('!':error "lex-next")
 "infinite"->s++repeat '!'
 _->error "tail mode"
observe name prefix mode=do
 r<-try(evaluate(null(P.readPrec_to_S(readPrec::P.ReadPrec(B.Many NoElem))0(input prefix mode))))
 L.putStrLn(encode(object(["name" .= name,"prefix" .= map ord prefix,"tail" .= mode]++case r of Right v->["result" .= show v];Left(e::SomeException)->["exception" .= takeWhile(/='\n')(displayException e)])))
main=do
 hSetBuffering stdout LineBuffering
 sequence_[observe(show i++"/"++show j++"/"++t)(c++s)t|(i,c)<-zip[0::Int ..]contexts,(j,s)<-zip[0::Int ..]lexemes,t<-tails]
 sequence_[observe("infinite/"++show i)("Many "++s)"infinite"|(i,s)<-zip[0::Int ..]["1","\"a\"","'a'",";","]"]]

 sequence_[observe("escape-prefix/"++show i++"/"++show j++"/"++t)(c++s)t|(i,c)<-zip[0::Int ..]contexts,(j,s)<-zip[0::Int ..](nub(concatMap(init.inits)escapeLexemes)++["\"\\1114111X","\"\\1114112X","\"\\x10ffffX","\"\\x110000X","\"\\o4177777X","\"\\o4200000X","\"\\SOX","\"\\SOHX"]),t<-tails]
