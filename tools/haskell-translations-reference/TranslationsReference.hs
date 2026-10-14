{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Transport only: compile the unchanged pinned upstream module.
module Main where
import Data.Aeson
import qualified Data.Aeson.Key as K
import qualified Data.Aeson.KeyMap as KM
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.Vector as V
import qualified Data.Yaml as Y
import Data.Char (chr,ord)
import Data.Int (Int64)
import Data.Semigroup (stimes, sconcat)
import Data.List.NonEmpty (NonEmpty(..))
import Control.Exception (try, SomeException, evaluate, displayException)
import System.IO (stdout,hFlush,isEOF)
import Text.Pandoc.Translations.Types
as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr (Array v) = V.toList v
arr _ = error "array"
chars v = map (chr . as) (arr v)
txt = T.pack . chars
terms = [minBound..maxBound] :: [Term]
-- Tagged transport preserves Text.pack replacement of each Haskell surrogate Char.
value v = case arr v of
 [String "s",s] -> String (txt s)
 [String "z"] -> Null
 [String "b",b] -> Bool (as b)
 [String "n",n] -> n
 [String "a",xs] -> toJSON (map value (arr xs))
 [String "o",xs] -> Object (KM.fromList [(K.fromText (txt k),value x) | pair <- arr xs,let [k,x]=arr pair])
 _ -> error "value"
table p t = object ["shown" .= T.pack (showsPrec p t ""),
 "terms" .= [toJSON [toJSON (show k),toJSON (lookupTerm k t)] | k <- terms]]
parsed p v = case fromJSON v :: Result Translations of
 Success t -> object ["success" .= table p t]
 Error e -> object ["error" .= e]
response x = case arr x of
 [String "termJSON",v] -> pure (case fromJSON (value v) :: Result Term of Success z -> object ["success" .= show z]; Error e -> object ["error" .= e])
 [String "translations",v,p] -> pure (parsed (as p) (value v))
 [String "yaml",s,p] -> case Y.decodeEither' (TE.encodeUtf8 (txt s)) :: Either Y.ParseException Value of
   Right v -> pure (object ["value" .= v,"parsed" .= parsed (as p) v])
   Left e -> error (Y.prettyPrintParseException e)
 [String "read",p,s] -> pure (toJSON [[toJSON (show v),toJSON (map ord rest)] | (v,rest) <- readsPrec (as p) (chars s) :: [(Term,String)]])
 [String "readList",s] -> pure (toJSON [[toJSON (map show vs),toJSON (map ord rest)] | (vs,rest) <- readList (chars s) :: [([Term],String)]])
 [String "relation",i,j,p] -> let a=toEnum(as i)::Term;b=toEnum(as j)::Term in pure (object [
   "eq" .= (a==b),"ne" .= (a/=b),"lt" .= (a<b),"le" .= (a<=b),"gt" .= (a>b),"ge" .= (a>=b),
   "compare" .= show (compare a b),"min" .= show (min a b),"max" .= show (max a b),
   "shown" .= showsPrec (as p) a "", "ordinal" .= fromEnum a])
 [String "combine",String op,vs,p,n] -> do
   let ts=map (as . value) (arr vs) :: [Translations]
       t=case op of
          "append" -> head ts <> ts!!1
          "concat" -> mconcat ts
          "sconcat" -> case ts of a:bs->sconcat(a:|bs);_->error "empty"
          "stimes" -> stimes (read (T.unpack (as n))::Integer) (head ts)
          _ -> error "combine"
       result=table (as p) t
   caught result
 [String "enum",String op,i,j,k,n] -> do
   let a=toEnum(as i)::Term;b=toEnum(as j)::Term;c=toEnum(as k)::Term
       vs=case op of "toEnum"->[a];"succ"->[succ a];"pred"->[pred a];"from"->enumFrom a;"fromTo"->enumFromTo a c;"fromThen"->enumFromThen a b;"fromThenTo"->enumFromThenTo a b c;"bounds"->[minBound,maxBound];_->error "enum"
   caught (object ["values" .= map show (take (as n) vs)])
 _ -> error "operation"
caught result = do
 e<-try(evaluate(BL.length(encode result)))::IO(Either SomeException Int64)
 pure(case e of Right _ -> result; Left err -> object ["error" .= takeWhile(/='\n')(displayException err)])
main=loop where
 loop=do
  end<-isEOF
  if end then pure() else do
   line<-BS.getLine
   let v=case eitherDecode(BL.fromStrict line) of Right a->a;Left e->error e
   result<-response v;C.putStrLn(encode result);hFlush stdout;loop
