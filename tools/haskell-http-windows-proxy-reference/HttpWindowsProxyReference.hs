{-# LANGUAGE OverloadedStrings #-}
module Main where
import OriginalWindowsProxy
import ProxyEffects
import ProxyProjection
import Data.Aeson
import Data.IORef
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Data.Char (chr,ord)
import Control.Exception
import System.IO

viewSettings settings = object ["show" .= show settings,"prec11" .= showsPrec 11 settings "TAIL"]
run :: Value -> IO Value
run v = do
 writeIORef config v
 writeIORef events []
 let protocol=httpProtocol $ maybe False id (optional v "secure")
     txt=map chr (field v "text")
     fallback=case maybe "request" id (optional v "fallback") :: String of
       "request" -> EHFromRequest
       "none" -> EHNoProxy
       "use" -> EHUseProxy (readProxy $ field v "fallbackProxy")
     apply f = do
       case optional v "after" of {Nothing -> pure ();Just after -> writeIORef config after}
       results<-mapM (\h -> observe $ pure $ f h) (field v "hosts" :: [Value])
       pure $ object ["results" .= results]
     make action project = do
       made<-try action
       case made of
        Left e -> pure $ object ["constructionError" .= failure e]
        Right f -> apply $ \h -> project (f (bs h))
 result<-case field v "api" :: String of
   "parse" -> observe $ pure $ maybe Null viewSettings $ parseProxy protocol txt
   "select" -> observe $ pure $ toJSON $ fmap (map ord) $ parseWindowsProxy protocol txt
   "registry" -> observe $ fmap (toJSON . fmap (\(a,b) -> [map ord a,map ord b])) registryProxyString
   "system" -> make (systemProxy protocol) (maybe Null viewSettings)
   "helper" -> do
      made<-try $ systemProxyHelper (T.pack . map chr <$> optional v "override") protocol fallback
      case made of
       Left e -> pure $ object ["constructionError" .= failure e]
       Right f -> apply $ \h -> viewReq (f (request h))
   _ -> error "api"
 log<-reverse <$> readIORef events
 pure $ object ["result" .= result,"events" .= log]
main = do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line -> case eitherDecode line of
   Left e -> error e
   Right v -> run v >>= L.putStrLn . encode) . L.lines
