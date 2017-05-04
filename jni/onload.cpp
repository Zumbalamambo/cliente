#include <string.h>
#include <assert.h>
#include "jni.h"
#include "native_logcat.h"

namespace fiubaar {
    int register_Signal(JNIEnv* env);
};

using namespace fiubaar;

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("JNI_OnLoad", "GetEnv failed!");
        return result;
    }
    assert(env != NULL);
    if (!register_Signal(env)) {
        LOGE("JNI_OnLoad", "ERROR: Test native registration failed");
        return result;
    }
    /* success -- return valid version number */
    return JNI_VERSION_1_4;
}
