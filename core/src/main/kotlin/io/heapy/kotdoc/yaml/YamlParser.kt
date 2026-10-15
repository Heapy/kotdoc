// Complete libyaml 0.2.5 parser.c algorithm, Kirill Simonov and Ingy döt Net.
// MIT; complete original notice: licenses/Libyaml-MIT.txt.
package io.heapy.kotdoc.yaml

/** Original iterative LL(1) parser; recursion depth lives in the state stack. */
class YamlParser(internal val scanner: YamlScanner) {
    var state: YamlParserState = YamlParserState.StreamStart
        private set
    var failure: YamlScanFailure? = null
        private set
    private val states=ArrayList<YamlParserState>()
    private val marks=ArrayList<YamlMark>()
    private val tags=ArrayList<YamlTagDirective>()
    internal fun savedStates()=states.toList()
    internal fun savedMarks()=marks.toList()
    internal fun tagDirectives()=tags.toList()
    private class Stop : RuntimeException(null,null,false,false)
    private fun peek(): YamlToken = scanner.peekForParser() ?: run { failure=checkNotNull(scanner.failure);throw Stop() }
    private fun skip()=scanner.consumeForParser()
    private fun pop()=states.removeAt(states.lastIndex)
    private fun popMark()=marks.removeAt(marks.lastIndex)
    private fun fail(problem: String, at: YamlMark, context: String?=null, contextMark: YamlMark=YamlMark()): Nothing {
        failure=YamlScanFailure(4,problem,context,at,contextMark);throw Stop()
    }
    fun next(): YamlParseResult {
        if(state==YamlParserState.End || failure!=null || scanner.streamEndProduced) return YamlParseResult.End
        if(scanner.failure!=null) { failure=scanner.failure;return YamlParseResult.End }
        return try { YamlParseResult.Event(dispatch()) } catch (_: Stop) { YamlParseResult.Failure(checkNotNull(failure)) }
    }
    private fun dispatch(): YamlEvent = when(state) {
        YamlParserState.StreamStart -> streamStart()
        YamlParserState.ImplicitDocumentStart -> documentStart(true)
        YamlParserState.DocumentStart -> documentStart(false)
        YamlParserState.DocumentContent -> documentContent()
        YamlParserState.DocumentEnd -> documentEnd()
        YamlParserState.BlockNode -> node(true,false)
        YamlParserState.BlockNodeOrIndentlessSequence -> node(true,true)
        YamlParserState.FlowNode -> node(false,false)
        YamlParserState.BlockSequenceFirstEntry -> blockSequence(true)
        YamlParserState.BlockSequenceEntry -> blockSequence(false)
        YamlParserState.IndentlessSequenceEntry -> indentlessSequence()
        YamlParserState.BlockMappingFirstKey -> blockMappingKey(true)
        YamlParserState.BlockMappingKey -> blockMappingKey(false)
        YamlParserState.BlockMappingValue -> blockMappingValue()
        YamlParserState.FlowSequenceFirstEntry -> flowSequence(true)
        YamlParserState.FlowSequenceEntry -> flowSequence(false)
        YamlParserState.FlowSequenceEntryMappingKey -> sequenceMappingKey()
        YamlParserState.FlowSequenceEntryMappingValue -> sequenceMappingValue()
        YamlParserState.FlowSequenceEntryMappingEnd -> sequenceMappingEnd()
        YamlParserState.FlowMappingFirstKey -> flowMappingKey(true)
        YamlParserState.FlowMappingKey -> flowMappingKey(false)
        YamlParserState.FlowMappingValue -> flowMappingValue(false)
        YamlParserState.FlowMappingEmptyValue -> flowMappingValue(true)
        YamlParserState.End -> error("terminal parser state")
    }
    private fun streamStart(): YamlEvent {
        val t=peek()
        if(t.type!=YamlTokenType.StreamStart) fail("did not find expected <stream-start>",t.start)
        state=YamlParserState.ImplicitDocumentStart
        val e=YamlEvent(YamlEventType.StreamStart,t.start,t.start,encoding=t.encoding);skip();return e
    }
    private fun documentStart(implicit: Boolean): YamlEvent {
        var t=peek()
        if(!implicit) while(t.type==YamlTokenType.DocumentEnd) { skip();t=peek() }
        if(implicit && t.type !in listOf(YamlTokenType.VersionDirective,YamlTokenType.TagDirective,YamlTokenType.DocumentStart,YamlTokenType.StreamEnd)) {
            processDirectives();states.add(YamlParserState.DocumentEnd);state=YamlParserState.BlockNode
            return YamlEvent(YamlEventType.DocumentStart,t.start,t.start,implicit=true)
        }
        if(t.type!=YamlTokenType.StreamEnd) {
            val start=t.start;val (version,directives)=processDirectives();t=peek()
            if(t.type!=YamlTokenType.DocumentStart) fail("did not find expected <document start>",t.start)
            states.add(YamlParserState.DocumentEnd);state=YamlParserState.DocumentContent
            val e=YamlEvent(YamlEventType.DocumentStart,start,t.end,version=version,directives=directives);skip();return e
        }
        state=YamlParserState.End
        val e=YamlEvent(YamlEventType.StreamEnd,t.start,t.end);skip();return e
    }
    private fun documentContent(): YamlEvent {
        val t=peek()
        if(t.type in listOf(YamlTokenType.VersionDirective,YamlTokenType.TagDirective,YamlTokenType.DocumentStart,YamlTokenType.DocumentEnd,YamlTokenType.StreamEnd)) {
            state=pop();return empty(t.start)
        }
        return node(true,false)
    }
    private fun documentEnd(): YamlEvent {
        val t=peek();var end=t.start;var implicit=true
        if(t.type==YamlTokenType.DocumentEnd) { end=t.end;skip();implicit=false }
        tags.clear();state=YamlParserState.DocumentStart
        return YamlEvent(YamlEventType.DocumentEnd,t.start,end,implicit=implicit)
    }
    private fun node(block: Boolean, indentless: Boolean): YamlEvent {
        var t=peek()
        if(t.type==YamlTokenType.Alias) {
            state=pop();val e=YamlEvent(YamlEventType.Alias,t.start,t.end,anchor=t.value);skip();return e
        }
        val start=t.start;var end=t.start;var tagMark=YamlMark()
        var anchor: ByteArray?=null;var handle: ByteArray?=null;var suffix: ByteArray?=null;var tag: ByteArray?=null
        if(t.type==YamlTokenType.Anchor) {
            anchor=t.value;end=t.end;skip();t=peek()
            if(t.type==YamlTokenType.Tag) { handle=t.value;suffix=t.suffix;tagMark=t.start;end=t.end;skip();t=peek() }
        } else if(t.type==YamlTokenType.Tag) {
            handle=t.value;suffix=t.suffix;tagMark=t.start;end=t.end;skip();t=peek()
            if(t.type==YamlTokenType.Anchor) { anchor=t.value;end=t.end;skip();t=peek() }
        }
        if(handle!=null) {
            if(handle.isEmpty()) tag=checkNotNull(suffix)
            else {
                val directive=tags.firstOrNull { it.handle.contentEquals(handle) }
                    ?: fail("found undefined tag handle",tagMark,"while parsing a node",start)
                tag=directive.prefix+checkNotNull(suffix)
            }
        }
        val implicit=tag==null || tag.isEmpty()
        if(indentless && t.type==YamlTokenType.BlockEntry) {
            state=YamlParserState.IndentlessSequenceEntry
            return YamlEvent(YamlEventType.SequenceStart,start,t.end,anchor=anchor,tag=tag,implicit=implicit,collectionStyle=YamlCollectionStyle.Block)
        }
        when(t.type) {
            YamlTokenType.Scalar -> {
                var plain=false;var quoted=false
                if(t.style==YamlStyle.Plain && tag==null || tag!=null && tag.contentEquals(byteArrayOf(33))) plain=true
                else if(tag==null) quoted=true
                state=pop()
                val e=YamlEvent(YamlEventType.Scalar,start,t.end,t.value,anchor,tag,scalarStyle=t.style,plainImplicit=plain,quotedImplicit=quoted)
                skip();return e
            }
            YamlTokenType.FlowSequenceStart -> {
                state=YamlParserState.FlowSequenceFirstEntry
                return YamlEvent(YamlEventType.SequenceStart,start,t.end,anchor=anchor,tag=tag,implicit=implicit,collectionStyle=YamlCollectionStyle.Flow)
            }
            YamlTokenType.FlowMappingStart -> {
                state=YamlParserState.FlowMappingFirstKey
                return YamlEvent(YamlEventType.MappingStart,start,t.end,anchor=anchor,tag=tag,implicit=implicit,collectionStyle=YamlCollectionStyle.Flow)
            }
            YamlTokenType.BlockSequenceStart -> if(block) {
                state=YamlParserState.BlockSequenceFirstEntry
                return YamlEvent(YamlEventType.SequenceStart,start,t.end,anchor=anchor,tag=tag,implicit=implicit,collectionStyle=YamlCollectionStyle.Block)
            }
            YamlTokenType.BlockMappingStart -> if(block) {
                state=YamlParserState.BlockMappingFirstKey
                return YamlEvent(YamlEventType.MappingStart,start,t.end,anchor=anchor,tag=tag,implicit=implicit,collectionStyle=YamlCollectionStyle.Block)
            }
            else -> Unit
        }
        if(anchor!=null || tag!=null) {
            state=pop()
            return YamlEvent(YamlEventType.Scalar,start,end,anchor=anchor,tag=tag,scalarStyle=YamlStyle.Plain,plainImplicit=implicit)
        }
        fail("did not find expected node content",t.start,if(block) "while parsing a block node" else "while parsing a flow node",start)
    }
    private fun blockSequence(first: Boolean): YamlEvent {
        if(first) { marks.add(peek().start);skip() }
        var t=peek()
        if(t.type==YamlTokenType.BlockEntry) {
            val at=t.end;skip();t=peek()
            if(t.type!=YamlTokenType.BlockEntry && t.type!=YamlTokenType.BlockEnd) {
                states.add(YamlParserState.BlockSequenceEntry);return node(true,false)
            }
            state=YamlParserState.BlockSequenceEntry;return empty(at)
        }
        if(t.type==YamlTokenType.BlockEnd) {
            state=pop();popMark();val e=YamlEvent(YamlEventType.SequenceEnd,t.start,t.end);skip();return e
        }
        fail("did not find expected '-' indicator",t.start,"while parsing a block collection",popMark())
    }
    private fun indentlessSequence(): YamlEvent {
        var t=peek()
        if(t.type==YamlTokenType.BlockEntry) {
            val at=t.end;skip();t=peek()
            if(t.type !in listOf(YamlTokenType.BlockEntry,YamlTokenType.Key,YamlTokenType.Value,YamlTokenType.BlockEnd)) {
                states.add(YamlParserState.IndentlessSequenceEntry);return node(true,false)
            }
            state=YamlParserState.IndentlessSequenceEntry;return empty(at)
        }
        state=pop();return YamlEvent(YamlEventType.SequenceEnd,t.start,t.start)
    }
    private fun blockMappingKey(first: Boolean): YamlEvent {
        if(first) { marks.add(peek().start);skip() }
        var t=peek()
        if(t.type==YamlTokenType.Key) {
            val at=t.end;skip();t=peek()
            if(t.type !in listOf(YamlTokenType.Key,YamlTokenType.Value,YamlTokenType.BlockEnd)) {
                states.add(YamlParserState.BlockMappingValue);return node(true,true)
            }
            state=YamlParserState.BlockMappingValue;return empty(at)
        }
        if(t.type==YamlTokenType.BlockEnd) {
            state=pop();popMark();val e=YamlEvent(YamlEventType.MappingEnd,t.start,t.end);skip();return e
        }
        fail("did not find expected key",t.start,"while parsing a block mapping",popMark())
    }
    private fun blockMappingValue(): YamlEvent {
        var t=peek()
        if(t.type==YamlTokenType.Value) {
            val at=t.end;skip();t=peek()
            if(t.type !in listOf(YamlTokenType.Key,YamlTokenType.Value,YamlTokenType.BlockEnd)) {
                states.add(YamlParserState.BlockMappingKey);return node(true,true)
            }
            state=YamlParserState.BlockMappingKey;return empty(at)
        }
        state=YamlParserState.BlockMappingKey;return empty(t.start)
    }
    private fun flowSequence(first: Boolean): YamlEvent {
        if(first) { marks.add(peek().start);skip() }
        var t=peek()
        if(t.type!=YamlTokenType.FlowSequenceEnd) {
            if(!first) {
                if(t.type==YamlTokenType.FlowEntry) { skip();t=peek() }
                else fail("did not find expected ',' or ']'",t.start,"while parsing a flow sequence",popMark())
            }
            if(t.type==YamlTokenType.Key) {
                state=YamlParserState.FlowSequenceEntryMappingKey
                val e=YamlEvent(YamlEventType.MappingStart,t.start,t.end,implicit=true,collectionStyle=YamlCollectionStyle.Flow);skip();return e
            }
            if(t.type!=YamlTokenType.FlowSequenceEnd) {
                states.add(YamlParserState.FlowSequenceEntry);return node(false,false)
            }
        }
        state=pop();popMark();val e=YamlEvent(YamlEventType.SequenceEnd,t.start,t.end);skip();return e
    }
    private fun sequenceMappingKey(): YamlEvent {
        val t=peek()
        if(t.type !in listOf(YamlTokenType.Value,YamlTokenType.FlowEntry,YamlTokenType.FlowSequenceEnd)) {
            states.add(YamlParserState.FlowSequenceEntryMappingValue);return node(false,false)
        }
        val at=t.end;skip();state=YamlParserState.FlowSequenceEntryMappingValue;return empty(at)
    }
    private fun sequenceMappingValue(): YamlEvent {
        var t=peek()
        if(t.type==YamlTokenType.Value) {
            skip();t=peek()
            if(t.type!=YamlTokenType.FlowEntry && t.type!=YamlTokenType.FlowSequenceEnd) {
                states.add(YamlParserState.FlowSequenceEntryMappingEnd);return node(false,false)
            }
        }
        state=YamlParserState.FlowSequenceEntryMappingEnd;return empty(t.start)
    }
    private fun sequenceMappingEnd(): YamlEvent {
        val t=peek();state=YamlParserState.FlowSequenceEntry
        return YamlEvent(YamlEventType.MappingEnd,t.start,t.start)
    }
    private fun flowMappingKey(first: Boolean): YamlEvent {
        if(first) { marks.add(peek().start);skip() }
        var t=peek()
        if(t.type!=YamlTokenType.FlowMappingEnd) {
            if(!first) {
                if(t.type==YamlTokenType.FlowEntry) { skip();t=peek() }
                else fail("did not find expected ',' or '}'",t.start,"while parsing a flow mapping",popMark())
            }
            if(t.type==YamlTokenType.Key) {
                skip();t=peek()
                if(t.type !in listOf(YamlTokenType.Value,YamlTokenType.FlowEntry,YamlTokenType.FlowMappingEnd)) {
                    states.add(YamlParserState.FlowMappingValue);return node(false,false)
                }
                state=YamlParserState.FlowMappingValue;return empty(t.start)
            }
            if(t.type!=YamlTokenType.FlowMappingEnd) {
                states.add(YamlParserState.FlowMappingEmptyValue);return node(false,false)
            }
        }
        state=pop();popMark();val e=YamlEvent(YamlEventType.MappingEnd,t.start,t.end);skip();return e
    }
    private fun flowMappingValue(empty: Boolean): YamlEvent {
        var t=peek()
        if(empty) { state=YamlParserState.FlowMappingKey;return empty(t.start) }
        if(t.type==YamlTokenType.Value) {
            skip();t=peek()
            if(t.type!=YamlTokenType.FlowEntry && t.type!=YamlTokenType.FlowMappingEnd) {
                states.add(YamlParserState.FlowMappingKey);return node(false,false)
            }
        }
        state=YamlParserState.FlowMappingKey;return empty(t.start)
    }
    private fun empty(at: YamlMark)=YamlEvent(YamlEventType.Scalar,at,at,scalarStyle=YamlStyle.Plain,plainImplicit=true)
    private fun processDirectives(): Pair<YamlVersionDirective?,List<YamlTagDirective>> {
        var version: YamlVersionDirective?=null;val explicit=ArrayList<YamlTagDirective>();var t=peek()
        while(t.type==YamlTokenType.VersionDirective || t.type==YamlTokenType.TagDirective) {
            if(t.type==YamlTokenType.VersionDirective) {
                if(version!=null) fail("found duplicate %YAML directive",t.start)
                if(t.major!=1 || t.minor !in 1..2) fail("found incompatible YAML document",t.start)
                version=YamlVersionDirective(t.major,t.minor)
            } else {
                val directive=YamlTagDirective(t.value,t.suffix);appendTag(directive,false,t.start);explicit.add(directive)
            }
            skip();t=peek()
        }
        appendTag(YamlTagDirective(byteArrayOf(33),byteArrayOf(33)),true,t.start)
        appendTag(YamlTagDirective(byteArrayOf(33,33),"tag:yaml.org,2002:".toByteArray()),true,t.start)
        return version to explicit
    }
    private fun appendTag(value: YamlTagDirective, allowDuplicate: Boolean, at: YamlMark) {
        if(tags.any { it.handle.contentEquals(value.handle) }) {
            if(allowDuplicate) return
            fail("found duplicate %TAG directive",at)
        }
        tags.add(value)
    }
}
