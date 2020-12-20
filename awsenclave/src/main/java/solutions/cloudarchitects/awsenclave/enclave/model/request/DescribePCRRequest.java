package solutions.cloudarchitects.awsenclave.enclave.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DescribePCRRequest {
    private final short index;

    public DescribePCRRequest(@JsonProperty("index") short index) {
        this.index = index;
    }

    public short getIndex() {
        return index;
    }
}
