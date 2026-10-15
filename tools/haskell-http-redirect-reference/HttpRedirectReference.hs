{-# LANGUAGE OverloadedStrings #-}
module Main where
import Control.Exception (ErrorCall(..),try,evaluate)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import Data.Maybe (fromJust)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.CaseInsensitive as CI
import Data.Time (UTCTime(..),Day(..))
import Network.HTTP.Client.Internal
import Network.HTTP.Types (HttpVersion(..))
import System.IO

field :: FromJSON a => Value -> Key -> a
field (Object o) k = fromJust (K.lookup k o >>= parseMaybe parseJSON)
field _ _ = error "object required"
value :: Value -> Key -> Value
value (Object o) k = fromJust $ K.lookup k o
value _ _ = error "object required"
optional :: FromJSON a => Value -> Key -> Maybe a
optional (Object o) k = K.lookup k o >>= parseMaybe parseJSON
optional _ _ = Nothing
bs :: Value -> B.ByteString
bs = B.pack . fromJust . parseMaybe parseJSON
bytes :: B.ByteString -> Value
bytes = toJSON . B.unpack
array :: Value -> [Value]
array = fromJust . parseMaybe parseJSON
headers :: Value -> [(CI.CI B.ByteString,B.ByteString)]
headers v = [(CI.mk $ bs a,bs b) | p <- array v,let [a,b]=array p]
viewHeaders hs = toJSON [[bytes (CI.original k),bytes v] | (k,v) <- hs]
jar :: Value -> CookieJar
jar = createCookieJar . map make . array where
 make n = let t=UTCTime (ModifiedJulianDay 60000) 0 in Cookie (bs n) "value" t "example.com" "/" t t False True False False
body :: Value -> RequestBody
body v = case field v "kind" :: String of
 "strict" -> RequestBodyBS $ bs $ value v "bytes"
 "lazy" -> RequestBodyLBS $ L.fromChunks $ map bs $ array $ value v "chunks"
 "builder" -> RequestBodyBuilder (field v "length") (error "body-forced")
 "stream" -> RequestBodyStream (field v "length") (error "body-forced")
 "chunked" -> RequestBodyStreamChunked (error "body-forced")
 "io" -> RequestBodyIO (error "body-forced")
 _ -> error "body kind"
viewBody b = case b of
 RequestBodyBS x -> object ["kind" .= ("strict"::String),"bytes" .= bytes x]
 RequestBodyLBS x -> object ["kind" .= ("lazy"::String),"chunks" .= map bytes (L.toChunks x)]
 RequestBodyBuilder n _ -> object ["kind" .= ("builder"::String),"length" .= n]
 RequestBodyStream n _ -> object ["kind" .= ("stream"::String),"length" .= n]
 RequestBodyStreamChunked _ -> object ["kind" .= ("chunked"::String)]
 RequestBodyIO _ -> object ["kind" .= ("io"::String)]
predicate :: Value -> CI.CI B.ByteString -> Bool
predicate v name = case field v "kind" :: String of
 "none" -> False
 "all" -> True
 "fail" -> error "strip-forced"
 "names" -> name `elem` map (CI.mk . bs) (array $ value v "names")
 "original" -> CI.original name `elem` map bs (array $ value v "names")
 _ -> error "predicate kind"
req :: Value -> Request
req v = defaultRequest
 { host = maybe "localhost" bs (optional v "host"),path = maybe "/" bs (optional v "path")
 , port = maybe 80 id (optional v "port"),queryString = maybe B.empty bs (optional v "query")
 , secure = maybe False id (optional v "secure"),method = maybe "GET" bs (optional v "method")
 , requestBody = maybe (RequestBodyLBS L.empty) body (optional v "body")
 , requestHeaders = maybe [] headers (optional v "headers")
 , cookieJar = case optional v "cookieJar" :: Maybe (Maybe Value) of
      Nothing -> Just mempty
      Just j -> fmap jar j
 , shouldStripHeaderOnRedirect = maybe (const False) predicate (optional v "strip")
 , shouldStripHeaderOnRedirectIfOnDifferentHostOnly = maybe False id (optional v "onlyDifferent")
 , redirectCount = maybe 10 id (optional v "redirectCount")
 , rawBody = maybe False id (optional v "rawBody")
 , requestVersion = case optional v "version" of {Just [a,b] -> HttpVersion a b;_ -> HttpVersion 1 1}
 , proxy = case optional v "proxy" of {Nothing -> Nothing; Just x -> Just $ Proxy (bs $ value x "host") (field x "port")}
 , proxySecureMode = if optional v "proxyConnect" == Just False then ProxySecureWithoutConnect else ProxySecureWithConnect
 }
viewReq r = object
 ["host" .= bytes (host r),"port" .= port r,"path" .= bytes (path r),"query" .= bytes (queryString r)
 ,"secure" .= secure r,"method" .= bytes (method r),"headers" .= viewHeaders (requestHeaders r),"body" .= viewBody (requestBody r)
 ,"cookieJar" .= fmap show (cookieJar r),"onlyDifferent" .= shouldStripHeaderOnRedirectIfOnDifferentHostOnly r
 ,"redirectCount" .= redirectCount r,"rawBody" .= rawBody r,"version" .= [httpMajor (requestVersion r),httpMinor (requestVersion r)]
 ,"proxy" .= fmap (\p -> object ["host" .= bytes (proxyHost p),"port" .= proxyPort p]) (proxy r)
 ,"proxyConnect" .= (proxySecureMode r == ProxySecureWithConnect),"show" .= show r]
run :: Value -> Value
run v = let original=req $ value v "original";initial=req $ value v "request"
            step current s = getRedirectedRequest original current (headers $ value s "headers") (jar $ value s "jar") (field s "status")
            go current [] = []
            go current (s:ss) = let next=step current s in fmap viewReq next : maybe [] (\r -> go r ss) next
        in toJSON $ go initial (array $ value v "steps")
main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 let loop = do
      eof <- hIsEOF stdin
      if eof then pure () else do
       input <- B.getLine
       let parsed=fromJust $ decodeStrict input
       result <- try (evaluate (let out=encode $ run parsed in L.length out `seq` out)) :: IO (Either ErrorCall L.ByteString)
       L.putStrLn $ case result of {Right output -> output;Left (ErrorCall message) -> encode $ object ["error" .= message]}
       loop
 loop
