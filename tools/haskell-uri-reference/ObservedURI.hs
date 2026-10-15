{-# LANGUAGE OverloadedStrings #-}
-- Observe real original functions, retaining all original types and expectations.
module ObservedURI(module U,parseURI,parseURIReference,parseRelativeReference,parseAbsoluteURI,isURI,isURIReference,isRelativeReference,isAbsoluteURI,uriIsAbsolute,uriIsRelative,relativeTo,nonStrictRelativeTo,relativeFrom,uriToString,uriAuthToString,escapeURIString,unEscapeString,normalizeCase,normalizeEscape,normalizePathSegments,pathSegments,rectify,rectifyAuth) where
import Network.URI as U hiding(parseURI,parseURIReference,parseRelativeReference,parseAbsoluteURI,isURI,isURIReference,isRelativeReference,isAbsoluteURI,uriIsAbsolute,uriIsRelative,relativeTo,nonStrictRelativeTo,relativeFrom,uriToString,uriAuthToString,escapeURIString,unEscapeString,normalizeCase,normalizeEscape,normalizePathSegments,pathSegments,rectify,rectifyAuth)
import qualified Network.URI as N
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Control.Exception as E
import qualified Data.Set as S
import System.Environment(lookupEnv)
import System.IO.Unsafe(unsafePerformIO)
import Control.Concurrent.MVar
import Data.Char(ord)
import UriWire
{-# NOINLINE lock #-}
lock=unsafePerformIO(newMVar ())
{-# NOINLINE capture #-}
capture req encoder result=unsafePerformIO $ do
 path<-lookupEnv "KOTDOC_URI_CAPTURE"
 case path of
  Nothing->pure()
  Just file->do
   let line=encode(object["request" .= req,"expected" .= encoder result])<>"\n"
   -- Nested original function arguments may themselves emit observations.
   -- Complete encoding before acquiring the serialization lock.
   _<-E.evaluate(B.length line)
   withMVar lock(\_->B.appendFile file line)
 pure result
parseURI s=capture(request "parse"[toJSON(0::Int),rv s])maybeUri(N.parseURI s)
parseURIReference s=capture(request "parse"[toJSON(1::Int),rv s])maybeUri(N.parseURIReference s)
parseRelativeReference s=capture(request "parse"[toJSON(2::Int),rv s])maybeUri(N.parseRelativeReference s)
parseAbsoluteURI s=capture(request "parse"[toJSON(3::Int),rv s])maybeUri(N.parseAbsoluteURI s)
isURI s=capture(request "valid"[toJSON(0::Int),rv s])toJSON(N.isURI s)
isURIReference s=capture(request "valid"[toJSON(1::Int),rv s])toJSON(N.isURIReference s)
isRelativeReference s=capture(request "valid"[toJSON(2::Int),rv s])toJSON(N.isRelativeReference s)
isAbsoluteURI s=capture(request "valid"[toJSON(3::Int),rv s])toJSON(N.isAbsoluteURI s)
relativeTo a b=capture(request "resolve"[toJSON(0::Int),uv a,uv b])uv(N.relativeTo a b)
nonStrictRelativeTo a b=capture(request "resolve"[toJSON(1::Int),uv a,uv b])uv(N.nonStrictRelativeTo a b)
relativeFrom a b=capture(request "resolve"[toJSON(2::Int),uv a,uv b])uv(N.relativeFrom a b)
normalizeCase s=capture(request "normalize"[toJSON(0::Int),rv s])rv(N.normalizeCase s)
normalizeEscape s=capture(request "normalize"[toJSON(1::Int),rv s])rv(N.normalizeEscape s)
normalizePathSegments s=capture(request "normalize"[toJSON(2::Int),rv s])rv(N.normalizePathSegments s)
uriIsAbsolute u=capture(request "absolute"[uv u])toJSON(N.uriIsAbsolute u)
uriIsRelative u=capture(request "relative"[uv u])toJSON(N.uriIsRelative u)
pathSegments u=capture(request "segments"[uv u])(toJSON.map rv)(N.pathSegments u)
rectify u=capture(request "rectify"[uv u])uv(N.rectify u)
rectifyAuth u=capture(request "rectify-auth"[av u])av(N.rectifyAuth u)
unEscapeString u=capture(request "unescape"[rv u])rv(N.unEscapeString u)
uriToString f u suffix=capture(request "render"[uv u,maybe Null (\a->if null(N.uriUserInfo a) then Null else rv(f(N.uriUserInfo a)))(N.uriAuthority u),rv suffix])rv(N.uriToString f u suffix)
uriAuthToString f u suffix=capture(request "render-auth"[maybe Null av u,maybe Null (\a->if null(N.uriUserInfo a) then Null else rv(f(N.uriUserInfo a)))u,rv suffix])rv(N.uriAuthToString f u suffix)
escapeURIString p s=capture(request "escape"[toJSON(S.toAscList(S.fromList[ord c|c<-s,p c])),rv s])rv(N.escapeURIString p s)
