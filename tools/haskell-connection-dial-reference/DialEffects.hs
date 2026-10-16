{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module DialEffects where
import Control.Exception
import Data.Aeson
import Data.Aeson.Types
import qualified Data.Aeson.Key as K
import Data.IORef
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Network.Socket as N
import System.IO.Unsafe
field :: FromJSON a => Value -> String -> a
field v k = maybe (error $ "field " ++ k) id $ optional v k
optional :: FromJSON a => Value -> String -> Maybe a
optional v k = parseMaybe (withObject "object" (.: K.fromString k)) v
value v k = field v k :: Value
array v = case fromJSON v of Success a -> a; Error e -> error e
string v = case v of String _ -> field (object ["x" .= v]) "x"; _ -> map toEnum $ array v
bytes v = B.pack $ array v
{-# NOINLINE config #-}
config :: IORef Value
config = unsafePerformIO $ newIORef Null
{-# NOINLINE packets #-}
packets :: IORef [Value]
packets = unsafePerformIO $ newIORef []
{-# NOINLINE logs #-}
logs :: IORef [Value]
logs = unsafePerformIO $ newIORef []
{-# NOINLINE sockets #-}
sockets :: IORef [(N.Socket,Value)]
sockets = unsafePerformIO $ newIORef []
emit v = evaluate (L.length $ encode v) >> modifyIORef' logs (v:)
raise :: String -> IO a
raise tag | take 3 tag == "io:" = ioError $ userError tag
          | otherwise = throwIO $ AssertionFailed tag
check v k = maybe (pure ()) raise (optional v k)
reset v = do
 writeIORef config v; writeIORef packets $ maybe [] id $ optional v "chunks"
 writeIORef logs []; writeIORef sockets []
cleanup = do
 xs <- readIORef sockets
 mapM_ (\(s,_) -> N.close s `catch` \(_::IOException) -> pure ()) xs
descriptor s = do
 xs <- readIORef sockets
 pure $ maybe (object ["id" .= (-1::Int)]) snd $ case filter ((==s).fst) xs of [] -> Nothing; x:_ -> Just x
recv :: N.Socket -> Int -> IO B.ByteString
recv _ n = do
 emit $ object ["event" .= ("recv"::String),"maximum" .= n]
 next <- atomicModifyIORef' packets $ \xs -> case xs of [] -> ([],toJSON ([]::[Int])); x:rest -> (rest,x)
 case next of String _ -> raise $ string next; _ -> pure $ bytes next
sendAll :: N.Socket -> B.ByteString -> IO ()
sendAll _ b = emit $ object ["event" .= ("send"::String),"bytes" .= B.unpack b]
