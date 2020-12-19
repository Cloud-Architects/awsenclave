package solutions.cloudarchitects.awsenclave.enclave;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import solutions.cloudarchitects.awsenclave.enclave.model.NsmRequest;
import solutions.cloudarchitects.awsenclave.enclave.model.NsmResponse;

import java.io.IOException;

public final class NsmDeviceImpl {

    public static final int MAX_NSM_REQUEST_SIZE = 4096;

    static {
        System.loadLibrary("aws-enclave-native-" + AwsEnclave.VERSION);
    }
    private final ObjectMapper mapper;

    int fd = -1;

    NsmDeviceImpl() {
        CBORFactory f = new CBORFactory();
        this.mapper = new ObjectMapper(f);
    }

    public NsmResponse processRequest(NsmRequest request) {
        byte[] responseBinary;
        try {
            byte[] requestBinary = mapper.writeValueAsBytes(request);
            if (requestBinary.length >= MAX_NSM_REQUEST_SIZE) {
                throw new IllegalArgumentException(
                        String.format("Too big NSM request, max request size is %d, got %d", MAX_NSM_REQUEST_SIZE,
                                requestBinary.length));
            }
            responseBinary = processRequestInternal(requestBinary);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid NSM request: " + e.getMessage(), e);
        }
        try {
            return mapper.readValue(responseBinary, NsmResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid response from NSM device: " + e.getMessage(), e);
        }
    }

    native void initialize();
    native byte[] processRequestInternal(byte[] request);
    native void close();
}
