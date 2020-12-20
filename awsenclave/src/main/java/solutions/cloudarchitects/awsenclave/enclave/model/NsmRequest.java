package solutions.cloudarchitects.awsenclave.enclave.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import solutions.cloudarchitects.awsenclave.enclave.model.request.DescribePCRRequest;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NsmRequest {

    private final DescribePCRRequest DescribePCR;

    public NsmRequest(@JsonProperty("DescribePCR") DescribePCRRequest describePCR) {
        DescribePCR = describePCR;
    }

    public DescribePCRRequest getDescribePCR() {
        return DescribePCR;
    }
}
