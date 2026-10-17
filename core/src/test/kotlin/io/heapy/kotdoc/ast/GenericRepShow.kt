package io.heapy.kotdoc.ast
import io.heapy.kotdoc.formats.HaskellString
@Suppress("UNCHECKED_CAST")
internal fun repShow(value:GenericValue<*>,precedence:Int=0):String {
 val data=value.data as GenericData<Any?>;val type=data.type
 fun application(name:String,fields:List<GenericValue<*>>):String {
  val text=name+fields.joinToString(""){" "+repShow(it,11)}
  return if(fields.isNotEmpty() && precedence>10)"($text)" else text
 }
 return when {
  type.name=="Int"->AstShow.show(value.value() as Long,precedence)
  type.name=="Double"->AstShow.show(value.value() as Double,precedence)
  type.name=="Bool"->if(value.value() as Boolean)"True"else"False"
  type.name=="Char"->{val cp=value.value() as Int;"'"+when(cp){39->"\\'";34->"\"";else->HaskellString.quoteCodePoints(intArrayOf(cp)).drop(1).dropLast(1)}+"'"}
  type.name=="Text"->AstShow.show(value.value() as String)
  type.name=="[]"->{
   val rendered=mutableListOf<String>();val characters=mutableListOf<Int>();var current=value
   while(true){val layer=(current.data as GenericData<Any?>).layer(current::erased);if(layer.fields.isEmpty())break;if(type.arguments.single().name=="Char") characters+=layer.fields[0].value() as Int else rendered+=repShow(layer.fields[0]);current=layer.fields[1]}
   if(type.arguments.single().name=="Char")HaskellString.quoteCodePoints(characters.toIntArray()) else rendered.joinToString(",","[","]")
  }
  type.name in setOf("(,)","(,,)")->data.layer(value::erased).fields.joinToString(",","(",")"){repShow(it)}
  type.name=="Maybe"->{val fields=data.layer(value::erased).fields;if(fields.isEmpty())"Nothing"else application("Just",fields)}
  type.name=="Map"->application("fromList",data.layer(value::erased).fields)
  type.name=="Seq"->{val heads=mutableListOf<String>();var current=value
   while(true){val fields=(current.data as GenericData<Any?>).layer(current::erased).fields;if(fields.isEmpty())break;heads+=repShow(fields[0]);current=fields[1]}
   val text="fromList "+heads.joinToString(",","[","]");if(precedence>10)"($text)"else text
  }
  type.name=="Many"->{val field=data.layer(value::erased).fields.single();val text="Many {unMany = "+repShow(field)+"}";if(precedence>10)"($text)"else text}
  type.module=="Text.Pandoc.Definition"->{
   val node=value.value() as GenericNode;val spec=GenericDefinitionData.declarations.single{it.name==node.declaration}.constructors.single{it.name==node.constructor}
   if(spec.labels.isEmpty())application(node.constructor,node.fields) else {
    val text=node.constructor+spec.labels.zip(node.fields).joinToString(", "," {","}"){(label,field)->label+" = "+repShow(field)}
    if(precedence>10)"($text)"else text
   }
  }
  else->error("Unmapped source Show: $type")
 }
}
