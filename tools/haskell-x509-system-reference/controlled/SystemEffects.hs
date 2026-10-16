{-# LANGUAGE OverloadedStrings #-}
module SystemEffects where
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Aeson.Key as Key
import qualified Data.Vector as V
import qualified Data.Text as T
import qualified Data.ByteString as B
import Data.IORef
import qualified Data.Map as M
import Data.Word
import Data.List (elemIndex)
import Control.Exception
import Control.Monad
import System.IO
import System.IO.Unsafe
import System.Exit
import System.Directory
import Foreign.Ptr
import Foreign.Storable
import Foreign.Marshal.Alloc
import Foreign.Marshal.Array
import Data.ASN1.Error
import Data.X509
import Data.X509.CertificateStore (CertificateStore,makeCertificateStore)

data State=State {request::Object, events::IORef [Value], counts::IORef (M.Map String Int), contexts::[Ptr Word8], allocations::[Ptr Word8], handles::IORef [Handle]}
{-# NOINLINE state #-}
state :: IORef State
state=unsafePerformIO $ newIORef (error "unconfigured")
convert v=case fromJSON v of Success x->x;Error e->error e
field v k=maybe Null id (K.lookup k v)
str=toJSON::String->Value
arr=toJSON::[Value]->Value
bytes v=B.pack $ convert v
configure v=do
 allocated<-forM (convert(field v "contexts")::[Object]) $ \c->do
  let raw=bytes(field c "bytes");ty=convert(field c "encoding")::Word32
  p<-mallocBytes (4*sizeOf(nullPtr::Ptr Word8)+8)
  b<-mallocBytes (max 1 $ B.length raw)
  B.useAsCStringLen raw $ \(src,n)->copyArray b (castPtr src) n
  pokeByteOff p 0 ty
  pokeByteOff p (alignment(nullPtr::Ptr Word8)) b
  pokeByteOff p (alignment(nullPtr::Ptr Word8)+sizeOf(nullPtr::Ptr Word8)) (fromIntegral(B.length raw)::Word32)
  pure(p,b)
 ev<-newIORef [];cs<-newIORef M.empty;hs<-newIORef []
 writeIORef state (State v ev cs (map fst allocated) (concatMap (\(p,b)->[p,b]) allocated) hs)
cleanup=do
 st<-readIORef state
 mapM_ free (allocations st)
 readIORef(handles st) >>= mapM_ hClose
trace=readIORef state >>= readIORef . events >>= pure . reverse
record label xs=do
 st<-readIORef state
 modifyIORef' (events st) (arr xs :)
 n<-atomicModifyIORef' (counts st) $ \m->let n=1+M.findWithDefault 0 label m in (M.insert label n m,n)
 let fs=convert(field(request st) "failures")::[[Value]]
 forM_ fs $ \[stage,call,tag]->when (convert stage==label && convert call==n) $ case convert tag::String of
  "io"->ioError(userError "IO failure")
  "asn1"->throwIO(TypeDecodingFailed "effect")
  _->error "effect failure"
lookupValue name=do
 st<-readIORef state
 let env=convert(field(request st) "environment")::Object
 pure $ case K.lookup (Key.fromString name) env of Just (String x)->Just(T.unpack x);_->Nothing
lookupEnv name=record ("lookup:"++name) [str "lookup",str name] >> lookupValue name
getEnv name=do
 record ("get:"++name) [str "get",str name]
 lookupValue name >>= maybe (ioError(userError "missing environment variable")) pure
readCertificateStore path=do
 record ("read:"++path) [str "read",str path]
 st<-readIORef state
 let stores=convert(field(request st) "stores")::Object
 pure $ decodeStore $ field stores (Key.fromString path)
decodeStore Null=Nothing
decodeStore (String "rootError")=Just $ error "store demanded"
decodeStore (String _)=error "store result demanded"
decodeStore (Array a)=Just $ makeCertificateStore $ map cert $ V.toList a
 where cert Null=error "certificate demanded"
       cert raw=either error id $ decodeSignedCertificate $ bytes raw

data StdStream=Inherit|CreatePipe deriving (Eq,Show)
data CreateProcess=CreateProcess {command::[String],std_out::StdStream}
data ProcessHandle=ProcessHandle
proc name args=CreateProcess (name:args) Inherit
createProcess cp=do
 record "process" [str "process",toJSON(command cp),toJSON(std_out cp==CreatePipe)]
 st<-readIORef state
 dir<-getTemporaryDirectory
 (p,h)<-openBinaryTempFile dir "kotdoc-system-output-"
 modifyIORef' (handles st) (h:)
 B.hPut h $ bytes $ field(request st) "output"
 hSeek h AbsoluteSeek 0
 removeFile p
 pure (Nothing::Maybe Handle,Just h,Nothing::Maybe Handle,ProcessHandle)
waitForProcess _=do
 record "wait" [str "wait"]
 st<-readIORef state
 pure $ let n=convert(field(request st) "exit") in if n==0 then ExitSuccess else ExitFailure n

c_CertOpenSystemStore :: Ptr Word8 -> Ptr Word16 -> IO (Ptr Word8)
c_CertOpenSystemStore provider name=do
 text<-peekArray0 0 name
 record "open" [str "open",toJSON(text::[Word16]),toJSON(provider==nullPtr)]
 st<-readIORef state
 pure $ if convert(field(request st) "open") then intPtrToPtr 1 else nullPtr
c_CertCloseStore :: Ptr Word8 -> Word32 -> IO ()
c_CertCloseStore _ flags=record "close" [str "close",toJSON flags]
c_CertEnumCertificatesInStore :: Ptr Word8 -> Ptr Word8 -> IO (Ptr Word8)
c_CertEnumCertificatesInStore _ previous=do
 st<-readIORef state
 let index=if previous==nullPtr then -1 else maybe (error "bad previous context") id $ elemIndex previous (contexts st)
 record "enum" [str "enum",if index<0 then Null else toJSON index]
 pure $ if index+1>=length(contexts st) then nullPtr else contexts st!!(index+1)
