{-# LANGUAGE OverloadedStrings #-}
module ConnectionEffects (module NC,ConnectionContext(..),Connection(..),initConnectionContext,connectTo,connectionClose,connectionGetChunk,connectionPut,connectionSetSecure) where
import Network.Connection as NC hiding (ConnectionContext,Connection,initConnectionContext,connectTo,connectionClose,connectionGetChunk,connectionPut,connectionSetSecure)
import Effects
import HttpRedirectProjection
import Data.IORef
import System.IO.Unsafe
import Data.Aeson
import qualified Data.ByteString as B

newtype ConnectionContext=Context Int
newtype Connection=Connection (Int,IORef [Value])
contexts :: IORef Int
{-# NOINLINE contexts #-}
contexts=unsafePerformIO $ newIORef 0
connections :: IORef Int
{-# NOINLINE connections #-}
connections=unsafePerformIO $ newIORef 0
initConnectionContext=do
 i<-atomicModifyIORef' contexts (\n->(n+1,n));emit $ object ["event" .= ("context"::String),"index" .= i]
 v<-getConfig
 case optional v "contextFailure" of {Just tag->raise tag;_ ->pure ()}
 pure $ Context i
connectTo context params=do
 i<-atomicModifyIORef' connections (\n->(n+1,n));emit $ object ["event" .= ("connect-start"::String),"index" .= i]
 v<-getConfig
 case optional v "beforeConnectFailure" of {Just tag->raise tag;_ ->pure ()}
 let ctx=if optional v "forceContext"==Just False then Nothing else case context of Context n->Just n
 emit $ object ["event" .= ("connect"::String),"index" .= i,"context" .= ctx
   ,"host" .= points (connectionHostname params),"port" .= (fromIntegral (connectionPort params)::Int)
   ,"tls" .= tlsView (connectionUseSecure params),"socks" .= sockView (connectionUseSocks params)]
 case optional v "connectFailure" of {Just tag->raise tag;_ ->pure ()}
 ref<-newIORef $ maybe [] array $ optional v "chunks"
 pure $ Connection (i,ref)
connectionClose (Connection (i,_))=do
 emit $ object ["event" .= ("nc-close"::String),"index" .= i]
 v<-getConfig;case optional v "closeFailure" of {Just tag->raise tag;_ ->pure ()}
connectionGetChunk (Connection (i,ref))=do
 emit $ object ["event" .= ("nc-read"::String),"index" .= i]
 x<-atomicModifyIORef' ref (\xs->case xs of {[]->([],Nothing);a:rest->(rest,Just a)})
 case x of {Just (String tag)->raise $ string $ String tag;Just a->pure $ bs a;Nothing->pure B.empty}
connectionPut (Connection (i,_)) b=do
 emit $ object ["event" .= ("nc-write"::String),"index" .= i,"bytes" .= bytes b]
 v<-getConfig;case optional v "writeFailure" of {Just tag->raise tag;_ ->pure ()}
connectionSetSecure (Context ctx) (Connection (i,_)) settings=do
 emit $ object ["event" .= ("secure"::String),"index" .= i,"context" .= ctx,"tls" .= tlsView (Just settings)]
 v<-getConfig;case optional v "secureFailure" of {Just tag->raise tag;_ ->pure ()}
