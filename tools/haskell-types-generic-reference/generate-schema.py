#!/usr/bin/env python3
"""Emit explicit source Data schemas from the independently extracted declarations."""
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
schema=json.loads((HERE/'definition-schema.json').read_text())
def expr(e):
    if 'name'in e:return 'N('+json.dumps(e['name'])+')'
    if 'list'in e:return 'L('+expr(e['list'])+')'
    if 'tuple'in e:return 'T(listOf('+','.join(map(expr,e['tuple']))+'))'
    return 'A('+json.dumps(e['apply'])+',listOf('+','.join(map(expr,e['args']))+'))'
rows=[]
for t in schema['types']:
    cs=[]
    for c in t['constructors']:
        cs.append('GenericConstructor('+json.dumps(c['name'])+',listOf('+','.join(map(expr,c['fields']))+'),listOf('+','.join(map(json.dumps,c['labels']))+'))')
    rows.append('        GenericDeclaration('+json.dumps(t['name'])+','+str(t['newtype']).lower()+',listOf('+','.join(cs)+'))')
aliases=[json.dumps(a['name'])+' to '+expr(a['type'])for a in schema['aliases']]
head='''// Source Data schema of pandoc-types Definition; Copyright (c) 2006–2023 John MacFarlane.
// BSD-3-Clause. Generated from the pinned declarations; never used to make expected output.
package io.heapy.kotdoc.ast

sealed interface GenericTypeExpression {
    data class N(val name:String):GenericTypeExpression
    data class L(val element:GenericTypeExpression):GenericTypeExpression
    data class T(val fields:List<GenericTypeExpression>):GenericTypeExpression
    data class A(val name:String,val arguments:List<GenericTypeExpression>):GenericTypeExpression
}
class GenericConstructor(val name:String,val fields:List<GenericTypeExpression>,val labels:List<String>)
class GenericDeclaration(val name:String,val newtype:Boolean,val constructors:List<GenericConstructor>)
class GenericNode internal constructor(val declaration:String,val constructor:String,val fields:List<GenericValue<*>>)

object GenericDefinitionData {
    private fun N(name:String)=GenericTypeExpression.N(name)
    private fun L(element:GenericTypeExpression)=GenericTypeExpression.L(element)
    private fun T(fields:List<GenericTypeExpression>)=GenericTypeExpression.T(fields)
    private fun A(name:String,args:List<GenericTypeExpression>)=GenericTypeExpression.A(name,args)
    val declarations:List<GenericDeclaration> = listOf(
'''
tail='''
    )
    private val dictionaries=HashMap<String,GenericData<Any?>>()
    @Suppress("UNCHECKED_CAST")
    fun named(name:String):GenericData<Any?> {
        val primitive:GenericData<*>?=when(name){"Int"->GenericDatas.int;"Double"->GenericDatas.double;"Bool"->GenericDatas.boolean;"Char"->GenericDatas.char;"Text"->GenericDatas.text;else->null}
        if(primitive!=null)return primitive as GenericData<Any?>
        aliases[name]?.let{return resolve(it)}
        return synchronized(dictionaries){dictionaries.getOrPut(name){DeclarationData(declarations.single{it.name==name}) as GenericData<Any?>}}
    }
    @Suppress("UNCHECKED_CAST")
    fun resolve(expression:GenericTypeExpression):GenericData<Any?> = when(expression) {
        is GenericTypeExpression.N->named(expression.name)
        is GenericTypeExpression.L->GenericDatas.list(resolve(expression.element)) as GenericData<Any?>
        is GenericTypeExpression.T->{val fields=expression.fields.map(::resolve);val result=when(fields.size){2->GenericDatas.tuple2(fields[0],fields[1]);3->GenericDatas.tuple3(fields[0],fields[1],fields[2]);else->error("Unsupported source tuple arity")};result as GenericData<Any?>}
        is GenericTypeExpression.A->when(expression.name){
            "Maybe"->GenericDatas.maybe(resolve(expression.arguments.single())) as GenericData<Any?>
            "M.Map"->GenericDatas.map(resolve(expression.arguments[0]),resolve(expression.arguments[1]),Comparator<Any?>{a,b->UnicodeTextOrder.compare(a as String,b as String)}) as GenericData<Any?>
            else->error("Unknown source type application: $expression")
        }
    }
    fun node(name:String,constructor:String,fields:List<GenericValue<*>>):GenericNode {
        val declaration=declarations.single{it.name==name};val spec=declaration.constructors.single{it.name==constructor}
        require(fields.size==spec.fields.size)
        fields.zip(spec.fields).forEach{(field,type)->require(field.data.type==resolve(type).type)}
        return GenericNode(name,constructor,fields)
    }
    fun value(name:String,constructor:String,fields:List<GenericValue<*>>):GenericValue<Any?> = GenericValue(named(name)){node(name,constructor,fields)}
    private class DeclarationData(private val declaration:GenericDeclaration):GenericData<GenericNode> {
        override val type=GenericType("Text.Pandoc.Definition",declaration.name)
        override fun force(value:()->GenericNode):GenericNode {
            val input=value();require(input.declaration==declaration.name)
            val constructor=declaration.constructors.single{it.name==input.constructor}
            // Definition uses StrictData; newtype WHNF is the underlying field WHNF.
            for((field,expression)in input.fields.zip(constructor.fields))field.cast(resolve(expression))
            return input
        }
        override fun toCanonical(value:GenericNode):Any? = if(declaration.newtype)value.fields.single().canonical() else value
        override fun fromCanonical(value:Any?):GenericNode {
            if(!declaration.newtype)return value as GenericNode
            val constructor=declaration.constructors.single();val field=resolve(constructor.fields.single())
            return node(declaration.name,constructor.name,listOf(GenericValue(field){field.fromCanonical(value)}))
        }
        override fun layer(value:()->GenericNode):GenericLayer<GenericNode> {
            if(declaration.newtype) {
                val constructor=declaration.constructors.single();val field=resolve(constructor.fields.single())
                return GenericLayer(listOf(GenericValue(field){value().fields.single().cast(field)})){fields->node(declaration.name,constructor.name,fields)}
            }
            val input=value()
            return GenericLayer(input.fields){fields->node(declaration.name,input.constructor,fields)}
        }
    }
}
'''
content=head+',\n'.join(rows)+'\n    )\n    val aliases:Map<String,GenericTypeExpression> = mapOf(\n        '+',\n        '.join(aliases)+tail
(HERE/'work-in-progress/GenericDefinitionData.kt').write_text(content)
print('Emitted',len(schema['types']),'typed source schemas /',sum(len(t['constructors'])for t in schema['types']),'constructors')
