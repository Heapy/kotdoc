{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,FlexibleContexts,FlexibleInstances,TypeOperators,UndecidableInstances,RankNTypes #-}
module Main where
import qualified Crypto.PubKey.RSA.Types as R
import Crypto.Debug (debugShow)
import Data.Data
import Data.Aeson
import Data.Aeson.Types
import qualified Data.ByteString.Lazy.Char8 as L
import Control.Exception
import Control.DeepSeq
import GHC.Generics
import Data.Proxy
import Data.String(fromString)
import System.IO
import Text.Read(readPrec,readListPrec,readPrec_to_S)

field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
number o k d=maybe d id(field o k)::Int
bad o k x=if k `elem` (maybe [] id(field o "bad")::[String])then errorWithoutStackTrace(text o "errorPrefix"""++k) else x
integer o k d=bad o k(read(text o (fromString k)(show d))::Integer)
toEnumError o=bad o "errorRoot"$[R.MessageSizeIncorrect,R.MessageTooLong,R.MessageNotRecognized,R.SignatureTooLong,R.InvalidParameters]!!number o "error"0
pub o=bad o "publicRoot"$R.PublicKey(bad o "size"(number o "size"1))(integer o "n"(-2))(integer o "e"3)
priv o=bad o "privateRoot"$R.PrivateKey(bad o "pub"(pub o))(integer o "d"4)(integer o "p"5)(integer o "q"6)(integer o "dP"7)(integer o "dQ"8)(integer o "qinv"9)
pair o=bad o "pairRoot"$R.KeyPair(priv o)
typeInfo :: Typeable a=>a->Value
typeInfo a=let t=typeRepTyCon(typeOf a)in object["module".=tyConModule t,"name".=tyConName t,"package".=tyConPackage t]
metadata :: Data a=>a->Value
metadata a=object["datatype".=dataTypeName(dataTypeOf a),"constructor".=showConstr(toConstr a),"fields".=constrFields(toConstr a),"index".=constrIndex(toConstr a),"children".=gmapQ(\x->show(typeOf x))a]
class Describe f where describe::f p->Value
instance (Datatype d,Describe f)=>Describe(M1 D d f)where describe m@(M1 x)=object["datatype".=datatypeName m,"module".=moduleName m,"package".=packageName m,"newtype".=isNewtype m,"child".=describe x]
instance (Constructor c,Describe f)=>Describe(M1 C c f)where describe m@(M1 x)=object["constructor".=conName m,"record".=conIsRecord m,"fixity".=show(conFixity m),"child".=describe x]
instance (Selector s,Describe f)=>Describe(M1 S s f)where describe m@(M1 x)=object["selector".=selName m,"unpackedness".=show(selSourceUnpackedness m),"strictness".=show(selSourceStrictness m),"decided".=show(selDecidedStrictness m),"child".=describe x]
instance (Describe f,Describe g)=>Describe(f:*:g)where describe(x:*:y)=object["product".=[describe x,describe y]]
instance Typeable c=>Describe(K1 i c)where describe _=object["fieldType".=show(typeRep(Proxy::Proxy c))]
class Edit f where edit::[String]->String->f p->f p
instance Edit f=>Edit(M1 i c f)where edit es path (M1 x)=M1(edit es path x)
instance(Edit f,Edit g)=>Edit(f:*:g)where edit es path value=if path `elem` es then errorWithoutStackTrace("rep:"++path)else let ~(x:*:y)=value in edit es(path++"L")x:*:edit es(path++"R")y
instance Edit(K1 i c)where edit es path value=if ("field:"++path)`elem`es then errorWithoutStackTrace("rep:field:"++path)else value
readRows :: forall a.Read a=>String->(a->String)->Int->String->Value
readRows op showValue p s=if op=="read"||op=="readPrec"then toJSON[(showValue a,r)|(a,r)<-(if op=="read"then readsPrec p s else readPrec_to_S readPrec p s)]else toJSON[([showValue a|a<-as],r)|(as,r)<-(if op=="readList"then readList s else readPrec_to_S readListPrec p s)]
forceValue x=evaluate x>>return(toJSON(42::Int))
observe o=let t=text o "type""PublicKey";op=text o "op""show";p=bad o "precedence"(number o "precedence"0);s=bad o "inputRoot"(text o "input"""++bad o "inputTail""") in case op of
 "eq"->return$toJSON$let other=maybe mempty id(field o "right"::Maybe Object) in case t of "PublicKey"->pub o==pub other;"PrivateKey"->priv o==priv other;"Blinder"->R.Blinder(integer o "r"2)(integer o "inverse"3)==R.Blinder(integer other "r"2)(integer other "inverse"3);"Error"->(toEnumError o)==(toEnumError other);_->pair o==pair other
 "ne"->return$toJSON$let other=maybe mempty id(field o "right"::Maybe Object) in case t of "PublicKey"->pub o/=pub other;"PrivateKey"->priv o/=priv other;"Blinder"->R.Blinder(integer o "r"2)(integer o "inverse"3)/=R.Blinder(integer other "r"2)(integer other "inverse"3);"Error"->(toEnumError o)/=(toEnumError other);_->pair o/=pair other
 "show"->return$toJSON$case t of "PublicKey"->showsPrec p(pub o)"";"PrivateKey"->showsPrec p(priv o)"";"KeyPair"->showsPrec p(pair o)"";"Blinder"->show(R.Blinder(integer o "r"2)(integer o "inverse"3));_->show([R.MessageSizeIncorrect,R.MessageTooLong,R.MessageNotRecognized,R.SignatureTooLong,R.InvalidParameters]!!number o "error"0)
 "debug"->return$toJSON$if t=="KeyPair"then debugShow(pair o)else debugShow(priv o)
 "readProjected"->return$case t of "PublicKey"->projectRead o(show::R.PublicKey->String)p s;"PrivateKey"->projectRead o(debugShow::R.PrivateKey->String)p s;_->projectRead o(debugShow::R.KeyPair->String)p s
 "read"->return$readsFor op t p s
 "readPrec"->return$readsFor op t p s
 "readList"->return$readsFor op t p s
 "readListPrec"->return$readsFor op t p s
 "showPrefix"->return$toJSON$take(number o "prefixLength"0)$case t of "PublicKey"->showsPrec p(pub o)(bad o "suffix""TAIL");"PrivateKey"->showsPrec p(priv o)(bad o "suffix""TAIL");"Blinder"->showsPrec p(R.Blinder(integer o "r"2)(integer o "inverse"3))(bad o "suffix""TAIL");"Error"->showsPrec p(toEnumError o)(bad o "suffix""TAIL");_->showsPrec p(pair o)(bad o "suffix""TAIL")
 "showListPrefix"->return$toJSON$take(number o "prefixLength"0)$case t of "PublicKey"->showList(pub o:bad o "listTail"[])(bad o "suffix""TAIL");"PrivateKey"->showList(priv o:bad o "listTail"[])(bad o "suffix""TAIL");_->showList(pair o:bad o "listTail"[])(bad o "suffix""TAIL")
 "debugPrefix"->return$toJSON$take(number o "prefixLength"0)$if t=="KeyPair"then debugShow(pair o)else debugShow(priv o)
 "typeInfo"->return$case t of "PublicKey"->typeInfo(pub o);"PrivateKey"->typeInfo(priv o);"KeyPair"->typeInfo(pair o);"Integer"->typeInfo(undefined::Integer);_->typeInfo(undefined::Int)
 "metadata"->return$case t of "PublicKey"->metadata(pub o);"PrivateKey"->metadata(priv o);_->metadata(pair o)
 "datatype"->return$toJSON$case t of "PublicKey"->dataTypeName(dataTypeOf(pub o));"PrivateKey"->dataTypeName(dataTypeOf(priv o));_->dataTypeName(dataTypeOf(pair o))
 "constructor"->return$toJSON$case t of "PublicKey"->showConstr(toConstr(pub o));"PrivateKey"->showConstr(toConstr(priv o));_->showConstr(toConstr(pair o))
 "genericToRoot"->if t=="PublicKey"then forceValue(to(edit(maybe[]id(field o "repErrors"))""(from(pub o)))::R.PublicKey)else forceValue(to(edit(maybe[]id(field o "repErrors"))""(from(priv o)))::R.PrivateKey)
 "genericToShow"->return$toJSON$if t=="PublicKey"then show(to(edit(maybe[]id(field o "repErrors"))""(from(pub o)))::R.PublicKey)else show(to(edit(maybe[]id(field o "repErrors"))""(from(priv o)))::R.PrivateKey)
 "genericToDebug"->return$toJSON$debugShow(to(edit(maybe[]id(field o "repErrors"))""(from(priv o)))::R.PrivateKey)
 "generic"->return$if t=="PublicKey"then describe(from(pub o))else describe(from(priv o))
 "root"->case t of "PublicKey"->forceValue(pub o);"PrivateKey"->forceValue(priv o);"KeyPair"->forceValue(pair o);_->forceValue(R.Blinder(integer o "r"2)(integer o "inverse"3))
 "nfdata"->forceValue$case t of "PublicKey"->rnf(pub o);"PrivateKey"->rnf(priv o);_->rnf(pair o)
 "selector"->return$toJSON$case text o "selector""n"of "size"->toInteger(R.private_size(priv o));"e"->R.private_e(priv o);"pairN"->R.public_n(R.toPublicKey(pair o));"pairSize"->toInteger(R.public_size(R.toPublicKey(pair o)));"pairE"->R.public_e(R.toPublicKey(pair o));"d"->R.private_d(R.toPrivateKey(pair o));_->R.private_n(priv o)
 _->errorWithoutStackTrace"unknown-operation"
projectRead :: forall a.Read a=>Object->(a->String)->Int->String->Value
projectRead o render p s=let values=readsPrec p s::[(a,String)] in case text o "projection""alternative"of
 "ignore"->toJSON(42::Int)
 "value"->toJSON(render(fst(head values)))
 "remainder"->toJSON(take(number o "prefixLength"0)(snd(head values)))
 _->toJSON(not(null values))

readsFor op t p s=case t of "PublicKey"->readRows op(show::R.PublicKey->String)p s;"PrivateKey"->readRows op(debugShow::R.PrivateKey->String)p s;_->readRows op(debugShow::R.KeyPair->String)p s
run o=do r<-try(observe o>>=evaluate.force)::IO(Either SomeException Value);return$either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id r
main=do hSetBuffering stdout LineBuffering;contents<-L.getContents;mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode)(L.lines contents)
