package io.heapy.kotdoc.parsing.attoparsec

import io.heapy.kotdoc.layout.TextChars

/** Immutable chunk buffer. Continuations can branch without copying consumed
 * prefixes or replaying a parser, including when a failed choice backtracks. */
internal sealed class TextRope(val size: Int,val height: Int) {
    private class Leaf(val points: IntArray): TextRope(points.size,1)
    private class Branch(val left: TextRope,val right: TextRope): TextRope(left.size+right.size,maxOf(left.height,right.height)+1)
    fun append(text: String): TextRope = join(this,from(text))
    operator fun get(index: Int): Int {
        require(index in 0..<size)
        var node=this;var at=index
        while(node is Branch)if(at<node.left.size)node=node.left else {at-=node.left.size;node=node.right}
        return (node as Leaf).points[at]
    }
    fun slice(from: Int,to: Int=size): String {
        require(from in 0..to && to<=size)
        return buildString {
            val stack=ArrayDeque<Pair<TextRope,Int>>();stack.addLast(this@TextRope to 0)
            while(stack.isNotEmpty()) {
                val(node,offset)=stack.removeLast()
                if(offset>=to || offset+node.size<=from)continue
                when(node) {
                    is Leaf->for(i in maxOf(0,from-offset)..<minOf(node.size,to-offset))appendCodePoint(node.points[i])
                    is Branch->{stack.addLast(node.right to offset+node.left.size);stack.addLast(node.left to offset)}
                }
            }
        }
    }
    companion object {
        val empty: TextRope=Leaf(intArrayOf())
        fun from(text: String): TextRope = Leaf(TextChars.fromPoints(text.codePoints().toArray()).codePoints().toArray())
        private fun branch(a: TextRope,b: TextRope): TextRope = when {a.size==0->b;b.size==0->a;else->Branch(a,b)}
        private fun balance(a: TextRope,b: TextRope): TextRope {
            if(a.height>b.height+1) {
                a as Branch
                return if(a.left.height>=a.right.height)branch(a.left,branch(a.right,b)) else {
                    val r=a.right as Branch;branch(branch(a.left,r.left),branch(r.right,b))
                }
            }
            if(b.height>a.height+1) {
                b as Branch
                return if(b.right.height>=b.left.height)branch(branch(a,b.left),b.right) else {
                    val l=b.left as Branch;branch(branch(a,l.left),branch(l.right,b.right))
                }
            }
            return branch(a,b)
        }
        private fun join(a: TextRope,b: TextRope): TextRope = when {
            a.size==0->b;b.size==0->a
            a.height>b.height+1->{a as Branch;balance(a.left,join(a.right,b))}
            b.height>a.height+1->{b as Branch;balance(join(a,b.left),b.right)}
            else->branch(a,b)
        }
    }
}
