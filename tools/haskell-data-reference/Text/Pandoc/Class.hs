{-# LANGUAGE ScopedTypeVariables, CPP, FlexibleContexts, MultiParamTypeClasses, BangPatterns, OverloadedStrings #-}
-- Exact selected PandocMonad definitions; IO file selection is an effect boundary.
module Text.Pandoc.Class where
import Control.Monad (when)
import Control.Monad.Except (MonadError,throwError)
import qualified Debug.Trace
import qualified Data.Text as T
import qualified Data.Text.Encoding as TSE
import qualified Data.Text.Encoding.Error as TSE
import qualified Data.ByteString as B
import Data.Word (Word8)
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Error
import Text.Pandoc.Logging
import Text.Pandoc.Shared (tshow,safeRead,makeCanonical)
import Data.Time.Clock (UTCTime)
import Data.Time.Clock.POSIX (POSIXTime,posixSecondsToUTCTime,utcTimeToPOSIXSeconds)
import qualified Data.ByteString.Lazy as BL
import System.FilePath (isRelative,splitDirectories)
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

  -- The original readDataFile operation is provided by the observing runtime.
  readDataFileEffect :: FilePath -> m B.ByteString
  readFileStrict :: FilePath -> m B.ByteString
  readFileLazy :: FilePath -> m BL.ByteString
  fileExists :: FilePath -> m Bool
  getDataFileName :: FilePath -> m FilePath
  lookupEnv :: T.Text -> m (Maybe T.Text)
  getCurrentTime :: m UTCTime

report :: PandocMonad m => LogMessage -> m ()
report msg = do
  verbosity <- getsCommonState stVerbosity
  let level = messageVerbosity msg
  when (level <= verbosity) $ logOutput msg
  modifyCommonState $ \st -> st{ stLog = msg : stLog st }

getUserDataDir :: PandocMonad m
               => m (Maybe FilePath)
getUserDataDir = getsCommonState stUserDataDir

toTextM :: PandocMonad m => FilePath -> B.ByteString -> m T.Text
toTextM fp bs =
  case TSE.decodeUtf8' . filterCRs . dropBOM $ bs of
    Left (TSE.DecodeError _ (Just w)) ->
      case findDecodingError bs of
        Just (offset, w') ->
          throwError $ PandocUTF8DecodingError (T.pack fp) offset w'
        Nothing -> throwError $ PandocUTF8DecodingError (T.pack fp) 0 w
    Left e -> throwError $ PandocAppError (tshow e)
    Right t -> return t
 where
   dropBOM bs' =
     if "\xEF\xBB\xBF" `B.isPrefixOf` bs'
        then B.drop 3 bs'
        else bs'
   -- Only allocate a filtered copy if a CR is actually present;
   -- B.elem compiles to a fast memchr.
   filterCRs bs' = if 13 `B.elem` bs'
                      then B.filter (/=13) bs'
                      else bs'

-- Find the offset and value of the first byte at which UTF-8 decoding
-- fails (RFC 3629).  Used to give an accurate position in decoding
-- error messages.  (The BOM and CR bytes stripped before decoding are
-- themselves valid UTF-8, so scanning the unstripped input finds the
-- same error, at its offset in the original file.)
findDecodingError :: B.ByteString -> Maybe (Int, Word8)
findDecodingError = go 0
 where
  go !i bs = case B.uncons bs of
    Nothing -> Nothing
    Just (w, rest)
      | w < 0x80  -> go (i + 1) rest
      | w < 0xC2  -> Just (i, w)  -- continuation byte or overlong lead
      | w == 0xE0 -> cont i w rest [(0xA0,0xBF),(0x80,0xBF)]
      | w == 0xED -> cont i w rest [(0x80,0x9F),(0x80,0xBF)]  -- no surrogates
      | w < 0xE0  -> cont i w rest [(0x80,0xBF)]
      | w <  0xF0 -> cont i w rest [(0x80,0xBF),(0x80,0xBF)]
      | w == 0xF0 -> cont i w rest [(0x90,0xBF),(0x80,0xBF),(0x80,0xBF)]
      | w == 0xF4 -> cont i w rest [(0x80,0x8F),(0x80,0xBF),(0x80,0xBF)]
      | w <  0xF4 -> cont i w rest [(0x80,0xBF),(0x80,0xBF),(0x80,0xBF)]
      | otherwise -> Just (i, w)  -- above U+10FFFF
  -- check that the bytes following the lead byte w at offset i fall
  -- into the given ranges; report the first byte that does not
  cont i w = go' (i + 1)
   where
    go' !j rest [] = go j rest
    go' !j rest ((lo,hi):ranges) =
      case B.uncons rest of
        Just (b, rest')
          | b >= lo && b <= hi -> go' (j + 1) rest' ranges
          | otherwise          -> Just (j, b)
        -- input ends in the middle of a sequence: report the lead byte
        Nothing -> Just (i, w)

getTimestamp :: PandocMonad m => m UTCTime
getTimestamp = do
  mbSourceDateEpoch <- lookupEnv "SOURCE_DATE_EPOCH"
  case mbSourceDateEpoch >>= safeRead of
    Just (epoch :: Integer) ->
      return $ posixSecondsToUTCTime $ fromIntegral epoch
    Nothing -> getCurrentTime

-- | Get the POSIX time. If @SOURCE_DATE_EPOCH@ is set to a unix time,
-- it is used instead of the current time.
getPOSIXTime :: PandocMonad m => m POSIXTime
getPOSIXTime = utcTimeToPOSIXSeconds <$> getTimestamp


isRelativeToParentDir :: FilePath -> Bool
isRelativeToParentDir fname =
  case splitDirectories (makeCanonical fname) of
    "..":_ -> True
    _      -> False

-- | Returns possible user data directory if the file path refers to a file or
-- subdirectory within it.
checkUserDataDir :: PandocMonad m => FilePath -> m (Maybe FilePath)
checkUserDataDir fname =
  if isRelative fname && not (isRelativeToParentDir fname)
     then getUserDataDir
     else return Nothing

