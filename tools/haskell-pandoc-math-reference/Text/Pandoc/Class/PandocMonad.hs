{-# LANGUAGE CPP, FlexibleContexts, MultiParamTypeClasses #-}
-- Byte-exact selected state/default-method definitions from pinned PandocMonad.
-- The rest of PandocMonad is not replaced or claimed implemented by this projection.
module Text.Pandoc.Class.PandocMonad where
import Control.Monad (when)
import Control.Monad.Except (MonadError, catchError, throwError)
import qualified Debug.Trace
import qualified Data.Text as T
import qualified Data.ByteString.Lazy as BL
import Network.URI (parseURI, URI(..))
import Text.Collate.Lang (Lang, parseLang)
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Error
import Text.Pandoc.Logging
import Text.Pandoc.MIME (MimeType)
import Text.Pandoc.MediaBag (MediaBag)
import qualified Text.Pandoc.MediaBag as MB
class (Monad m, MonadError PandocError m) => PandocMonad m where
  getCommonState :: m CommonState
  -- | Set the value of the 'CommonState' used by all instances
  -- of 'PandocMonad'.
  putCommonState :: CommonState -> m ()
  -- | Get the value of a specific field of 'CommonState'.
  getsCommonState :: (CommonState -> a) -> m a
  getsCommonState f = f <$> getCommonState
  -- | Modify the 'CommonState'.
  modifyCommonState :: (CommonState -> CommonState) -> m ()
  modifyCommonState f = getCommonState >>= putCommonState . f
  -- | Output a log message.
  logOutput :: LogMessage -> m ()
  -- | Output a debug message to sterr, using 'Debug.Trace.trace',
  -- if tracing is enabled.  Note: this writes to stderr even in
  -- pure instances.
  trace :: T.Text -> m ()
  trace msg = do
    tracing <- getsCommonState stTrace
    when tracing $ Debug.Trace.trace ("[trace] " ++ T.unpack msg) (return ())


setVerbosity :: PandocMonad m => Verbosity -> m ()
setVerbosity verbosity =
  modifyCommonState $ \st -> st{ stVerbosity = verbosity }

-- | Get the verbosity level.
getVerbosity :: PandocMonad m => m Verbosity
getVerbosity = getsCommonState stVerbosity

-- | Set tracing. This affects the behavior of 'trace'. If tracing
-- is not enabled, 'trace' does nothing.
setTrace :: PandocMonad m => Bool -> m ()
setTrace enabled = modifyCommonState $ \st -> st{ stTrace = enabled }

-- | Get tracing status.
getTrace :: PandocMonad m => m Bool
getTrace = getsCommonState stTrace

-- | Get the accumulated log messages (in temporal order).
getLog :: PandocMonad m => m [LogMessage]
getLog = reverse <$> getsCommonState stLog

-- | Log a message using 'logOutput'.  Note that 'logOutput' is
-- called only if the verbosity level exceeds the level of the
-- message, but the message is added to the list of log messages
-- that will be retrieved by 'getLog' regardless of its verbosity level.
report :: PandocMonad m => LogMessage -> m ()
report msg = do
  verbosity <- getsCommonState stVerbosity
  let level = messageVerbosity msg
  when (level <= verbosity) $ logOutput msg
  modifyCommonState $ \st -> st{ stLog = msg : stLog st }

-- | Run an action, but suppress the output of any log messages;
-- instead, all messages reported by @action@ are returned separately
-- and not added to the main log.
runSilently :: PandocMonad m => m a -> m (a, [LogMessage])
runSilently action = do
  -- get current settings
  origLog <- getsCommonState stLog
  origVerbosity <- getVerbosity
  let restore = modifyCommonState
        (\st -> st { stVerbosity = origVerbosity, stLog = origLog })
  -- reset log level and set verbosity to the minimum
  modifyCommonState (\st -> st { stVerbosity = ERROR, stLog = []})
  -- restore the original log and verbosity even if the action fails
  result <- action `catchError` (\e -> restore *> throwError e)
  -- get log messages reported while running `action`
  newLog <- getsCommonState stLog
  restore
  return (result, newLog)

-- | Set request header to use in HTTP requests.
setRequestHeader :: PandocMonad m
                 => T.Text  -- ^ Header name
                 -> T.Text  -- ^ Value
                 -> m ()
setRequestHeader name val = modifyCommonState $ \st ->
  st{ stRequestHeaders =
       (name, val) : filter (\(n,_) -> n /= name) (stRequestHeaders st)  }

-- | Determine whether certificate validation is disabled
setNoCheckCertificate :: PandocMonad m => Bool -> m ()
setNoCheckCertificate noCheckCertificate = modifyCommonState $ \st ->
  st{ stNoCheckCertificate = noCheckCertificate
#ifdef PANDOC_HTTP_SUPPORT
    -- discard any cached HTTP manager, since it was created with
    -- TLS settings based on the previous value of this option
    , stManager = if stNoCheckCertificate st == noCheckCertificate
                     then stManager st
                     else Nothing
#endif
    }

-- | Initialize the media bag.
setMediaBag :: PandocMonad m => MediaBag -> m ()
setMediaBag mb = modifyCommonState $ \st -> st{stMediaBag = mb}

-- | Retrieve the media bag.
getMediaBag :: PandocMonad m => m MediaBag
getMediaBag = getsCommonState stMediaBag

-- | Insert an item into the media bag.
insertMedia :: PandocMonad m => FilePath -> Maybe MimeType -> BL.ByteString -> m ()
insertMedia fp mime bs = do
  mb <- getMediaBag
  let mb' = MB.insertMedia fp mime bs mb
  setMediaBag mb'

-- | Retrieve the input filenames.
getInputFiles :: PandocMonad m => m [FilePath]
getInputFiles = getsCommonState stInputFiles

-- | Set the input filenames.
setInputFiles :: PandocMonad m => [FilePath] -> m ()
setInputFiles fs = do
  let sourceURL = case fs of
                    []    -> Nothing
                    (x:_) -> case parseURI x of
                                Just u
                                  | uriScheme u `elem` ["http:","https:"] ->
                                      Just $ show u{ uriQuery = "",
                                                     uriFragment = "" }
                                _ -> Nothing

  modifyCommonState $ \st -> st{ stInputFiles = fs
                               , stSourceURL = T.pack <$> sourceURL }

-- | Retrieve the output filename.
getOutputFile :: PandocMonad m => m (Maybe FilePath)
getOutputFile = getsCommonState stOutputFile

-- | Set the output filename.
setOutputFile :: PandocMonad m => Maybe FilePath -> m ()
setOutputFile mbf = modifyCommonState $ \st -> st{ stOutputFile = mbf }

-- | Retrieve the resource path searched by 'fetchItem'.
getResourcePath :: PandocMonad m => m [FilePath]
getResourcePath = getsCommonState stResourcePath

-- | Set the resource path searched by 'fetchItem'.
setResourcePath :: PandocMonad m => [FilePath] -> m ()
setResourcePath ps = modifyCommonState $ \st -> st{stResourcePath = ps}

-- | Retrieve the request headers to add for HTTP requests.
getRequestHeaders :: PandocMonad m => m [(T.Text, T.Text)]
getRequestHeaders = getsCommonState stRequestHeaders

-- | Set the request headers to add for HTTP requests.
setRequestHeaders :: PandocMonad m => [(T.Text, T.Text)] -> m ()
setRequestHeaders hs = modifyCommonState $ \st -> st{ stRequestHeaders = hs }

-- | Get the absolute URL or directory of first source file.
getSourceURL :: PandocMonad m => m (Maybe T.Text)
getSourceURL = getsCommonState stSourceURL



toLang :: PandocMonad m => Maybe T.Text -> m (Maybe Lang)
toLang Nothing = return Nothing
toLang (Just s) =
  case parseLang s of
       Left _ -> do
         report $ InvalidLang s
         return Nothing
       Right l -> return (Just l)



setUserDataDir :: PandocMonad m
               => Maybe FilePath
               -> m ()
setUserDataDir mbfp = modifyCommonState $ \st -> st{ stUserDataDir = mbfp }

-- | Get the user data directory from common state.
getUserDataDir :: PandocMonad m
               => m (Maybe FilePath)
getUserDataDir = getsCommonState stUserDataDir