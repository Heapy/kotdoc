{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified OriginalTests as Original
import qualified CapturedTests as Captured
import qualified Network.URI as N
import UriWire
import Data.Aeson
import Data.Char(chr,ord)
import qualified Data.Set as S
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString.Lazy as B
import qualified Control.Exception as E
import Data.Binary.Put
import Crypto.Hash(SHA256,Digest,hashlazy)
import System.IO
import System.Environment(getArgs,withArgs)
respond v=case arr v of
 [String "parse",k,s]->maybeUri((case as k::Int of 0->N.parseURI;1->N.parseURIReference;2->N.parseRelativeReference;3->N.parseAbsoluteURI)(raw s))
 [String "valid",k,s]->toJSON((case as k::Int of 0->N.isURI;1->N.isURIReference;2->N.isRelativeReference;3->N.isAbsoluteURI;4->N.isIPv4address;5->N.isIPv6address)(raw s))
 [String "absolute",u]->toJSON(N.uriIsAbsolute(uri u))
 [String "relative",u]->toJSON(N.uriIsRelative(uri u))
 [String "resolve",k,a,b]->uv((case as k::Int of 0->N.relativeTo;1->N.nonStrictRelativeTo;2->N.relativeFrom)(uri a)(uri b))
 [String "render",u,m,s]->rv(N.uriToString(const(if m==Null then error "unexpected callback" else raw m))(uri u)(raw s))
 [String "render-auth",u,m,s]->rv(N.uriAuthToString(const(if m==Null then error "unexpected callback" else raw m))(if u==Null then Nothing else Just(auth u))(raw s))
 [String "escape",keep,s]->rv(N.escapeURIString(`S.member` S.fromList(map(chr.as)(arr keep)))(raw s))
 [String "unescape",s]->rv(N.unEscapeString(raw s))
 [String "normalize",k,s]->rv((case as k::Int of 0->N.normalizeCase;1->N.normalizeEscape;2->N.normalizePathSegments)(raw s))
 [String "segments",u]->toJSON(map rv(N.pathSegments(uri u)))
 [String "rectify",u]->uv(N.rectify(uri u))
 [String "rectify-auth",a]->av(N.rectifyAuth(auth a))
 [String "components",u]->let x=uri u in toJSON(map rv[N.scheme x,N.authority x,N.path x,N.query x,N.fragment x])
 [String "char",n]->let c=chr(as n) in toJSON[N.isReserved c,N.isUnreserved c,N.isAllowedInURI c,N.isUnescapedInURI c,N.isUnescapedInURIComponent c]
 [String "unicode",first,count]->let
  outputs=[encode[rv(N.escapeURIChar(const False)c),rv(N.unEscapeString(N.escapeURIChar(const False)c)),rv(N.normalizeCase['x',':','%',c,c]),toJSON[N.isReserved c,N.isUnreserved c,N.isAllowedInURI c,N.isUnescapedInURI c,N.isUnescapedInURIComponent c]]|n<-[as first..as first+as count-1],let c=chr n]
  bytes=runPut(mapM_(\b->putWord32be(fromIntegral(B.length b)) >> putLazyByteString b)outputs)
  in toJSON(show(hashlazy bytes::Digest SHA256))
 _->error "request"
main=do
 args<-getArgs
 case args of
  ["--upstream-tests"]->withArgs["--num-threads=1","--quickcheck-replay=314159"]Original.main
  ["--capture-tests"]->withArgs["--num-threads=1","--quickcheck-replay=314159"]Captured.main
  _->loop
 where
 loop=do
  end<-isEOF
  if end then pure() else do
   line<-L.pack <$> getLine
   let value=case eitherDecode line of Left e->error e;Right v->respond v
   bytes<-E.evaluate(let b=encode value in B.length b `seq` b)
   L.putStrLn bytes;hFlush stdout;loop
