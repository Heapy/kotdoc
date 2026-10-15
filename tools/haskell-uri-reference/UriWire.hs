{-# LANGUAGE OverloadedStrings #-}
module UriWire where
import Data.Aeson
import Data.Char(chr,ord)
import qualified Data.Vector as V
import qualified Network.URI as N
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr(Array x)=V.toList x
arr _=error "array"
raw=map(chr.as).arr
rv=toJSON.map ord
uv u=toJSON[rv(N.uriScheme u),maybe Null av(N.uriAuthority u),rv(N.uriPath u),rv(N.uriQuery u),rv(N.uriFragment u)]
av a=toJSON[rv(N.uriUserInfo a),rv(N.uriRegName a),rv(N.uriPort a)]
uri v=case arr v of [s,a,p,q,f]->N.URI(raw s)(if a==Null then Nothing else Just(auth a))(raw p)(raw q)(raw f);_->error "uri"
auth v=case arr v of [u,h,p]->N.URIAuth(raw u)(raw h)(raw p);_->error "auth"
maybeUri=maybe Null uv
request tag args=toJSON(String tag:args)
