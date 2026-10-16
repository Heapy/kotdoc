{- |
Module      : Network.Socks5.LowLevel
License     : BSD-style
Copyright   : (c) 2010-2019 Vincent Hanquez <vincent@snarc.org>
Stability   : experimental
Portability : unknown

Low level types and functions for interacting with a SOCKS server.
-}

module OriginalSocksLowlevel
  ( socksListen
    -- * Low level types and functions
  , module OriginalSocksWire
  , module OriginalSocksCommand
  ) where

import           Data.Functor ( void )
import           Network.Socket ( Socket )
import           OriginalSocksCommand
import           OriginalSocksTypes ( SocksMethod (..) )
import           OriginalSocksWire

-- | For the specified socket, wait for a SOCKS Hello, send a SOCKS Hello
-- response (specifying no authentification method), and wait for a SOCKS
-- request.
socksListen ::
     Socket
     -- ^ The socket to use.
  -> IO SocksRequest
socksListen sock = do
  void (waitSerialized sock :: IO SocksHello)
  sendSerialized sock (SocksHelloResponse SocksMethodNone)
  waitSerialized sock
