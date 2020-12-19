package solutions.cloudarchitects.awsenclave.enclave.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DescribePCRResponse {
    private final boolean lock;
    private final byte[] data;

    public DescribePCRResponse(@JsonProperty("lock") boolean lock, @JsonProperty("data") byte[] data) {
        this.lock = lock;
        this.data = data;
    }

    public boolean isLock() {
        return lock;
    }

    public byte[] getData() {
        return data;
    }
}
