{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Finite deep source traversal observations, not generated expectations.
import qualified Text.Pandoc.Generic as G
import Text.Pandoc.Definition
import Control.Monad.State.Strict
import Data.Monoid
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
import System.IO
observeInline depth=let { x=iterate (Emph . pure) (Str "a") !! depth; change(c::Char)=if c=='a' then 'b' else c; (result,count)=runState(G.bottomUpM(\(c::Char)->modify(+1)>>pure(change c))x)(0::Int); text=G.queryWith(\(c::Char)->[c]) }
 in object["queryChars" .= getSum(G.queryWith(\(_::Char)->Sum(1::Int))x),"queryNodes" .= getSum(G.queryWith(\(_::Inline)->Sum(1::Int))x),"bottomUp" .= text(G.bottomUp change x),"topDown" .= text(G.topDown change x),"bottomUpM" .= text result,"effects" .= count]
observe depth=let x=replicate depth 'a'; (result,count)=runState(G.bottomUpM(\(c::Char)->modify(+1)>>pure(if c=='a' then 'b' else c))x)(0::Int)
 in object["queryChars" .= getSum(G.queryWith(\(_::Char)->Sum(1::Int))x),"queryTails" .= getSum(G.queryWith(\(_::String)->Sum(1::Int))x),"bottomUp" .= G.bottomUp(\(c::Char)->if c=='a' then 'b' else c)x,"topDown" .= G.topDown(\(c::Char)->if c=='a' then 'b' else c)x,"bottomUpM" .= result,"effects" .= count]
respond line=do
 let Object r=either error id(eitherDecode line);Just v=K.lookup "depth" r;Success depth=fromJSON v
 result<-try(evaluate(let bytes=encode(if K.lookup "shape" r==Just(String "inline") then observeInline depth else observe depth)in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
