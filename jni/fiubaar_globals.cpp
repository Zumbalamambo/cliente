#include "fiubaar_globals.h"

#include <stdio.h>
#include <sstream>
#include <string>
using namespace std;

// http://stackoverflow.com/questions/13196013/why-application-is-dying-randomly

void* myMalloc(size_t size, const char* filename, const int linenum,  const char* funcname) {
	std::stringstream ss;
	std::string tmp;
	ss << "[MALLOC] - size = " << size << " - file = " << filename <<
	" - line = " << linenum << " - function = " << funcname << endl;
	tmp = ss.str();
	LOGD("CUSTOM_ALLOCATOR", tmp.c_str());
	return  malloc(size);
	//return 0;
}


void myFree(void* address, const char* filename, const int linenum,  const char* funcname) {
	std::stringstream ss;
	std::string tmp;
	ss << "[FREE] - address = " << address << " - file = " << filename <<
	" - line = " << linenum << " - function = " << funcname << endl;
	tmp = ss.str();
	LOGD("CUSTOM_ALLOCATOR", tmp.c_str());
	free(address);
}
