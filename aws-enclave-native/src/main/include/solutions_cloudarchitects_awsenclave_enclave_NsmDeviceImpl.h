/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl */

#ifndef _Included_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl
#define _Included_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl
#ifdef __cplusplus
extern "C" {
#endif
#undef solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_MAX_NSM_REQUEST_SIZE
#define solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_MAX_NSM_REQUEST_SIZE 4096L
/*
 * Class:     solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl
 * Method:    initialize
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_initialize
  (JNIEnv *, jobject);

/*
 * Class:     solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl
 * Method:    processRequestInternal
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_processRequestInternal
  (JNIEnv *, jobject, jbyteArray);

/*
 * Class:     solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_solutions_cloudarchitects_awsenclave_enclave_NsmDeviceImpl_close
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
