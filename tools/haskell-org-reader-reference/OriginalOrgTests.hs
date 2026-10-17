{-# LANGUAGE OverloadedStrings #-}
{- |
   Module      : Tests.Shared
   Copyright   : © 2014-2024 Albert Krewinkel
   License     : GNU GPL, version 2 or above

   Maintainer  : Albert Krewinkel <albert+pandoc@tarleb.com>
   Stability   : alpha
   Portability : portable

Tests of the org reader.
-}
module OriginalOrgTests (tests) where

import OracleTree (TestTree, testGroup)
import qualified OriginalOrgTests.Block as Block
import qualified OriginalOrgTests.Directive as Directive
import qualified OriginalOrgTests.Inline as Inline
import qualified OriginalOrgTests.Meta as Meta

tests :: [TestTree]
tests =
  [ testGroup "Inlines" Inline.tests
  , testGroup "Basic Blocks" Block.tests
  , testGroup "Meta Information" Meta.tests
  , testGroup "Directives" Directive.tests
  ]
