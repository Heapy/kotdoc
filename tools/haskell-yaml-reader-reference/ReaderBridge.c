/* Observer/transport only; decoder and buffering execute unchanged libyaml. */
#include "yaml.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
extern int yaml_parser_update_buffer(yaml_parser_t *,size_t);
typedef struct {
 const unsigned char *input; size_t size, position;
 const int *chunks; size_t chunks_size, chunk_index, calls, last_request;
} Input;
static int read_input(void *data, unsigned char *buffer,size_t maximum,size_t *size_read) {
 Input *s=data; s->calls++;s->last_request=maximum;
 int chunk=s->chunk_index<s->chunks_size?s->chunks[s->chunk_index++]:16384;
 if(chunk<0)return 0;
 size_t n=(size_t)chunk;
 if(n>maximum)n=maximum;
 if(n>s->size-s->position)n=s->size-s->position;
 memcpy(buffer,s->input+s->position,n);s->position+=n;*size_read=n;return 1;
}
typedef struct { char *data;size_t used,capacity; } Output;
static void append(Output *s,const char *format,...) {
 for(;;){
  va_list args;va_start(args,format);
  int n=vsnprintf(s->data+s->used,s->capacity-s->used,format,args);va_end(args);
  if(n<0)abort();
  if((size_t)n<s->capacity-s->used){s->used+=(size_t)n;return;}
  s->capacity=s->used+(size_t)n+1024;
  s->data=realloc(s->data,s->capacity);if(!s->data)abort();
 }
}
static void hex(Output *s,const unsigned char *start,const unsigned char *end) {
 append(s,"\"");for(;start<end;start++)append(s,"%02x",*start);append(s,"\"");
}
static void snapshot(Output *o,yaml_parser_t *p,Input *i,int result) {
 append(o,"{\"result\":%s,\"encoding\":%d,\"offset\":\"%zu\",\"eof\":%s,\"unread\":%zu,\"buffer\":",result?"true":"false",p->encoding,p->offset,p->eof?"true":"false",p->unread);
 hex(o,p->buffer.pointer,p->buffer.last);append(o,",\"raw\":");hex(o,p->raw_buffer.pointer,p->raw_buffer.last);
 append(o,",\"calls\":%zu,\"inputPosition\":%zu,\"chunkIndex\":%zu,\"lastRequest\":%zu,\"failure\":",i->calls,i->position,i->chunk_index,i->last_request);
 if(p->error==YAML_READER_ERROR)append(o,"[\"%s\",\"%zu\",%d]",p->problem,p->problem_offset,p->problem_value);else append(o,"null");
 append(o,"}");
}
char *reader_observations(const unsigned char *bytes,size_t size,int encoding,uint64_t offset,const int *chunks,size_t chunks_size,const int *steps,size_t step_count) {
 yaml_parser_t p;if(!yaml_parser_initialize(&p))abort();
 Input input={bytes,size,0,chunks,chunks_size,0,0,0};
 yaml_parser_set_input(&p,read_input,&input);
 if(encoding)yaml_parser_set_encoding(&p,encoding);
 p.offset=(size_t)offset;
 Output o={malloc(1024),0,1024};append(&o,"[");
 snapshot(&o,&p,&input,1);
 for(size_t k=0;k<step_count;k++){
  int op=steps[2*k],n=steps[2*k+1],result=1;
  if(op==0)result=yaml_parser_update_buffer(&p,(size_t)n);
  else{
   for(int j=0;j<n&&p.unread;j++){
    unsigned char b=*p.buffer.pointer;
    p.buffer.pointer+=b<128?1:b<224?2:b<240?3:4;p.unread--;
   }
  }
  append(&o,",");snapshot(&o,&p,&input,result);
  if(!result)break;
 }
 append(&o,"]");yaml_parser_delete(&p);return o.data;
}
void reader_free(char *p){free(p);}

/* Input construction only; each result executes the actual complete reader. */
const char *reader_codepoint_problem(uint32_t point,int *value) {
 unsigned char bytes[4];size_t n;
 if(point<128){bytes[0]=(unsigned char)point;n=1;}
 else if(point<2048){bytes[0]=192+(point>>6);bytes[1]=128+(point&63);n=2;}
 else if(point<65536){bytes[0]=224+(point>>12);bytes[1]=128+((point>>6)&63);bytes[2]=128+(point&63);n=3;}
 else{bytes[0]=240+(point>>18);bytes[1]=128+((point>>12)&63);bytes[2]=128+((point>>6)&63);bytes[3]=128+(point&63);n=4;}
 yaml_parser_t p;if(!yaml_parser_initialize(&p))abort();
 yaml_parser_set_encoding(&p,YAML_UTF8_ENCODING);yaml_parser_set_input_string(&p,bytes,n);
 int result=yaml_parser_update_buffer(&p,1);
 const char *problem=result?NULL:p.problem;*value=p.problem_value;
 yaml_parser_delete(&p);return problem;
}
const char *reader_utf16_problem(uint32_t unit,int encoding,int *value) {
 unsigned char bytes[2];int low=encoding==YAML_UTF16LE_ENCODING?0:1;
 bytes[low]=unit&255;bytes[1-low]=unit>>8;
 yaml_parser_t p;if(!yaml_parser_initialize(&p))abort();
 yaml_parser_set_encoding(&p,encoding);yaml_parser_set_input_string(&p,bytes,2);
 int result=yaml_parser_update_buffer(&p,1);
 const char *problem=result?NULL:p.problem;*value=p.problem_value;
 yaml_parser_delete(&p);return problem;
}
