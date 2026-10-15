/* Input/output observer only. All scanning executes unchanged original C. */
#include "yaml.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
typedef struct { const unsigned char *data;size_t size,pos;const int *chunks;size_t count,index,calls; } Input;
static int read_input(void *p,unsigned char *b,size_t maximum,size_t *n) {
 Input *i=p;i->calls++;
 int chunk=i->index<i->count?i->chunks[i->index++]:16384;
 if(chunk<0)return 0;
 *n=(size_t)chunk;if(*n>maximum)*n=maximum;if(*n>i->size-i->pos)*n=i->size-i->pos;
 memcpy(b,i->data+i->pos,*n);i->pos+=*n;return 1;
}
typedef struct { char *data;size_t used,capacity; } Output;
static void append(Output *o,const char *format,...) {
 for(;;){va_list args;va_start(args,format);int n=vsnprintf(o->data+o->used,o->capacity-o->used,format,args);va_end(args);
  if(n<0)abort();if((size_t)n<o->capacity-o->used){o->used+=(size_t)n;return;}
  o->capacity=o->used+(size_t)n+4096;o->data=realloc(o->data,o->capacity);if(!o->data)abort();}
}
static void hex(Output *o,const unsigned char *p,size_t n){append(o,"\"");for(size_t i=0;i<n;i++)append(o,"%02x",p[i]);append(o,"\"");}
static void mark(Output *o,yaml_mark_t m){append(o,"[\"%zu\",\"%zu\",\"%zu\"]",m.index,m.line,m.column);}
static void string(Output *o,const char *p){if(!p){append(o,"null");return;}append(o,"\"");for(;*p;p++){if(*p=='"'||*p=='\\')append(o,"\\%c",*p);else append(o,"%c",*p);}append(o,"\"");}
static void token(Output *o,yaml_token_t *t){
 append(o,"{\"type\":%d,\"start\":",t->type);mark(o,t->start_mark);append(o,",\"end\":");mark(o,t->end_mark);append(o,",\"fields\":[");
 switch(t->type){
 case YAML_STREAM_START_TOKEN:append(o,"%d",t->data.stream_start.encoding);break;
 case YAML_VERSION_DIRECTIVE_TOKEN:append(o,"%d,%d",t->data.version_directive.major,t->data.version_directive.minor);break;
 case YAML_TAG_DIRECTIVE_TOKEN:hex(o,t->data.tag_directive.handle,strlen((char*)t->data.tag_directive.handle));append(o,",");hex(o,t->data.tag_directive.prefix,strlen((char*)t->data.tag_directive.prefix));break;
 case YAML_ALIAS_TOKEN:hex(o,t->data.alias.value,strlen((char*)t->data.alias.value));break;
 case YAML_ANCHOR_TOKEN:hex(o,t->data.anchor.value,strlen((char*)t->data.anchor.value));break;
 case YAML_TAG_TOKEN:hex(o,t->data.tag.handle,strlen((char*)t->data.tag.handle));append(o,",");hex(o,t->data.tag.suffix,strlen((char*)t->data.tag.suffix));break;
 case YAML_SCALAR_TOKEN:hex(o,t->data.scalar.value,t->data.scalar.length);append(o,",%d",t->data.scalar.style);break;
 default:break;}
 append(o,"]}");
}
static void state(Output *o,yaml_parser_t *p,Input *input){
 append(o,"{\"mark\":");mark(o,p->mark);
 append(o,",\"flow\":%d,\"indent\":%d,\"parsed\":\"%zu\",\"allowed\":%s,\"keys\":[",p->flow_level,p->indent,p->tokens_parsed,p->simple_key_allowed?"true":"false");
 for(yaml_simple_key_t *k=p->simple_keys.start;k<p->simple_keys.top;k++){if(k!=p->simple_keys.start)append(o,",");append(o,"[%s,%s,\"%zu\",",k->possible?"true":"false",k->required?"true":"false",k->token_number);mark(o,k->mark);append(o,"]");}
 append(o,"],\"queued\":[");for(yaml_token_t *t=p->tokens.head;t<p->tokens.tail;t++){if(t!=p->tokens.head)append(o,",");token(o,t);}
 append(o,"],\"reader\":{\"encoding\":%d,\"offset\":\"%zu\",\"eof\":%s,\"unread\":%zu,\"buffer\":",p->encoding,p->offset,p->eof?"true":"false",p->unread);
 hex(o,p->buffer.pointer,p->buffer.last-p->buffer.pointer);append(o,",\"raw\":");hex(o,p->raw_buffer.pointer,p->raw_buffer.last-p->raw_buffer.pointer);
 append(o,"},\"calls\":%zu,\"inputPosition\":%zu,\"chunkIndex\":%zu}",input->calls,input->pos,input->index);
}
char *scan_observations(const unsigned char *data,size_t size,const int *chunks,size_t count,int encoding,int limit){
 yaml_parser_t p;if(!yaml_parser_initialize(&p))abort();Input input={data,size,0,chunks,count,0,0};
 yaml_parser_set_input(&p,read_input,&input);if(encoding)yaml_parser_set_encoding(&p,encoding);
 Output o={malloc(4096),0,4096};append(&o,"{\"steps\":[");
 for(int n=0;n<limit;n++){
  yaml_token_t t;int result=yaml_parser_scan(&p,&t);if(n)append(&o,",");
  append(&o,"{\"result\":%s,\"token\":",result?"true":"false");token(&o,&t);append(&o,",\"state\":");state(&o,&p,&input);append(&o,"}");
  int terminal=!result||t.type==YAML_NO_TOKEN;yaml_token_delete(&t);if(terminal)break;
 }
 append(&o,"],\"error\":{\"type\":%d,\"problem\":",p.error);string(&o,p.problem);append(&o,",\"context\":");string(&o,p.context);
 append(&o,",\"problemMark\":");mark(&o,p.problem_mark);append(&o,",\"contextMark\":");mark(&o,p.context_mark);
 append(&o,",\"offset\":\"%zu\",\"value\":%d}}",p.problem_offset,p.problem_value);
 yaml_parser_delete(&p);return o.data;
}
void scan_free(char *p){free(p);}
