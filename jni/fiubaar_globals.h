// lugar global donde podemos poner definiciones imporantes o variables globales

#ifndef _fiubaar_globals_H
#define _fiubaar_globals_H

#include "native_logcat.h"
#include <stddef.h>
// una idea interesante para trackear errores de heap corruption
// http://stackoverflow.com/questions/15218508/aborting-heap-memory-corruption-on-ndk-env-poco-library-sqlite3-cocos2dx
/*
 * meto estas macros en un lugar a parte para no generar recursividad
#define malloc(x) myMalloc(x, __FILE__,__LINE__,__func__)
#define free(x) myFree(x, __FILE__,__LINE__,__func__)
*/
void* myMalloc(size_t size, const char* filename, const int linenum,  const char* funcname);
void myFree(void* address, const char* filename, const int linenum,  const char* funcname);

#endif
