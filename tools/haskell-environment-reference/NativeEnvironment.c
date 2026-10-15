#ifdef __APPLE__
#include <crt_externs.h>
char ***kotdoc_environ_address(void) { return _NSGetEnviron(); }
#else
extern char **environ;
char ***kotdoc_environ_address(void) { return &environ; }
#endif
#include <locale.h>
const char *kotdoc_locale_name(void) { return setlocale(LC_CTYPE, NULL); }
