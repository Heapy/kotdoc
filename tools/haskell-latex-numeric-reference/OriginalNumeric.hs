{-# LANGUAGE OverloadedStrings #-}
-- Exact pure declarations from pinned LaTeX.Parsing and Shared. GPL-2.0-or-later. Copyright (C) 2006-2024 John MacFarlane.
module OriginalNumeric where
import Control.Monad (MonadPlus,mzero,guard)
import Data.Char (isSpace,isLetter,isDigit)
import Data.Text (Text)
import qualified Data.Text as T
import Data.List (dropWhileEnd,isSuffixOf)
import Numeric (showEFloat,showFFloat)

safeRead :: (MonadPlus m, Read a) => T.Text -> m a
safeRead = safeStrRead . T.unpack

safeStrRead :: (MonadPlus m, Read a) => String -> m a
safeStrRead s = case reads s of
                  (d,x):_
                    | all isSpace x -> return d
                  _                 -> mzero

evalInteval :: Text -> Maybe Text
evalInteval t = do
  (n, rest) <- pIntExpr t
  guard $ T.null (skipWs rest)
  pure $ T.pack (show n)
 where
  pIntExpr s0 = pIntTerm s0 >>= addLoop
  addLoop (acc, s) =
    case T.uncons (skipWs s) of
      Just ('+', s') -> do (y, s'') <- pIntTerm s'
                           addLoop (acc + y, s'')
      Just ('-', s') -> do (y, s'') <- pIntTerm s'
                           addLoop (acc - y, s'')
      _ -> Just (acc, s)
  pIntTerm s0 = pIntFactor s0 >>= mulLoop
  mulLoop (acc, s) =
    case T.uncons (skipWs s) of
      Just ('*', s') -> do (y, s'') <- pIntFactor s'
                           mulLoop (acc * y, s'')
      Just ('/', s') -> do (y, s'') <- pIntFactor s'
                           guard $ y /= 0
                           mulLoop (divRound acc y, s'')
      _ -> Just (acc, s)
  pIntFactor s0 =
    case T.uncons (skipWs s0) of
      Just ('-', s) -> do (x, s') <- pIntFactor s
                          pure (negate x, s')
      Just ('+', s) -> pIntFactor s
      Just ('(', s) -> do
        (x, s') <- pIntExpr s
        case T.uncons (skipWs s') of
          Just (')', s'') -> pure (x, s'')
          _ -> Nothing
      Just (c, _) | isDigit c ->
        let (ds, s) = T.span isDigit (skipWs s0)
        in do n <- safeRead ds
              pure (n :: Integer, s)
      _ -> Nothing
  divRound a b =
    let (q, r) = a `quotRem` b
    in if 2 * abs r >= abs b
          then q + signum a * signum b
          else q

-- | Evaluate a floating point expression (a practical subset of
-- @\fpeval@): @+ - * / ^ ( )@ (also @**@ for @^@) and decimal
-- literals.
evalFpeval :: Text -> Maybe Text
evalFpeval t0 = do
  let t = T.replace "**" "^" t0
  (x, rest) <- pFpExpr t
  guard $ T.null (skipWs rest)
  guard $ not (isNaN x || isInfinite x)
  pure $ formatFp x
 where
  pFpExpr s0 = pFpTerm s0 >>= addLoop
  addLoop (acc, s) =
    case T.uncons (skipWs s) of
      Just ('+', s') -> do (y, s'') <- pFpTerm s'
                           addLoop (acc + y, s'')
      Just ('-', s') -> do (y, s'') <- pFpTerm s'
                           addLoop (acc - y, s'')
      _ -> Just (acc, s)
  pFpTerm s0 = pFpPow s0 >>= mulLoop
  mulLoop (acc, s) =
    case T.uncons (skipWs s) of
      Just ('*', s') -> do (y, s'') <- pFpPow s'
                           mulLoop (acc * y, s'')
      Just ('/', s') -> do (y, s'') <- pFpPow s'
                           mulLoop (acc / y, s'')
      _ -> Just (acc, s)
  pFpPow s0 = do
    (x, s) <- pFpFactor s0
    case T.uncons (skipWs s) of
      Just ('^', s') -> do (y, s'') <- pFpPow s'  -- right-associative
                           pure (x ** y, s'')
      _ -> pure (x, s)
  pFpFactor s0 =
    case T.uncons (skipWs s0) of
      Just ('-', s) -> do (x, s') <- pFpFactor s
                          pure (negate x, s')
      Just ('+', s) -> pFpFactor s
      Just ('(', s) -> do
        (x, s') <- pFpExpr s
        case T.uncons (skipWs s') of
          Just (')', s'') -> pure (x, s'')
          _ -> Nothing
      Just (c, _) | isLetter c ->
        let (name, s1) = T.span isLetter (skipWs s0)
        in case name of
             "pi"  -> pure (pi, s1)
             "deg" -> pure (pi / 180, s1)  -- one degree in radians
             _ ->
               case T.uncons (skipWs s1) of
                 Just ('(', s2) -> do
                   (args, s3) <- pFpArgs s2
                   x <- applyFn name args
                   pure (x, s3)
                 _ -> do
                   -- prefix application without parentheses,
                   -- e.g. "sqrt 2":
                   (y, s2) <- pFpFactor s1
                   x <- applyFn name [y]
                   pure (x, s2)
      Just (c, _) | isDigit c || c == '.' ->
        let s = skipWs s0
            (ds, s') = T.span (\d -> isDigit d || d == '.') s
            (expt, s'') = case T.uncons s' of
                            Just (e, r) | e == 'e' || e == 'E' ->
                              let (sign, r') =
                                    case T.uncons r of
                                      Just (sg, rr) | sg == '+' || sg == '-'
                                        -> (T.singleton sg, rr)
                                      _ -> ("", r)
                                  (eds, r'') = T.span isDigit r'
                              in if T.null eds
                                    then ("", s')
                                    else ("e" <> sign <> eds, r'')
                            _ -> ("", s')
        in do x <- safeRead (fixup ds <> expt)
              pure (x :: Double, s'')
      _ -> Nothing
  fixup ds -- make the literal readable for Haskell's 'read'
    | "." `T.isPrefixOf` ds = "0" <> fixup' ds
    | otherwise = fixup' ds
  fixup' ds
    | "." `T.isSuffixOf` ds = ds <> "0"
    | otherwise = ds
  -- comma-separated function arguments, ending with ')':
  pFpArgs s0 = do
    (x, s) <- pFpExpr s0
    case T.uncons (skipWs s) of
      Just (',', s') -> do (xs, s'') <- pFpArgs s'
                           pure (x : xs, s'')
      Just (')', s') -> pure ([x], s')
      _ -> Nothing
  applyFn name args =
    case (lookup name unaryFns, args) of
      (Just f, [x]) -> Just (f x)
      _ ->
        case (name, args) of
          ("max", _:_) -> Just (maximum args)
          ("min", _:_) -> Just (minimum args)
          ("atan", [x, y]) -> Just (atan2 x y)
          ("atand", [x, y]) -> Just (unrad (atan2 x y))
          ("round", _) -> rounder (fromInteger . round) args
          ("floor", _) -> rounder (fromInteger . floor) args
          ("ceil", _) -> rounder (fromInteger . ceiling) args
          ("trunc", _) -> rounder (fromInteger . truncate) args
          _ -> Nothing
  -- rounding functions take an optional number of decimal places:
  rounder f [x] = Just (f x)
  rounder f [x, n] | n == fromInteger (round n) =
    let m = 10 ^^ (round n :: Integer)
        -- round to 16 significant digits first (like l3fp), so
        -- that e.g. round(2.345,2) gives 2.34, not 2.35:
        y = read (showEFloat (Just 15) (x * m) "") :: Double
    in Just (f y / m)
  rounder _ _ = Nothing
  rad x = x * pi / 180
  unrad x = x * 180 / pi
  unaryFns :: [(Text, Double -> Double)]
  unaryFns =
    [ ("abs", abs), ("sign", signum), ("sqrt", sqrt)
    , ("exp", exp), ("ln", log)
    , ("fact", \x -> if x >= 0 && x == fromInteger (round x) && x < 171
                        then fromInteger (product [1 .. round x])
                        else 0 / 0)
    , ("sin", sin), ("cos", cos), ("tan", tan)
    , ("cot", recip . tan), ("sec", recip . cos), ("csc", recip . sin)
    , ("asin", asin), ("acos", acos), ("atan", atan)
    , ("acot", atan . recip), ("asec", acos . recip), ("acsc", asin . recip)
    , ("sind", sin . rad), ("cosd", cos . rad), ("tand", tan . rad)
    , ("cotd", recip . tan . rad), ("secd", recip . cos . rad)
    , ("cscd", recip . sin . rad)
    , ("asind", unrad . asin), ("acosd", unrad . acos)
    , ("atand", unrad . atan), ("acotd", unrad . atan . recip)
    , ("asecd", unrad . acos . recip), ("acscd", unrad . asin . recip)
    ]

formatFp :: Double -> Text
formatFp x0
  | x == fromInteger r && abs x < 1e16 = T.pack (show r)
  | otherwise = T.pack (trimZeros (showFFloat Nothing x ""))
 where
  -- l3fp computes with 16 significant decimal digits; round to
  -- that precision so that e.g. 0.1 + 0.2 yields 0.3.
  x = read (showEFloat (Just 15) x0 "") :: Double
  r = round x
  trimZeros s
    | '.' `elem` s = case dropWhileEnd (== '0') s of
                       s' | "." `isSuffixOf` s' -> take (length s' - 1) s'
                          | otherwise -> s'
    | otherwise = s

skipWs :: Text -> Text
skipWs = T.dropWhile (\c -> c == ' ' || c == '\t' || c == '\n' || c == '\r')
