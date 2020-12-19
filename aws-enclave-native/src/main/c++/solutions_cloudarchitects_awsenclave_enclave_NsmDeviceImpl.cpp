#include <iostream>
#include <cstring>

#include <sys/ioctl.h>
#include <sys/uio.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

#include <jni.h>
#include <solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl.h>

#define NSM_IOCTL_MAGIC 0x0A
#define NSM_RESPONSE_MAX_SIZE 0x3000

JNIEXPORT void JNICALL Java_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_initialize
  (JNIEnv *env, jobject thisObject) {
    int fd = open("/dev/nsm", O_RDWR);
    jclass NsmDeviceImplClass = env->FindClass("solutions/cloudarchitects/awsenclave/enclave/NsmDeviceImpl");
    jfieldID fdField = env->GetFieldID(NsmDeviceImplClass, "fd", "I");
    env->SetIntField(thisObject, fdField, fd);
}

JNIEXPORT void JNICALL Java_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_close
  (JNIEnv *env, jobject thisObject) {
    jclass NsmDeviceImplClass = env->FindClass("solutions/cloudarchitects/awsenclave/enclave/NsmDeviceImpl");
    jfieldID fdField = env->GetFieldID(NsmDeviceImplClass, "fd", "I");
    int s = (int)env->GetIntField(thisObject, fdField);

    if (s == -1) {
        return;
    }

    int status = close(s);

    env->SetIntField(thisObject, fdField, -1);

    if (status != 0) {
       env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), ("Error while closing: " + std::to_string(status)).c_str());
    }
}