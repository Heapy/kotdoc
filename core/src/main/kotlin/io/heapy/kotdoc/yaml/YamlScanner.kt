// Complete libyaml 0.2.5 scanner.c algorithm; © Kirill Simonov and Ingy döt Net.
// MIT; complete original notice: licenses/Libyaml-MIT.txt.
package io.heapy.kotdoc.yaml

import java.io.ByteArrayOutputStream

/** Streaming scanner, with original delayed simple-key insertion and positions. */
class YamlScanner(private val reader: YamlReader) {
    var mark = YamlMark()
        private set
    var failure: YamlScanFailure? = null
        private set
    var tokensParsed: ULong = 0u
        private set
    var flowLevel: Int = 0
        private set
    var indent: Int = 0
        private set
    var simpleKeyAllowed: Boolean = false
        private set
    private var started = false
    private var ended = false
    internal val streamEndProduced: Boolean get() = ended
    private var available = false
    internal val tokenAvailable: Boolean get() = available
    private val queue = ArrayList<YamlToken>()
    private var head = 0
    private val indents = ArrayList<Int>()
    private data class SimpleKey(var possible: Boolean = false, val required: Boolean = false,
        val number: ULong = 0u, val mark: YamlMark = YamlMark())
    private val keys = ArrayList<SimpleKey>()
    internal data class KeyState(val possible: Boolean, val required: Boolean, val number: ULong, val mark: YamlMark)
    internal fun keyStates(): List<KeyState> = keys.map { KeyState(it.possible,it.required,it.number,it.mark) }
    internal fun queuedTokens(): List<YamlToken> = queue.subList(head,queue.size).toList()
    private class Stop : RuntimeException(null, null, false, false)

    fun next(): YamlScanResult {
        if (ended || failure != null) return YamlScanResult.End
        val token = peekForParser() ?: return YamlScanResult.Failure(checkNotNull(failure))
        consumeForParser()
        return YamlScanResult.Token(token)
    }
    /** PEEK_TOKEN must not advance tokensParsed, the queue, or stream-end state. */
    internal fun peekForParser(): YamlToken? {
        if (failure != null) return null
        if (available) return queue[head]
        return try {
            while (true) {
                var more = head == queue.size
                if (!more) {
                    staleKeys()
                    more = keys.any { it.possible && it.number == tokensParsed }
                }
                if (!more) break
                fetch()
            }
            available = true
            queue[head]
        } catch (_: Stop) { null }
    }
    internal fun consumeForParser() {
        check(available)
        val token = queue[head++]
        available = false
        tokensParsed++
        if (token.type == YamlTokenType.StreamEnd) ended = true
        if (head > 1024 && head * 2 > queue.size) { queue.subList(0, head).clear(); head = 0 }
    }
    private fun fail(context: String?, start: YamlMark, problem: String): Nothing {
        failure = YamlScanFailure(3, problem, context, mark, start)
        throw Stop()
    }
    private fun memory(): Nothing { failure = YamlScanFailure(1, null, null); throw Stop() }
    private fun cache(count: Int) {
        if (reader.unread < count && !reader.ensure(count)) {
            val e = checkNotNull(reader.failure)
            failure = YamlScanFailure(2, e.problem, null, readerFailure = e)
            throw Stop()
        }
    }
    private fun b(i: Int = 0) = reader.peekByte(i)
    private fun c(ch: Char, i: Int = 0) = b(i) == ch.code
    private fun blank(i: Int = 0) = c(' ', i) || c('\t', i)
    private fun br(i: Int = 0): Boolean = c('\r',i) || c('\n',i) ||
        b(i) == 0xc2 && b(i+1) == 0x85 || b(i) == 0xe2 && b(i+1) == 0x80 && b(i+2) in 0xa8..0xa9
    private fun blankZ(i: Int = 0) = blank(i) || br(i) || b(i) == 0
    private fun breakZ() = br() || b() == 0
    private fun alpha(i: Int = 0) = b(i) in 48..57 || b(i) in 65..90 || b(i) in 97..122 || b(i) == 95 || b(i) == 45
    private fun digit() = b() in 48..57
    private fun hex(i: Int) = b(i) in 48..57 || b(i) in 65..70 || b(i) in 97..102
    private fun hexValue(i: Int) = when (val v = b(i)) { in 65..70 -> v-55; in 97..102 -> v-87; else -> v-48 }
    private fun width() = when { b() < 128 -> 1; b() < 224 -> 2; b() < 240 -> 3; else -> 4 }
    private fun skip() { reader.consume(1); mark = mark.copy(index = mark.index+1u, column = mark.column+1u) }
    private fun read(out: ByteArrayOutputStream) {
        repeat(width()) { out.write(b(it)) }; skip()
    }
    private fun line(out: ByteArrayOutputStream? = null) {
        val count = if (c('\r') && c('\n',1)) 2 else 1
        when {
            c('\r') || c('\n') || b() == 0xc2 && b(1) == 0x85 -> out?.write(10)
            b() == 0xe2 && b(1) == 0x80 && b(2) in 0xa8..0xa9 -> repeat(3) { out?.write(b(it)) }
            else -> return
        }
        reader.consume(count)
        mark = YamlMark(mark.index+count.toULong(), mark.line+1u, 0u)
    }
    private fun docIndicator() = mark.column == 0uL &&
        (c('-') && c('-',1) && c('-',2) || c('.') && c('.',1) && c('.',2)) && blankZ(3)
    private fun enqueue(type: YamlTokenType, start: YamlMark = mark, end: YamlMark = mark) { queue.add(YamlToken(type,start,end)) }
    private fun staleKeys() {
        for (key in keys) if (key.possible && (key.mark.line < mark.line || key.mark.index+1024u < mark.index)) {
            if (key.required) fail("while scanning a simple key",key.mark,"could not find expected ':'")
            key.possible = false
        }
    }
    private fun removeKey() {
        val key = keys.last()
        if (key.possible && key.required) fail("while scanning a simple key",key.mark,"could not find expected ':'")
        key.possible = false
    }
    private fun saveKey() {
        val required = flowLevel == 0 && indent.toLong() == mark.column.toLong()
        if (simpleKeyAllowed) {
            val key = SimpleKey(true,required,tokensParsed+(queue.size-head).toULong(),mark)
            removeKey(); keys[keys.lastIndex] = key
        }
    }
    private fun roll(column: Long, number: ULong?, type: YamlTokenType, start: YamlMark) {
        if (flowLevel != 0) return
        if (indent.toLong() < column) {
            indents.add(indent)
            if (column > Int.MAX_VALUE) memory()
            indent = column.toInt()
            val token = YamlToken(type,start,start)
            if (number == null) queue.add(token) else queue.add(head+(number-tokensParsed).toInt(),token)
        }
    }
    private fun unroll(column: Long) {
        if (flowLevel != 0) return
        while (indent.toLong() > column) { enqueue(YamlTokenType.BlockEnd); indent = indents.removeAt(indents.lastIndex) }
    }
    private fun simple(type: YamlTokenType, count: Int = 1) { val start=mark; repeat(count) { skip() }; enqueue(type,start,mark) }
    private fun nextContent() {
        while (true) {
            cache(1)
            if (mark.column == 0uL && b() == 0xef && b(1) == 0xbb && b(2) == 0xbf) skip()
            cache(1)
            while (c(' ') || (flowLevel != 0 || !simpleKeyAllowed) && c('\t')) { skip(); cache(1) }
            if (c('#')) while (!breakZ()) { skip(); cache(1) }
            if (!br()) return
            cache(2); line()
            if (flowLevel == 0) simpleKeyAllowed = true
        }
    }
    private fun fetch() {
        cache(1)
        if (!started) {
            indent=-1; keys.add(SimpleKey()); simpleKeyAllowed=true; started=true
            queue.add(YamlToken(YamlTokenType.StreamStart,mark,mark,encoding=reader.encoding)); return
        }
        nextContent(); staleKeys(); unroll(mark.column.toLong()); cache(4)
        when {
            b() == 0 -> {
                if (mark.column != 0uL) mark=mark.copy(column=0u,line=mark.line+1u)
                unroll(-1); removeKey(); simpleKeyAllowed=false; enqueue(YamlTokenType.StreamEnd)
            }
            mark.column == 0uL && c('%') -> { unroll(-1); removeKey(); simpleKeyAllowed=false; queue.add(directive()) }
            docIndicator() -> { val type=if(c('-')) YamlTokenType.DocumentStart else YamlTokenType.DocumentEnd
                unroll(-1); removeKey(); simpleKeyAllowed=false; simple(type,3) }
            c('[') || c('{') -> {
                val type=if(c('[')) YamlTokenType.FlowSequenceStart else YamlTokenType.FlowMappingStart
                saveKey(); keys.add(SimpleKey()); if(flowLevel==Int.MAX_VALUE) memory(); flowLevel++
                simpleKeyAllowed=true; simple(type)
            }
            c(']') || c('}') -> {
                val type=if(c(']')) YamlTokenType.FlowSequenceEnd else YamlTokenType.FlowMappingEnd
                removeKey(); if(flowLevel!=0) { flowLevel--; keys.removeAt(keys.lastIndex) }
                simpleKeyAllowed=false; simple(type)
            }
            c(',') -> { removeKey(); simpleKeyAllowed=true; simple(YamlTokenType.FlowEntry) }
            c('-') && blankZ(1) -> {
                if(flowLevel==0) {
                    if(!simpleKeyAllowed) fail(null,mark,"block sequence entries are not allowed in this context")
                    roll(mark.column.toLong(),null,YamlTokenType.BlockSequenceStart,mark)
                }
                removeKey(); simpleKeyAllowed=true; simple(YamlTokenType.BlockEntry)
            }
            c('?') && (flowLevel!=0 || blankZ(1)) -> {
                if(flowLevel==0) {
                    if(!simpleKeyAllowed) fail(null,mark,"mapping keys are not allowed in this context")
                    roll(mark.column.toLong(),null,YamlTokenType.BlockMappingStart,mark)
                }
                removeKey(); simpleKeyAllowed=flowLevel==0; simple(YamlTokenType.Key)
            }
            c(':') && (flowLevel!=0 || blankZ(1)) -> {
                val key=keys.last()
                if(key.possible) {
                    queue.add(head+(key.number-tokensParsed).toInt(),YamlToken(YamlTokenType.Key,key.mark,key.mark))
                    roll(key.mark.column.toLong(),key.number,YamlTokenType.BlockMappingStart,key.mark)
                    key.possible=false; simpleKeyAllowed=false
                } else {
                    if(flowLevel==0) {
                        if(!simpleKeyAllowed) fail(null,mark,"mapping values are not allowed in this context")
                        roll(mark.column.toLong(),null,YamlTokenType.BlockMappingStart,mark)
                    }
                    simpleKeyAllowed=flowLevel==0
                }
                simple(YamlTokenType.Value)
            }
            c('*') || c('&') -> { val type=if(c('&')) YamlTokenType.Anchor else YamlTokenType.Alias
                saveKey(); simpleKeyAllowed=false; queue.add(anchor(type)) }
            c('!') -> { saveKey(); simpleKeyAllowed=false; queue.add(tag()) }
            (c('|') || c('>')) && flowLevel==0 -> { val literal=c('|'); removeKey(); simpleKeyAllowed=true; queue.add(blockScalar(literal)) }
            c('\'') || c('"') -> { val single=c('\''); saveKey(); simpleKeyAllowed=false; queue.add(flowScalar(single)) }
            !blankZ() && b().toChar() !in "-?:,[]{}#&*!|>'\"%@`" ||
                c('-') && !blank(1) || flowLevel==0 && (c('?') || c(':')) && !blankZ(1) -> {
                saveKey(); simpleKeyAllowed=false; queue.add(plainScalar())
            }
            else -> fail("while scanning for the next token",mark,"found character that cannot start any token")
        }
    }
    private fun directive(): YamlToken {
        val start=mark; skip(); val name=ByteArrayOutputStream(); cache(1)
        while(alpha()) { read(name); cache(1) }
        if(name.size()==0) fail("while scanning a directive",start,"could not find expected directive name")
        if(!blankZ()) fail("while scanning a directive",start,"found unexpected non-alphabetical character")
        val token=when(name.toString(Charsets.UTF_8)) {
            "YAML" -> {
                cache(1); while(blank()) { skip(); cache(1) }
                val major=versionNumber(start)
                if(!c('.')) fail("while scanning a %YAML directive",start,"did not find expected digit or '.' character")
                skip(); val minor=versionNumber(start)
                YamlToken(YamlTokenType.VersionDirective,start,mark,major=major,minor=minor)
            }
            "TAG" -> {
                cache(1); while(blank()) { skip(); cache(1) }
                val handle=tagHandle(true,start); cache(1)
                if(!blank()) fail("while scanning a %TAG directive",start,"did not find expected whitespace")
                while(blank()) { skip(); cache(1) }
                val prefix=tagUri(true,true,null,start); cache(1)
                if(!blankZ()) fail("while scanning a %TAG directive",start,"did not find expected whitespace or line break")
                YamlToken(YamlTokenType.TagDirective,start,mark,handle,prefix)
            }
            else -> fail("while scanning a directive",start,"found unknown directive name")
        }
        cache(1); while(blank()) { skip(); cache(1) }
        if(c('#')) while(!breakZ()) { skip(); cache(1) }
        if(!breakZ()) fail("while scanning a directive",start,"did not find expected comment or line break")
        if(br()) { cache(2); line() }
        return token
    }
    private fun versionNumber(start: YamlMark): Int {
        var value=0; var length=0; cache(1)
        while(digit()) {
            if(++length>9) fail("while scanning a %YAML directive",start,"found extremely long version number")
            value=value*10+b()-48; skip(); cache(1)
        }
        if(length==0) fail("while scanning a %YAML directive",start,"did not find expected version number")
        return value
    }
    private fun anchor(type: YamlTokenType): YamlToken {
        val start=mark; skip(); cache(1); val value=ByteArrayOutputStream()
        while(alpha()) { read(value); cache(1) }
        if(value.size()==0 || !(blankZ() || b().toChar() in "?:,]}%@`"))
            fail(if(type==YamlTokenType.Anchor) "while scanning an anchor" else "while scanning an alias",start,
                "did not find expected alphabetic or numeric character")
        return YamlToken(type,start,mark,value.toByteArray())
    }
    private fun tagHandle(directive: Boolean, start: YamlMark): ByteArray {
        val context=if(directive) "while scanning a tag directive" else "while scanning a tag"
        cache(1); if(!c('!')) fail(context,start,"did not find expected '!'")
        val out=ByteArrayOutputStream(); read(out); cache(1)
        while(alpha()) { read(out); cache(1) }
        if(c('!')) read(out)
        else if(directive && out.size()!=1) fail("while parsing a tag directive",start,"did not find expected '!'")
        return out.toByteArray()
    }
    private fun tagUri(uriChars: Boolean, directive: Boolean, head: ByteArray?, start: YamlMark): ByteArray {
        var length=head?.size ?: 0; val out=ByteArrayOutputStream()
        if(length>1) out.write(head,1,length-1)
        cache(1)
        while(alpha() || b().toChar() in ";/?:@&=+$ .%!~*'()".replace(" ","") || uriChars && b().toChar() in ",[]") {
            if(c('%')) uriEscape(out,directive,start) else read(out)
            length++; cache(1)
        }
        if(length==0) fail(if(directive) "while parsing a %TAG directive" else "while parsing a tag",start,"did not find expected tag URI")
        return out.toByteArray()
    }
    private fun uriEscape(out: ByteArrayOutputStream, directive: Boolean, start: YamlMark) {
        val context=if(directive) "while parsing a %TAG directive" else "while parsing a tag"
        var left=0
        do {
            cache(3)
            if(!c('%') || !hex(1) || !hex(2)) fail(context,start,"did not find URI escaped octet")
            val octet=(hexValue(1) shl 4)+hexValue(2)
            if(left==0) {
                left=when { octet and 128==0 -> 1; octet and 224==192 -> 2; octet and 240==224 -> 3; octet and 248==240 -> 4; else -> 0 }
                if(left==0) fail(context,start,"found an incorrect leading UTF-8 octet")
            } else if(octet and 192!=128) fail(context,start,"found an incorrect trailing UTF-8 octet")
            out.write(octet); repeat(3) { skip() }
            left--
        } while(left!=0)
    }
    private fun tag(): YamlToken {
        val start=mark; cache(2); var handle: ByteArray; var suffix: ByteArray
        if(c('<',1)) {
            handle=byteArrayOf(); repeat(2) { skip() }; suffix=tagUri(true,false,null,start)
            if(!c('>')) fail("while scanning a tag",start,"did not find the expected '>'")
            skip()
        } else {
            handle=tagHandle(false,start)
            if(handle.size>1 && handle.last()==33.toByte()) suffix=tagUri(false,false,null,start)
            else {
                suffix=tagUri(false,false,handle,start); handle=byteArrayOf(33)
                if(suffix.isEmpty() || suffix[0]==0.toByte()) { val tmp=handle; handle=suffix; suffix=tmp }
            }
        }
        cache(1)
        if(!blankZ() && !(flowLevel!=0 && c(','))) fail("while scanning a tag",start,"did not find expected whitespace or line break")
        return YamlToken(YamlTokenType.Tag,start,mark,handle,suffix)
    }

    private fun plainScalar(): YamlToken {
        val start=mark; var end=mark; val out=ByteArrayOutputStream()
        val leading=ByteArrayOutputStream(); val trailing=ByteArrayOutputStream(); val spaces=ByteArrayOutputStream()
        var leadingBlanks=false; val requiredIndent=indent+1
        while(true) {
            cache(4); if(docIndicator() || c('#')) break
            while(!blankZ()) {
                if(flowLevel!=0 && c(':') && b(1).toChar() in ",?[]{}") fail("while scanning a plain scalar",start,"found unexpected ':'")
                if(c(':') && blankZ(1) || flowLevel!=0 && b().toChar() in ",[]{}") break
                if(leadingBlanks || spaces.size()!=0) {
                    if(leadingBlanks) { fold(out,leading,trailing); leadingBlanks=false }
                    else { spaces.writeTo(out); spaces.reset() }
                }
                read(out); end=mark; cache(2)
            }
            if(!blank() && !br()) break
            cache(1)
            while(blank() || br()) {
                if(blank()) {
                    if(leadingBlanks && mark.column.toInt()<requiredIndent && c('\t'))
                        fail("while scanning a plain scalar",start,"found a tab character that violates indentation")
                    if(!leadingBlanks) read(spaces) else skip()
                } else {
                    cache(2)
                    if(!leadingBlanks) { spaces.reset(); line(leading); leadingBlanks=true } else line(trailing)
                }
                cache(1)
            }
            if(flowLevel==0 && mark.column.toInt()<requiredIndent) break
        }
        if(leadingBlanks) simpleKeyAllowed=true
        return YamlToken(YamlTokenType.Scalar,start,end,out.toByteArray(),style=YamlStyle.Plain)
    }
    private fun fold(out: ByteArrayOutputStream, leading: ByteArrayOutputStream, trailing: ByteArrayOutputStream) {
        if(leading.toByteArray().firstOrNull()==10.toByte()) {
            if(trailing.size()==0) out.write(32) else { trailing.writeTo(out); trailing.reset() }
            leading.reset()
        } else { leading.writeTo(out); trailing.writeTo(out); leading.reset(); trailing.reset() }
    }
    private fun flowScalar(single: Boolean): YamlToken {
        val start=mark; skip(); val out=ByteArrayOutputStream()
        val leading=ByteArrayOutputStream(); val trailing=ByteArrayOutputStream(); val spaces=ByteArrayOutputStream()
        val quote=if(single) '\'' else '"'
        while(true) {
            cache(4)
            if(docIndicator()) fail("while scanning a quoted scalar",start,"found unexpected document indicator")
            if(b()==0) fail("while scanning a quoted scalar",start,"found unexpected end of stream")
            cache(2); var leadingBlanks=false
            while(!blankZ()) {
                when {
                    single && c('\'') && c('\'',1) -> { out.write(39); skip(); skip() }
                    c(quote) -> break
                    !single && c('\\') && br(1) -> { cache(3); skip(); line(); leadingBlanks=true; break }
                    !single && c('\\') -> quotedEscape(out,start)
                    else -> read(out)
                }
                cache(2)
            }
            cache(1); if(c(quote)) break
            cache(1)
            while(blank() || br()) {
                if(blank()) { if(!leadingBlanks) read(spaces) else skip() }
                else { cache(2); if(!leadingBlanks) { spaces.reset(); line(leading); leadingBlanks=true } else line(trailing) }
                cache(1)
            }
            if(leadingBlanks) fold(out,leading,trailing) else { spaces.writeTo(out); spaces.reset() }
        }
        skip()
        return YamlToken(YamlTokenType.Scalar,start,mark,out.toByteArray(),style=if(single) YamlStyle.SingleQuoted else YamlStyle.DoubleQuoted)
    }
    private fun quotedEscape(out: ByteArrayOutputStream, start: YamlMark) {
        val esc=b(1).toChar(); var length=0
        when(esc) {
            '0' -> out.write(0); 'a' -> out.write(7); 'b' -> out.write(8); 't','\t' -> out.write(9)
            'n' -> out.write(10); 'v' -> out.write(11); 'f' -> out.write(12); 'r' -> out.write(13); 'e' -> out.write(27)
            ' ','"','/','\\' -> out.write(esc.code)
            'N' -> point(out,0x85u); '_' -> point(out,0xa0u); 'L' -> point(out,0x2028u); 'P' -> point(out,0x2029u)
            'x' -> length=2; 'u' -> length=4; 'U' -> length=8
            else -> fail("while parsing a quoted scalar",start,"found unknown escape character")
        }
        skip(); skip()
        if(length!=0) {
            cache(length); var value=0u
            for(k in 0 until length) {
                if(!hex(k)) fail("while parsing a quoted scalar",start,"did not find expected hexdecimal number")
                value=(value shl 4)+hexValue(k).toUInt()
            }
            if(value in 0xd800u..0xdfffu || value>0x10ffffu) fail("while parsing a quoted scalar",start,"found invalid Unicode character escape code")
            point(out,value); repeat(length) { skip() }
        }
    }
    private fun point(out: ByteArrayOutputStream, v: UInt) {
        val p=v.toInt()
        when {
            p<128 -> out.write(p)
            p<2048 -> { out.write(192+(p shr 6)); out.write(128+(p and 63)) }
            p<65536 -> { out.write(224+(p shr 12)); out.write(128+((p shr 6) and 63)); out.write(128+(p and 63)) }
            else -> { out.write(240+(p shr 18)); out.write(128+((p shr 12) and 63)); out.write(128+((p shr 6) and 63)); out.write(128+(p and 63)) }
        }
    }
    private fun blockScalar(literal: Boolean): YamlToken {
        val start=mark; skip(); var chomping=0; var increment=0; cache(1)
        if(c('+') || c('-')) {
            chomping=if(c('+')) 1 else -1; skip(); cache(1)
            if(digit()) {
                if(c('0')) fail("while scanning a block scalar",start,"found an indentation indicator equal to 0")
                increment=b()-48; skip()
            }
        } else if(digit()) {
            if(c('0')) fail("while scanning a block scalar",start,"found an indentation indicator equal to 0")
            increment=b()-48; skip(); cache(1)
            if(c('+') || c('-')) { chomping=if(c('+')) 1 else -1; skip() }
        }
        cache(1); while(blank()) { skip(); cache(1) }
        if(c('#')) while(!breakZ()) { skip(); cache(1) }
        if(!breakZ()) fail("while scanning a block scalar",start,"did not find expected comment or line break")
        if(br()) { cache(2); line() }
        val out=ByteArrayOutputStream(); val leading=ByteArrayOutputStream(); val trailing=ByteArrayOutputStream()
        var requiredIndent=if(increment!=0) { if(indent>=0) indent+increment else increment } else 0
        var breaks=blockBreaks(requiredIndent,trailing,start); requiredIndent=breaks.first; var end=breaks.second
        var leadingBlank=false; cache(1)
        while(mark.column.toInt()==requiredIndent && b()!=0) {
            val trailingBlank=blank()
            if(!literal && leading.toByteArray().firstOrNull()==10.toByte() && !leadingBlank && !trailingBlank) {
                if(trailing.size()==0) out.write(32)
                leading.reset()
            } else { leading.writeTo(out); leading.reset() }
            trailing.writeTo(out); trailing.reset(); leadingBlank=blank()
            while(!breakZ()) { read(out); cache(1) }
            cache(2); line(leading)
            breaks=blockBreaks(requiredIndent,trailing,start); requiredIndent=breaks.first; end=breaks.second
        }
        if(chomping!=-1) leading.writeTo(out)
        if(chomping==1) trailing.writeTo(out)
        return YamlToken(YamlTokenType.Scalar,start,end,out.toByteArray(),style=if(literal) YamlStyle.Literal else YamlStyle.Folded)
    }
    private fun blockBreaks(initial: Int, breaks: ByteArrayOutputStream, start: YamlMark): Pair<Int,YamlMark> {
        var requiredIndent=initial; var maxIndent=0; var end=mark
        while(true) {
            cache(1)
            while((requiredIndent==0 || mark.column.toInt()<requiredIndent) && c(' ')) { skip(); cache(1) }
            if(mark.column.toInt()>maxIndent) maxIndent=mark.column.toInt()
            if((requiredIndent==0 || mark.column.toInt()<requiredIndent) && c('\t'))
                fail("while scanning a block scalar",start,"found a tab character where an indentation space is expected")
            if(!br()) break
            cache(2); line(breaks); end=mark
        }
        if(requiredIndent==0) requiredIndent=maxOf(maxIndent,indent+1,1)
        return requiredIndent to end
    }
}
