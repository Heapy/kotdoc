{-# LANGUAGE OverloadedStrings #-}
module ExecutionEffects where
import Control.Monad (when)
import Data.IORef
import System.IO.Unsafe
import Data.Time hiding (getCurrentTime)
import Data.Time.Clock.POSIX
import Data.Aeson
import Network.HTTP.Client.Internal
import qualified Network.HTTP.Client.Internal as R
import Data.KeyedPool

data Effects = Effects {clock :: IO UTCTime, emit :: Value -> IO (), expires :: Bool}
state :: IORef Effects
{-# NOINLINE state #-}
state=unsafePerformIO $ newIORef $ Effects (pure $ posixSecondsToUTCTime 0) (const $ pure ()) False
getCurrentTime :: IO UTCTime
getCurrentTime=readIORef state >>= clock
timeout :: Int -> IO a -> IO (Maybe a)
timeout n action=do
 e<-readIORef state
 emit e $ object ["event" .= ("acquire-timeout"::String),"microseconds" .= n]
 if expires e then pure Nothing else Just <$> action
getResponse :: Maybe MaxHeaderLength -> Maybe MaxNumberHeaders -> Maybe Int -> Request -> Managed Connection -> Maybe (IO ()) -> IO (Response BodyReader)
getResponse ml mh budget req conn cont=do
 e<-readIORef state
 case budget of {Just n -> emit e $ object ["event" .= ("status-timeout"::String),"microseconds" .= n];_ -> pure ()}
 -- Replace only the header timer effect; parsing/body/release stay original.
 R.getResponse ml mh Nothing req conn cont
