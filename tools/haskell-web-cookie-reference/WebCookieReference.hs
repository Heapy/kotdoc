{-# LANGUAGE OverloadedStrings #-}
module Main where
import Control.Exception (ErrorCall(..),try,evaluate)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import Data.Char (chr,ord)
import Data.Maybe (fromJust)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.ByteString.Builder (toLazyByteString)
import qualified Data.Text as T
import Data.Time (UTCTime(..),Day(..),DiffTime,diffTimeToPicoseconds,picosecondsToDiffTime)
import System.IO
import Test.QuickCheck (Arbitrary(..))
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
import qualified OriginalCookieSpec as O
import Web.Cookie

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
text :: Value -> T.Text
text v = T.pack $ map chr (fromJust $ parseMaybe parseJSON v :: [Int])
points :: T.Text -> Value
points = toJSON . map ord . T.unpack
array :: Value -> [Value]
array = fromJust . parseMaybe parseJSON
pair :: (a -> Value) -> (a,a) -> Value
pair f (a,b) = toJSON [f a,f b]
utc :: Value -> UTCTime
utc v = let [a,b] = array v in UTCTime (ModifiedJulianDay (read $ fromJust $ parseMaybe parseJSON a)) (picosecondsToDiffTime (read $ fromJust $ parseMaybe parseJSON b))
viewUtc :: UTCTime -> Value
viewUtc (UTCTime (ModifiedJulianDay day) picos) = toJSON [show day,show $ diffTimeToPicoseconds picos]
cookie :: Value -> SetCookie
cookie v = defaultSetCookie
 { setCookieName = bs $ value v "name",setCookieValue = bs $ value v "value"
 , setCookiePath = optional v "path" >>= fmap B.pack
 , setCookieExpires = optional v "expires" >>= fmap utc
 , setCookieMaxAge = optional v "maxAge" >>= fmap (picosecondsToDiffTime . read)
 , setCookieDomain = optional v "domain" >>= fmap B.pack
 , setCookieHttpOnly = field v "httpOnly",setCookieSecure = field v "secure"
 , setCookieSameSite = case optional v "sameSite" :: Maybe (Maybe String) of
      Just (Just "Lax") -> Just sameSiteLax
      Just (Just "Strict") -> Just sameSiteStrict
      Just (Just "None") -> Just sameSiteNone
      _ -> Nothing
 , setCookiePartitioned = field v "partitioned"
 }
viewCookie :: SetCookie -> Value
viewCookie c = object
 ["name" .= bytes (setCookieName c),"value" .= bytes (setCookieValue c)
 ,"path" .= fmap bytes (setCookiePath c),"expires" .= fmap viewUtc (setCookieExpires c)
 ,"maxAge" .= fmap (show . diffTimeToPicoseconds) (setCookieMaxAge c),"domain" .= fmap bytes (setCookieDomain c)
 ,"httpOnly" .= setCookieHttpOnly c,"secure" .= setCookieSecure c
 ,"sameSite" .= fmap show (setCookieSameSite c),"partitioned" .= setCookiePartitioned c]
viewSet :: SetCookie -> Value
viewSet c = object ["cookie" .= viewCookie c,"render" .= bytes (renderSetCookieBS c),"show" .= show c,
                   "reparsed" .= viewCookie (parseSetCookie $ renderSetCookieBS c)]
propertyInputs :: Int -> Value
propertyInputs count = object
 ["cookies" .= [let cs = unGen arbitrary (mkQCGen (938+i)) (i `mod` 61) :: O.Cookies'
                in object ["input" .= map (pair bytes . (\(a,b)->(O.fromUnChars a,O.fromUnChars b))) cs,
                           "holds" .= O.propParseRenderCookies cs] | i <- [0..count-1]]
 ,"text" .= [let {cs = unGen arbitrary (mkQCGen (1938+i)) (i `mod` 61) :: O.Cookies';
                  convert = T.pack . map (chr . fromIntegral . O.unChar')}
              in object ["input" .= map (pair points . (\(a,b)->(convert a,convert b))) cs,
                         "holds" .= O.propParseRenderCookiesText cs] | i <- [0..count-1]]
 ,"set" .= [let c = unGen arbitrary (mkQCGen (2938+i)) (i `mod` 61) :: SetCookie
             in object ["input" .= viewCookie c,"holds" .= O.propParseRenderSetCookie c] | i <- [0..count-1]]]
run :: Value -> Value
run v = case field v "mode" :: String of
 "default" -> viewSet defaultSetCookie
 "property-inputs" -> propertyInputs $ field v "count"
 "parse" -> let input = bs $ value v "bytes"; cs = parseCookies input; ts = parseCookiesText input
             in object ["cookies" .= map (pair bytes) cs,"text" .= map (pair points) ts,
                        "render" .= bytes (renderCookiesBS cs),"renderText" .= bytes (L.toStrict $ toLazyByteString $ renderCookiesText ts)]
 "render" -> let cs = [ (bs a,bs b) | x <- array $ value v "cookies",let [a,b] = array x ]
                 output = renderCookiesBS cs
             in object ["bytes" .= bytes output,"parsed" .= map (pair bytes) (parseCookies output)]
 "render-text" -> let cs = [ (text a,text b) | x <- array $ value v "cookies",let [a,b] = array x ]
                      output = L.toStrict $ toLazyByteString $ renderCookiesText cs
                  in object ["bytes" .= bytes output,"parsed" .= map (pair points) (parseCookiesText output)]
 "set" -> viewSet $ maybe (parseSetCookie $ bs $ value v "bytes") cookie (optional v "cookie")
 "set-field" -> let c0 = parseSetCookie $ bs $ value v "bytes"
                    c = if optional v "copy" == Just True then c0 {setCookieName = "copied"} else c0
                in case field v "field" :: String of
                     "name" -> bytes $ setCookieName c
                     "maxAge" -> toJSON $ fmap (show . diffTimeToPicoseconds) (setCookieMaxAge c)
                     "eq-name-different" -> toJSON $ c == c {setCookieName = "different"}
                     "eq-self" -> toJSON $ c == c
                     "clear-max-age" -> viewSet $ c {setCookieMaxAge = Nothing}
                     "clear-max-age-then-eq" -> let cleared = c {setCookieMaxAge = Nothing} in toJSON $ cleared == cleared
                     _ -> error "unknown field"
 "date" -> let t = parseCookieExpires $ bs $ value v "bytes"
           in object ["time" .= fmap viewUtc t,"formatted" .= fmap (bytes . formatCookieExpires) t,"show" .= fmap show t]
 "format-date" -> let t = utc $ value v "time"
                  in object ["bytes" .= bytes (formatCookieExpires t),"show" .= show t]
 "equal" -> toJSON $ cookie (value v "left") == cookie (value v "right")
 _ -> error "unknown mode"
main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 let loop = do
       eof <- hIsEOF stdin
       if eof then pure () else do
        input <- B.getLine
        let parsed = fromJust $ decodeStrict input
        result <- try (evaluate $ let out = encode (run parsed) in L.length out `seq` out) :: IO (Either ErrorCall L.ByteString)
        L.putStrLn $ case result of
          Right output -> output
          Left (ErrorCall message) -> encode $ object ["error" .= message]
        loop
 loop
