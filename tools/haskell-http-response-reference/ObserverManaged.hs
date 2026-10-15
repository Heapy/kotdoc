-- Only the pool effect boundary is replaced in the verbatim Response observer.
module ObserverManaged where
data Reuse = Reuse | DontReuse deriving (Show,Eq)
data Managed a = Managed {managedResource :: a, managedRelease :: Reuse -> IO (), keepAlive :: IO ()}
