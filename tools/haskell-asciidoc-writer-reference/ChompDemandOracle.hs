{-# LANGUAGE OverloadedStrings #-}
-- Original DocLayout demand controls; John MacFarlane, BSD-3-Clause.
import Text.DocLayout
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as B
import Data.Aeson
import Control.Exception
cases :: [(T.Text,T.Text,Doc T.Text)]
cases=[("root","whnf",error"doc-root")
 ,("concat-left-before-right","whnf",Concat(error"left-root")(error"right-root"))
 ,("concat-right","whnf",Concat(literal"x")(error"right-root"))
 ,("concat-middle-before-right","whnf",Concat(Concat(literal"x")(error"middle-root"))(error"right-root"))
 ,("prefix-child-whnf","whnf",Prefixed"p"(error"child-root"))
 ,("prefix-child-render","render",Prefixed"p"(error"child-root"))
 ,("prefix-text-whnf","whnf",Prefixed(error"prefix-text")(literal"x"))
 ,("prefix-text-render","render",Prefixed(error"prefix-text")(literal"x"))]
main=mapM_ observe cases
observe(name,mode,doc)=do
 result<-try(if mode=="whnf" then evaluate(chomp doc)>>pure"root-returned" else evaluate(T.length(render Nothing(chomp doc)))>>pure"render-returned")::IO(Either SomeException T.Text)
 B.putStrLn$encode$case result of{Left e->object["name".=name,"mode".=mode,"thrown".=takeWhile(/='\n')(displayException e)];Right x->object["name".=name,"mode".=mode,"result".=x]}
