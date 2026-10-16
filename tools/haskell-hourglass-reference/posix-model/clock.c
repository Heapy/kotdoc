#include <stdint.h>
#include <string.h>
static int64_t sec_value, ns_value, zone_value;
static int tm_values[6], gm_ok, local_ok;
static int kinds[10000], count;
static int64_t args[10000];
static void record(int kind, int64_t arg) { if (count<10000) { kinds[count]=kind; args[count]=arg; ++count; } }
void hg_config(int64_t sec, int64_t ns, int64_t zone, int gm, int local,
               int a,int b,int c,int d,int e,int f) {
 sec_value=sec; ns_value=ns; zone_value=zone; gm_ok=gm; local_ok=local;
 tm_values[0]=a;tm_values[1]=b;tm_values[2]=c;tm_values[3]=d;tm_values[4]=e;tm_values[5]=f;count=0;
}
void *gmtime_r(const void *input,void *output) {
 int64_t s;memcpy(&s,input,8);record(1,s);
 if (!gm_ok) return 0;
 memcpy(output,tm_values,24);return output;
}
void *localtime_r(const void *input,void *output) {
 int64_t s;memcpy(&s,input,8);record(2,s);
 if (!local_ok) return 0;
 memcpy((char*)output+40,&zone_value,8);return output;
}
void hourglass_clock_calendar(void *output) {
 record(3,0);memcpy(output,&sec_value,8);memcpy((char*)output+8,&ns_value,8);
}
int hg_trace_count(void) { return count; }
int hg_trace_kind(int i) { return kinds[i]; }
int64_t hg_trace_arg(int i) { return args[i]; }
