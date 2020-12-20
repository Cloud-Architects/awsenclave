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

struct nsm_message_t {
  iovec request;
  iovec response;
} ;

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
    int fd = (int)env->GetIntField(thisObject, fdField);

    if (fd == -1) {
        return;
    }

    int status = close(fd);

    env->SetIntField(thisObject, fdField, -1);

    if (status != 0) {
       env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), ("Error while closing: " + std::to_string(status)).c_str());
    }
}

JNIEXPORT jbyteArray JNICALL Java_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_processRequestInternal
  (JNIEnv *env, jobject thisObject, jbyteArray request) {
    jclass NsmDeviceImplClass = env->FindClass("solutions/cloudarchitects/awsenclave/enclave/NsmDeviceImpl");
    jfieldID fdField = env->GetFieldID(NsmDeviceImplClass, "fd", "I");
    int fd = (int)env->GetIntField(thisObject, fdField);

    char RESPONSE[NSM_RESPONSE_MAX_SIZE];

    jbyte* request_bytes = env->GetByteArrayElements(request, NULL);
    jint request_length = env->GetArrayLength((jarray) request);

    nsm_message_t message;
    message.request.iov_base = (void *) request_bytes;
    message.request.iov_len = (size_t) request_length;
    message.response.iov_base = (void *) &RESPONSE;
    message.response.iov_len = NSM_RESPONSE_MAX_SIZE;

    int status = ioctl(fd, _IOWR(NSM_IOCTL_MAGIC, 0, sizeof(nsm_message_t)), &message);
    env->ReleaseByteArrayElements(request, request_bytes, JNI_ABORT);

    if (status != 0) {
        if (errno == EMSGSIZE) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Input too large");
        } else if (errno == EBADF) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid file descriptor");
        } else if (errno == EFAULT) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Inaccessible memory area reference");
        } else if (errno == EINVAL) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid request");
        } else if (errno == ENOTTY) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Not a character special device");
        } else  {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), ("Error while contacting NSM: " + std::to_string(status)).c_str());
        }
    }

    jbyteArray response = env->NewByteArray((jsize) message.response.iov_len);
    env->SetByteArrayRegion(response, 0, (jsize) message.response.iov_len, (const jbyte*) message.response.iov_base);

    return response;
}