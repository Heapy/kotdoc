module OriginalSocketFactories where
import qualified SocketEffects as NS
import OriginalSocketConnection (openSocketConnection, openSocketConnectionSize)
import Network.HTTP.Client.Internal (Connection)

rawConnectionModifySocket :: (NS.Socket -> IO ())
                          -> IO (Maybe NS.HostAddress -> String -> Int -> IO Connection)
rawConnectionModifySocket = return . openSocketConnection

-- | Same as @rawConnectionModifySocket@, but also takes in a chunk size.
--
-- @since 0.5.2
rawConnectionModifySocketSize :: (NS.Socket -> IO ())
                              -> IO (Int -> Maybe NS.HostAddress -> String -> Int -> IO Connection)
rawConnectionModifySocketSize = return . openSocketConnectionSize


