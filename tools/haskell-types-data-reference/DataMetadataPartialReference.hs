-- Original Data metadata String prefix observations; no Kotlin-generated expectations.
import Data.Data
import Control.Exception
import Data.Aeson(encode)
import qualified Data.ByteString.Lazy.Char8 as B
probe name value=do
 result<-try(evaluate(length value))::IO(Either SomeException Int)
 B.putStrLn(encode(name,either(const "error")(const value)result))
main=do
 let dt=mkDataType ('D':error "name tail") []
     c=mkConstrTag dt ('C':error "constructor tail") 1 [('f':error "field tail")] (error "fixity")
 probe "datatype-prefix" (take 1(dataTypeName dt))
 probe "constructor-prefix" (take 1(showConstr c))
 probe "field-prefix" (take 1(head(constrFields c)))
 probe "constr-type-prefix" (take 1(dataTypeName(constrType c)))
 probe "index-ignores-names" (show(constrIndex c))
 probe "fixity-demand" (show(constrFixity c))
 let bad=mkIntType (error "datatype name")
 result<-try(evaluate(dataTypeConstrs bad))::IO(Either ErrorCall [Constr])
 case result of
  Left e->do
   probe "error-message-prefix" (take 10(displayException e))
   probe "error-message-full" (displayException e)
  Right _->error "expected failure"
