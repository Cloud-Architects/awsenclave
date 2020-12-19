package solutions.cloudarchitects.awsenclave.enclave.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import solutions.cloudarchitects.awsenclave.enclave.model.response.DescribePCRResponse;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NsmResponse {

    private final DescribePCRResponse DescribePCR;

    public NsmResponse(@JsonProperty("DescribePCR") DescribePCRResponse describePCR) {
        DescribePCR = describePCR;
    }

    public DescribePCRResponse getDescribePCR() {
        return DescribePCR;
    }
}
