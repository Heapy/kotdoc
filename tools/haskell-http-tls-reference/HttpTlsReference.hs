{-# LANGUAGE OverloadedStrings, CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import qualified OriginalHttpTls as T
import qualified OriginalTlsError as TE
import qualified ConnectionEffects as NC
import qualified TlsEffects as F
import Effects
import HttpRedirectProjection
import Network.HTTP.Client.Internal hiding (getGlobalManager,setGlobalManager)
import Network.HTTP.Types (statusCode)
import Control.Exception
import Control.Monad
import Data.Aeson
import Data.IORef
import Data.Maybe (fromJust)
import qualified Data.Map as M
import qualified Data.Text as Text
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Default as D
import System.IO
import System.Mem.StableName
import Data.KeyedPool

settings v= T.mkManagerSettingsContext' (base v) context (tls $ maybe 1 id $ optional v "tls") (sock <$> optValue v "httpSocks") (sock <$> optValue v "httpsSocks")
 where context=NC.Context <$> optional v "context"
base v=if optional v "custom"==Just True then F.defaultManagerSettings
 {managerRawConnection=F.rawFactory "custom",managerTlsConnection=F.rawFactory "custom-tls"
 ,managerTlsProxyConnection=customProxyFactory
 ,managerProxyInsecure=F.proxyPolicy "custom-http",managerProxySecure=F.proxyPolicy "custom-https"
 ,managerRetryableException=const False,managerWrapException= \_ a->a
 ,managerConnCount=3,managerIdleConnectionCount=7,managerResponseTimeout=ResponseTimeoutMicro 42}
 else F.defaultManagerSettings
customProxyFactory=do
 emit $ object ["event" .= ("proxy-tls-custom-init"::String)]
 pure $ \_ _ _ _ host port->do
   emit $ object ["event" .= ("proxy-tls-custom-connect"::String),"host" .= host,"port" .= port]
   makeConnection (pure B.empty) (const $ pure ()) (pure ())
viewSettings s=object ["count" .= managerConnCount s,"idle" .= managerIdleConnectionCount s,"timeout" .= show (managerResponseTimeout s)]
viewRes r=object ["status" .= statusCode (responseStatus r),"headers" .= viewHeaders (responseHeaders r),"jar" .= show (responseCookieJar r),"show" .= show r]
readError v=case field v "kind"::Int of
 0->TE.Error_Misc d;1->TE.Error_Protocol d a;2->TE.Error_Protocol_Warning d a;3->TE.Error_Certificate d
 4->TE.Error_HandshakePolicy d;5->TE.Error_EOF;6->TE.Error_Packet d;7->TE.Error_Packet_unexpected d (string $ value v "other")
 8->TE.Error_Packet_Parsing d;_->TE.Error_TCP_Terminate
 where d=maybe "description" string $ optional v "description";a=TE.AlertDescription $ fromIntegral (maybe 255 id (optional v "alert")::Integer)
readException v=case field v "kind"::Int of
 0->TE.Terminated (maybe False id $ optional v "fatal") (maybe "reason" string $ optional v "description") e
 1->TE.HandshakeFailed e;2->TE.PostHandshake e;3->TE.Uncontextualized e;4->TE.ConnectionNotEstablished;_->TE.MissingHandshake
 where e=readError $ value v "error"
run v=do
 writeIORef config v;writeIORef logs []
 result<-case field v "api"::String of
  "socks"->do
    let env=[(string a,string b)|[a,b]<-map array $ array $ value v "environment"]
        folded=M.fromList [(Text.toLower $ Text.pack a,b)|(a,b)<-env]
    pure $ sockView $ T.parseSocksSettings env folded (Text.pack $ string $ value v "name")
  "errors"->do
    let e=readError $ value v "error";ex=readException $ value v "exception"
        level=TE.AlertLevel $ fromIntegral (field v "level"::Integer)
        alert=TE.AlertDescription $ fromIntegral (field v "alert"::Integer)
    pure $ object ["error" .= show e,"errorPrec11" .= showsPrec 11 e "TAIL"
        ,"exception" .= show ex,"exceptionPrec11" .= showsPrec 11 ex "TAIL"
        ,"equalError" .= (e==readError (value v "otherError")),"equalException" .= (ex==readException (value v "otherException"))
        ,"level" .= show level,"levelPrec11" .= showsPrec 11 level "TAIL","alert" .= show alert,"alertPrec11" .= showsPrec 11 alert "TAIL"]
  "wrap"->do
    let s=settings v;r=req $ value v "request"
    tag<-pure $ field v "failure"
    se<-try (raise tag)::IO (Either SomeException ())
    retry<-case se of Left e->pure $ managerRetryableException s e;_->error "raise"
    outcome<-attempt $ managerWrapException s r $ raise tag
    pure $ object ["retry" .= retry,"outcome" .= outcome]
  "initialize"->attempt $ do
    let context=NC.Context <$> optional v "context";socks=sock <$> optValue v "socks"
    if optional v "proxy"==Just True then T.getTlsProxyConnection context (tls 1) socks >> pure Null
      else T.getTlsConnection context (Just $ tls 1) socks >> pure Null
  "factory"->attempt $ do
    let context=NC.Context <$> optional v "context";tlsSetting=maybe (Just $ tls 1) (fmap tls) $ optional v "tls"
        socks=sock <$> optValue v "socks";host=maybe "[::1]" string $ optional v "host";port=maybe 443 id $ optional v "port"
    c<-if optional v "proxy"==Just True then do
        f<-T.getTlsProxyConnection context (maybe (tls 1) id tlsSetting) socks
        f (maybe "CONNECT" bs $ optional v "connectBytes") (\c->do
            emit $ object ["event" .= ("check"::String)]
            when (optional v "checkRead"==Just True) $ connectionRead c >>= \b->emit $ object ["event" .= ("check-read"::String),"bytes" .= bytes b]
            when (optional v "checkClose"==Just True) $ connectionClose c
            case optional v "checkFailure" of {Just tag->raise tag;_ ->pure ()})
            (maybe "[server]" string $ optional v "server") (optional v "address") host port
      else do
        f<-T.getTlsConnection context tlsSetting socks;f (optional v "address") host port
    values<-forM (maybe [] array $ optional v "commands") $ \cmd->attempt $ case field cmd "op"::String of
      "read"->bytes <$> connectionRead c
      "unread"->connectionUnread c (bs $ value cmd "bytes") >> pure Null
      "write"->connectionWrite c (bs $ value cmd "bytes") >> pure Null
      "close"->connectionClose c >> pure Null
      _->raise "unknown-command"
    pure $ toJSON values
  "digest"->attempt $ do
    let r=req $ value v "request"
    inner<-T.applyDigestAuth (bs $ value v "user") (bs $ value v "password") r (error "manager-forced")::IO (IO Request)
    if optional v "defer"==Just True then pure $ object ["deferred" .= True] else do
      outcome<-try inner::IO (Either T.DigestAuthException Request)
      pure $ case outcome of
        Right r'->object ["request" .= viewReq r']
        Left ex@(T.DigestAuthException r' res details)->object ["digestError" .= show details,"request" .= viewReq r',"response" .= viewRes res
           ,"show" .= show ex,"showPrec11" .= showsPrec 11 ex "TAIL","display" .= T.displayDigestAuthException ex]
  "manager"->do
    identities<-newIORef [];managers<-newIORef []
    let identify m=do
          key<-makeStableName $! m;old<-readIORef identities
          case lookup key old of
            Just i->pure i
            Nothing->do let i=length old
                        modifyIORef' identities ((key,i):);pure i
    outputs<-forM (array $ value v "commands") $ \cmd->attempt $ do
      case field cmd "op"::String of
        "mk"->do
          m<-F.newManager $ settings cmd
          modifyIORef' managers (++[m]);toJSON <$> identify m
        "new"->do
          m<-if optional cmd "with"==Just True then T.newTlsManagerWith (base cmd) else T.newTlsManager
          modifyIORef' managers (++[m]);toJSON <$> identify m
        "get"->T.getGlobalManager >>= \m->modifyIORef' managers (++[m]) >> (toJSON <$> identify m)
        "set"->do ms<-readIORef managers;T.setGlobalManager (ms!!field cmd "manager");pure Null
        "proxy"->do
          ms<-readIORef managers
          pure $ viewReq $ mSetProxy (ms!!field cmd "manager") (req $ value cmd "request")
        "connect"->do
          ms<-readIORef managers;mc<-getConn (req $ value cmd "request") (ms!!field cmd "manager")
          managedRelease mc DontReuse;pure Null
        _->raise "manager-command"
    pure $ toJSON outputs
  "settings"->pure $ viewSettings $ settings v
  _->error "api"
 out<-evaluate $ L.length (encode result) `seq` result
 events<-reverse <$> readIORef logs
 pure $ object ["result" .= out,"events" .= events]
main=do
 input<-B.getLine
 output<-run $ fromJust $ decodeStrict input
 L.putStrLn $ encode output
