{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import qualified OriginalConnection as C
import qualified OriginalConnectionTypes as T
import qualified OriginalSocks as S
import qualified OriginalSocksTypes as ST
import qualified OriginalSocksWire as W
import qualified OriginalSocksCommand as SC
import qualified OriginalSocksLowlevel as SL
import qualified ConnectionTlsEffects as TLS
import qualified Network.TLS as RealTLS
import qualified Network.Socket as N
import qualified DialEffects as F
import Data.Aeson hiding (encode)
import qualified Data.Aeson
import Data.IORef
import Data.Serialize (encode)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as Text
import Control.Exception
import Control.Monad
import System.IO
import System.IO.Error

host v = case F.field v "kind" :: String of
 "ipv4" -> ST.SocksAddrIPV4 $ fromInteger $ F.field v "word"
 "ipv6" -> let [a,b,c,d] = map fromInteger (F.field v "words" :: [Integer]) in ST.SocksAddrIPV6 (a,b,c,d)
 "domain" -> ST.SocksAddrDomainName $ F.bytes $ F.value v "bytes"
 _ -> error "host kind"
viewHost h = case h of
 ST.SocksAddrIPV4 w -> object ["kind" .= ("ipv4"::String),"word" .= w,"show" .= show h]
 ST.SocksAddrIPV6 (a,b,c,d) -> object ["kind" .= ("ipv6"::String),"words" .= [a,b,c,d],"show" .= show h]
 ST.SocksAddrDomainName n -> object ["kind" .= ("domain"::String),"bytes" .= B.unpack n,"show" .= show h]
address v = ST.SocksAddress (host $ F.value v "host") (fromInteger $ F.field v "port")
viewAddress (h,p) = object ["host" .= viewHost h,"port" .= (fromIntegral p::Int)]
method v = case F.optional v "other" of
 Just n -> ST.SocksMethodOther $ fromInteger n
 Nothing -> toEnum $ fromInteger (F.field v "code"::Integer)
command v = case F.optional v "other" of
 Just n -> ST.SocksCommandOther $ fromInteger n
 Nothing -> toEnum $ fromInteger (F.field v "code"::Integer)
reply v = case F.optional v "other" of
 Just n -> ST.SocksReplyError $ ST.SocksErrorOther $ fromInteger n
 Nothing -> toEnum $ fromInteger (F.field v "code"::Integer)
request v = W.SocksRequest (command $ F.value v "command") (host $ F.value v "host") (fromInteger $ F.field v "port")
response v = W.SocksResponse (reply $ F.value v "reply") (host $ F.value v "host") (fromInteger $ F.field v "port")
viewRequest r = object ["command" .= show (W.requestCommand r),"host" .= viewHost (W.requestDstAddr r),"port" .= (fromIntegral (W.requestDstPort r)::Int)]
viewResponse r = object ["reply" .= show (W.responseReply r),"host" .= viewHost (W.responseBindAddr r),"port" .= (fromIntegral (W.responseBindPort r)::Int)]
proxy Null = Nothing
proxy v = Just $ case F.field v "kind" :: String of
 "simple" -> T.SockSettingsSimple (F.string $ F.value v "host") (fromInteger $ F.field v "port")
 "other" -> T.OtherProxy (F.string $ F.value v "host") (fromInteger $ F.field v "port")
 "environment" -> T.SockSettingsEnvironment $ case F.optional v "name" of Just Null -> Nothing; Just x -> Just $ F.string x; _ -> Nothing
 _ -> error "proxy kind"
failure (e::SomeException) = case fromException e :: Maybe IOException of
 Just io -> object ["error" .= ("io"::String),"type" .= show (ioeGetErrorType io),"location" .= ioeGetLocation io,
                    "description" .= ioeGetErrorString io,"filename" .= ioeGetFileName io]
 Nothing -> case fromException e of
  Just (C.HostNotResolved h) -> object ["error" .= ("HostNotResolved"::String),"host" .= map fromEnum h]
  Nothing -> case fromException e of
   Just (C.HostCannotConnect h es) -> object ["error" .= ("HostCannotConnect"::String),"host" .= map fromEnum h,"errors" .= map (failure.toException) es]
   Nothing -> case fromException e :: Maybe ST.SocksError of
    Just se -> object ["error" .= ("socks"::String),"show" .= show se]
    Nothing -> case fromException e of
     Just (ErrorCall msg) -> object ["error" .= ("protocol"::String),"message" .= msg]
     Nothing -> case fromException e of
      Just (AssertionFailed msg) -> object ["error" .= ("fatal"::String),"message" .= msg]
      _ -> object ["error" .= ("unexpected"::String),"message" .= displayException e]

run v = do
 F.reset v; TLS.reset; writeIORef TLS.config v
 result <- try $ do
  r <- case F.field v "api" :: String of
   "dial" -> do
    let secure = if F.optional v "secure" == Just True then Just $ T.TLSSettings $ RealTLS.defaultParamsClient "ignored" B.empty else Nothing
        p = T.ConnectionParams (F.string $ F.value v "host") (fromInteger $ F.field v "port") secure (proxy $ maybe Null id $ F.optional v "proxy")
    c <- C.connectTo (error "context-forced") p
    tls <- C.connectionIsSecure c
    when (F.optional v "close" == Just True) $ C.connectionClose c
    pure $ object ["secure" .= tls,"host" .= map fromEnum (fst $ T.connectionID c),"port" .= (fromIntegral (snd $ T.connectionID c)::Int)]
   "encode" -> do
    b <- case F.field v "kind" :: String of
     "hello" -> pure $ encode $ W.SocksHello $ map method $ F.field v "methods"
     "helloResponse" -> pure $ encode $ W.SocksHelloResponse $ method $ F.value v "method"
     "request" -> pure $ encode $ request v
     "response" -> pure $ encode $ response v
     _ -> error "encoding kind"
    pure $ toJSON $ B.unpack b
   "enum" -> do
    let n = fromInteger (F.field v "number"::Integer)
        m = toEnum n :: ST.SocksMethod
        c = toEnum n :: ST.SocksCommand
        e = toEnum n :: ST.SocksError
        r = toEnum n :: ST.SocksReply
    pure $ object ["method" .= show m,"command" .= show c,"error" .= show e,"reply" .= show r,
                   "methodCode" .= fromEnum m,"commandCode" .= fromEnum c,"errorCode" .= fromEnum e,"replyCode" .= fromEnum r]
   api -> bracket (N.socket N.AF_INET N.Stream N.defaultProtocol) N.close $ \sock -> case api of
    "hello" -> toJSON . map show . W.getSocksHelloMethods <$> (SC.waitSerialized sock :: IO W.SocksHello)
    "helloResponse" -> toJSON . show . W.getSocksHelloResponseMethod <$> (SC.waitSerialized sock :: IO W.SocksHelloResponse)
    "request" -> viewRequest <$> (SC.waitSerialized sock :: IO W.SocksRequest)
    "response" -> viewResponse <$> (SC.waitSerialized sock :: IO W.SocksResponse)
    "connect" -> viewAddress <$> S.socksConnectWithSocket sock (S.defaultSocksConf $ N.SockAddrInet 1080 0) (address v)
    "rpc" -> do
     r <- SC.rpc sock (request v)
     pure $ either (\e -> object ["left" .= show e]) (\a -> object ["right" .= viewAddress a]) r
    "listen" -> viewRequest <$> SL.socksListen sock
    _ -> error "api"
  evaluate $ L.length $ Data.Aeson.encode r
  pure r
 F.cleanup
 es <- reverse <$> readIORef F.logs
 pure $ object ["result" .= either failure id result,"events" .= es]
main = do
 hSetBuffering stdout LineBuffering
 input <- L.getContents
 forM_ (L.lines input) $ \line -> case eitherDecode line of
  Left e -> error e
  Right v -> run v >>= L.putStrLn . Data.Aeson.encode
