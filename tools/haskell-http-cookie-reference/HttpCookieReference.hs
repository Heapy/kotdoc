{-# LANGUAGE OverloadedStrings #-}
module Main where
import Control.Exception (ErrorCall(..),try,evaluate)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import Data.Maybe (fromJust,isJust)
import Data.IORef
import qualified Data.ByteString as B
import qualified Data.ByteString.Char8 as C
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.CaseInsensitive as CI
import Data.Time (UTCTime(..),Day(..),diffTimeToPicoseconds,picosecondsToDiffTime)
import qualified Data.IP as IP
import Text.Read (readMaybe)
import System.IO
import Network.HTTP.Client.Internal
import Network.HTTP.Types (statusCode,statusMessage,httpMajor,httpMinor)
import qualified Web.Cookie as W
import qualified Test.Hspec as H
import qualified OriginalCookieSpec

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
utc :: Value -> UTCTime
utc v = let [a,b] = array v in UTCTime (ModifiedJulianDay (read $ fromJust $ parseMaybe parseJSON a)) (picosecondsToDiffTime (read $ fromJust $ parseMaybe parseJSON b))
viewUtc :: UTCTime -> Value
viewUtc (UTCTime (ModifiedJulianDay d) t) = toJSON [show d,show $ diffTimeToPicoseconds t]
cookie :: Value -> Cookie
cookie v = Cookie (bs $ value v "name") (bs $ value v "value") (utc $ value v "expiry")
                  (bs $ value v "domain") (bs $ value v "path") (utc $ value v "creation") (utc $ value v "access")
                  (field v "persistent") (field v "hostOnly") (field v "secureOnly") (field v "httpOnly")
viewCookie :: Cookie -> Value
viewCookie c = object ["name" .= bytes (cookie_name c),"value" .= bytes (cookie_value c),"expiry" .= viewUtc (cookie_expiry_time c),
 "domain" .= bytes (cookie_domain c),"path" .= bytes (cookie_path c),"creation" .= viewUtc (cookie_creation_time c),"access" .= viewUtc (cookie_last_access_time c),
 "persistent" .= cookie_persistent c,"hostOnly" .= cookie_host_only c,"secureOnly" .= cookie_secure_only c,"httpOnly" .= cookie_http_only c]
jar :: Value -> CookieJar
jar = createCookieJar . map cookie . array
viewJar :: CookieJar -> Value
viewJar = toJSON . map viewCookie . destroyCookieJar
headers :: Value -> [(CI.CI B.ByteString,B.ByteString)]
headers v = [(CI.mk $ bs a,bs b) | p <- array v,let [a,b]=array p]
viewHeaders hs = toJSON [[bytes (CI.original k),bytes v] | (k,v) <- hs]
req :: Value -> Request
req v = defaultRequest
 { host = maybe "localhost" bs (optional v "host"),path = maybe "/" bs (optional v "path")
 , secure = maybe False id (optional v "secure"),method = maybe "GET" bs (optional v "method")
 , requestBody = RequestBodyBS $ maybe B.empty bs (optional v "body")
 , requestHeaders = maybe [] headers (optional v "headers")
 , cookieJar = case optional v "cookieJar" :: Maybe (Maybe Value) of
      Nothing -> Just mempty
      Just j -> fmap jar j
 }
viewReq r = object ["host" .= bytes (host r),"path" .= bytes (path r),"secure" .= secure r,"method" .= bytes (method r),
 "headers" .= viewHeaders (requestHeaders r),"body" .= case requestBody r of {RequestBodyBS b -> bytes b;_ -> Null},
 "cookieJar" .= fmap viewJar (cookieJar r)]
setCookie v = case optional v "setCookie" of
 Nothing -> W.parseSetCookie $ bs $ value v "set"
 Just c -> W.defaultSetCookie
  { W.setCookieName=bs $ value c "name",W.setCookieValue=bs $ value c "value"
  , W.setCookiePath=optional c "path" >>= fmap B.pack,W.setCookieDomain=optional c "domain" >>= fmap B.pack
  , W.setCookieExpires=optional c "expires" >>= fmap utc
  , W.setCookieMaxAge=optional c "maxAge" >>= fmap (picosecondsToDiffTime . read)
  , W.setCookieHttpOnly=field c "httpOnly",W.setCookieSecure=field c "secure"
  , W.setCookiePartitioned=field c "partitioned"
  , W.setCookieSameSite=case optional c "sameSite" :: Maybe (Maybe String) of
      Just (Just "Lax") -> Just W.sameSiteLax
      Just (Just "Strict") -> Just W.sameSiteStrict
      Just (Just "None") -> Just W.sameSiteNone
      _ -> Nothing }
rawResponse r = do
 (conn,_,_) <- dummyConnection ["HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"]
 getResponse Nothing Nothing Nothing r (dummyManaged conn) Nothing
response r hs cj = do
 result <- rawResponse r
 pure result { responseHeaders = hs,responseCookieJar = cj,responseEarlyHints = [("Link","early")] }
viewRes r = object ["status" .= [toJSON (statusCode $ responseStatus r),bytes (statusMessage $ responseStatus r)],
 "version" .= [httpMajor (responseVersion r),httpMinor (responseVersion r)],
 "headers" .= viewHeaders (responseHeaders r),"cookieJar" .= viewJar (responseCookieJar r),
 "request" .= viewReq (getOriginalRequest r),"early" .= viewHeaders (responseEarlyHints r)]
run :: Value -> IO Value
run v = case field v "mode" :: String of
 "original-tests" -> do
   writeIORef H.passed []; OriginalCookieSpec.spec; toJSON <$> readIORef H.passed
 "ip" -> let input = C.unpack $ bs $ value v "bytes"
         in pure $ object ["v4" .= fmap IP.fromIPv4 (readMaybe input),"v6" .= fmap IP.fromIPv6 (readMaybe input)]
 "origin" -> pure $ toJSON $ isPotentiallyTrustworthyOrigin (field v "secure") (bs $ value v "host")
 "is-ip" -> pure $ toJSON $ isIpAddress (bs $ value v "bytes")
 "domain" -> pure $ toJSON $ domainMatches (bs $ value v "host") (bs $ value v "domain")
 "path" -> pure $ toJSON $ pathMatches (bs $ value v "request") (bs $ value v "cookie")
 "default-path" -> pure $ bytes $ defaultPath $ req $ value v "request"
 "compare" -> let a=cookie $ value v "left";b=cookie $ value v "right"
              in pure $ object ["equal" .= equalCookie a b,"equiv" .= equivCookie a b,"compare" .= show (compareCookies a b)]
 "jar-pair" -> let a=jar $ value v "left";b=jar $ value v "right"
               in pure $ object ["equal" .= equalCookieJar a b,"equiv" .= equivCookieJar a b,"combined" .= viewJar (a<>b),"show" .= show (a<>b)]
 "remove" -> let (old,result)=removeExistingCookieFromCookieJar (cookie $ value v "cookie") (jar $ value v "jar")
             in pure $ object ["old" .= fmap viewCookie old,"jar" .= viewJar result]
 "insert" -> pure $ viewJar $ insertCheckedCookie (cookie $ value v "cookie") (jar $ value v "jar") (field v "http")
 "evict" -> pure $ viewJar $ evictExpiredCookies (jar $ value v "jar") (utc $ value v "now")
 "generate" -> pure $ toJSON $ fmap viewCookie $ generateCookie (setCookie v) (req $ value v "request") (utc $ value v "now") (field v "http")
 "generated-field" -> let c=generateCookie (setCookie v) (req $ value v "request") (utc $ value v "now") (field v "http")
                      in pure $ case field v "field" :: String of
                         "exists" -> toJSON $ isJust c
                         "name" -> toJSON $ fmap (bytes . cookie_name) c
                         "persistent" -> toJSON $ fmap cookie_persistent c
                         "expiry" -> toJSON $ fmap (viewUtc . cookie_expiry_time) c
                         "equiv-self" -> toJSON $ fmap (\x -> equivCookie x x) c
                         "equal-self" -> toJSON $ fmap (\x -> equalCookie x x) c
                         "show" -> toJSON $ fmap show c
                         _ -> error "unknown field"
 "set-demand" -> let c=setCookie v
                 in pure $ object ["present" .= isJust (W.setCookieMaxAge c),"equalAbsent" .= (c == c {W.setCookieMaxAge = Nothing})]
 "receive" -> pure $ viewJar $ receiveSetCookie (setCookie v) (req $ value v "request") (utc $ value v "now") (field v "http") (jar $ value v "jar")
 "receive-compute" -> let r=req $ value v "request";now=utc $ value v "now"
                          received=receiveSetCookie (setCookie v) r now (field v "http") (jar $ value v "jar")
                          (line,result)=computeCookieString r received now (field v "http")
                      in pure $ object ["header" .= bytes line,"names" .= map (bytes . cookie_name) (destroyCookieJar result),
                                        "persistent" .= map cookie_persistent (destroyCookieJar result)]
 "receive-evict" -> let r=req $ value v "request";now=utc $ value v "now"
                   in pure $ viewJar $ evictExpiredCookies (receiveSetCookie (setCookie v) r now (field v "http") (jar $ value v "jar")) now
 "compute" -> let (line,result)=computeCookieString (req $ value v "request") (jar $ value v "jar") (utc $ value v "now") (field v "http")
              in pure $ object ["header" .= bytes line,"jar" .= viewJar result]
 "insert-request" -> let (r,result)=insertCookiesIntoRequest (req $ value v "request") (jar $ value v "jar") (utc $ value v "now")
                     in pure $ object ["request" .= viewReq r,"jar" .= viewJar result]
 "response" -> do
   let r=req $ value v "request"
   before <- response r (headers $ value v "headers") (jar $ value v "responseJar")
   let (cj,after)=updateCookieJar before r (utc $ value v "now") (jar $ value v "jar")
   lbs <- lbsResponse after
   pure $ object ["jar" .= viewJar cj,"response" .= viewRes lbs,"body" .= map bytes (L.toChunks $ responseBody lbs)]
 "get-response" -> do
   r <- rawResponse (req $ value v "request")
   l <- lbsResponse r {responseEarlyHints = [("Link","early")]}
   pure $ object ["response" .= viewRes l,"body" .= map bytes (L.toChunks $ responseBody l)]
 _ -> error "unknown mode"
main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 let loop = do
      eof <- hIsEOF stdin
      if eof then pure () else do
       input <- B.getLine
       let parsed=fromJust $ decodeStrict input
       result <- try (run parsed >>= \v -> evaluate (let out=encode v in L.length out `seq` out)) :: IO (Either ErrorCall L.ByteString)
       L.putStrLn $ case result of {Right output -> output;Left (ErrorCall message) -> encode $ object ["error" .= message]}
       loop
 loop
