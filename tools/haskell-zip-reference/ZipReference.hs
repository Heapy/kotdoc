{-# LANGUAGE OverloadedStrings #-}
module Main where
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Codec.Archive.Zip as Z
import qualified Codec.Archive.ZipWindows as W
import Data.Char (chr,ord)
import Data.Binary (decodeOrFail)
import Crypto.Hash (hashlazy,Digest,SHA256)
import System.IO
import Control.Monad (unless)
import qualified Control.Exception as E
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success a->a;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
chars=map(chr.as).arr
bytes=BL.pack.map as.arr
opt _ Null=Nothing
opt f v=Just(f v)
shape e=toJSON[toJSON(map ord(Z.eRelativePath e)),toJSON(show(Z.eCompressionMethod e)),case Z.eEncryptionMethod e of Z.NoEncryption->Null;Z.PKWAREEncryption c->toJSON c,
 toJSON(show(Z.eLastModified e)),toJSON(Z.eCRC32 e),toJSON(Z.eCompressedSize e),toJSON(Z.eUncompressedSize e),toJSON(BL.unpack(Z.eExtraField e)),toJSON(BL.unpack(Z.eFileComment e)),toJSON(Z.eVersionMadeBy e),toJSON(Z.eInternalFileAttributes e),toJSON(Z.eExternalFileAttributes e),toJSON(BL.unpack(Z.eCompressedData e))]
entry v=case arr v of
 [p,m,en,t,c,cs,us,x,fc,vm,ia,ea,d]->Z.Entry(chars p)(read(as m))(case en of Null->Z.NoEncryption;_->Z.PKWAREEncryption(as en))(read(as t))(as c)(as cs)(as us)(bytes x)(bytes fc)(as vm)(as ia)(as ea)(bytes d)
 _->error "entry"
archive v=case arr v of [es,s,c]->Z.Archive(map entry(arr es))(opt bytes s)(bytes c);_->error "archive"
arch a=toJSON[toJSON(map shape(Z.zEntries a)),maybe Null (toJSON.BL.unpack)(Z.zSignature a),toJSON(BL.unpack(Z.zComment a))]
win e=W.Entry(Z.eRelativePath e)(case Z.eCompressionMethod e of Z.Deflate->W.Deflate;_->W.NoCompression)(case Z.eEncryptionMethod e of Z.NoEncryption->W.NoEncryption;Z.PKWAREEncryption c->W.PKWAREEncryption c)(Z.eLastModified e)(Z.eCRC32 e)(Z.eCompressedSize e)(Z.eUncompressedSize e)(Z.eExtraField e)(Z.eFileComment e)(Z.eVersionMadeBy e)(Z.eInternalFileAttributes e)(Z.eExternalFileAttributes e)(Z.eCompressedData e)
pos e=Z.Entry(W.eRelativePath e)(case W.eCompressionMethod e of W.Deflate->Z.Deflate;_->Z.NoCompression)(case W.eEncryptionMethod e of W.NoEncryption->Z.NoEncryption;W.PKWAREEncryption c->Z.PKWAREEncryption c)(W.eLastModified e)(W.eCRC32 e)(W.eCompressedSize e)(W.eUncompressedSize e)(W.eExtraField e)(W.eFileComment e)(W.eVersionMadeBy e)(W.eInternalFileAttributes e)(W.eExternalFileAttributes e)(W.eCompressedData e)
winA a=W.Archive(map win(Z.zEntries a))(Z.zSignature a)(Z.zComment a)
posA a=Z.Archive(map pos(W.zEntries a))(W.zSignature a)(W.zComment a)
sha b=show(hashlazy b::Digest SHA256)
shapeHash e=case shape e of
 Array fields->toJSON(V.toList(V.take 12 fields)++[toJSON(sha(Z.eCompressedData e))]);_->error "shape"
contentHash e=do
 r<-E.try(E.evaluate(sha(Z.fromEntry e)))::IO(Either E.SomeException String)
 pure(either (\e->object["exception" .= takeWhile(/='\n')(show e)]) (\h->object["sha" .= h]) r)
response req=case arr req of
 [String "paths",platform,start,end,drive]->pure(toJSON[map ord(path n)|n<-[as start..as end]]) where
  path n=let p=if as drive then chr n : ":/dir/file" else "prefix/"++[chr n]++"/file"
         in if as platform then W.eRelativePath(W.toEntry p 0 BL.empty) else Z.eRelativePath(Z.toEntry p 0 BL.empty)
 [String "source",path]->do
  b<-BL.readFile(as path)
  case decodeOrFail b of
   Left(_,_,e)->pure(object["error" .= e])
   Right(_,n,a)->do
    hs<-mapM contentHash(Z.zEntries a)
    pure(object["entries" .= map shapeHash(Z.zEntries a),"signature" .= fmap BL.unpack(Z.zSignature a),"comment" .= BL.unpack(Z.zComment a),"consumed" .= n,"encodedSha" .= sha(Z.fromArchive a),"contents" .= hs])
 [String "entry",platform,p,t,b]->pure(shape(if as platform then pos(W.toEntry(chars p)(read(as t))(bytes b)) else Z.toEntry(chars p)(read(as t))(bytes b)))
 [String "encode",platform,a]->pure(toJSON(BL.unpack(if as platform then W.fromArchive(winA(archive a)) else Z.fromArchive(archive a))))
 [String "decode",b]->pure(case decodeOrFail(bytes b) of Left(_,_,e)->object["error" .= e];Right(_,n,a)->object["archive" .= arch a,"consumed" .= n])
 [String "contents",e]->pure(toJSON(BL.unpack(Z.fromEntry(entry e))))
 [String "decrypt",p,e]->pure(maybe Null (toJSON.BL.unpack)(Z.fromEncryptedEntry(chars p)(entry e)))
 [String "symlink",e]->pure(toJSON[Bool(Z.isEntrySymbolicLink(entry e)),toJSON(toInteger(Z.entryCMode(entry e))),maybe Null (toJSON.map ord)(Z.symbolicLinkEntryTarget(entry e))])
 [String "ops",platform,a,ops]->pure(arch(foldl step (archive a)(arr ops))) where
  step a v=case arr v of
   [String "add",e]->if as platform then posA(W.addEntryToArchive(win(entry e))(winA a)) else Z.addEntryToArchive(entry e)a
   [String "delete",p]->if as platform then posA(W.deleteEntryFromArchive(chars p)(winA a)) else Z.deleteEntryFromArchive(chars p)a
   _->error "op"
 [String "find",platform,a,p]->pure(maybe Null shape(if as platform then fmap pos(W.findEntryByPath(chars p)(winA(archive a))) else Z.findEntryByPath(chars p)(archive a)))
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-BS.getLine
   result<-E.try(response(either error id(eitherDecode(BL.fromStrict line))) >>= E.evaluate . BL.toStrict . encode)::IO(Either E.SomeException BS.ByteString)
   case result of
    Right b->BS.putStr b>>BS.putStr "\n"
    Left e->C.putStrLn(encode(object["exception" .= takeWhile (/='\n')(show e)]))
   hFlush stdout;loop
