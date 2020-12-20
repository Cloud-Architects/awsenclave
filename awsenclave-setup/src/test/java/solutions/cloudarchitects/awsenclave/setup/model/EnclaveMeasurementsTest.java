package solutions.cloudarchitects.awsenclave.setup.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class EnclaveMeasurementsTest {

    @Test
    void onEnclaveDataShouldReturnMeasurements() {
        String logLines = "Last login: Sun Dec  6 10:00:00 2020 from ads\n" +
                "\n" +
                "       __|  __|_  )\n" +
                "       _|  (     /   Amazon Linux 2 AMI\n" +
                "      ___|\\___|___|\n" +
                "\n" +
                "https://aws.amazon.com/amazon-linux-2/\n" +
                "0 package(s) needed for security, out of 0 available\n" +
                "Run \"sudo yum update\" to apply all updates.\n" +
                "nitro-cli build-enclave --docker-uri enclave-image:latest  --output-file sample.eif\n" +
                "exit\n" +
                "test  --output-file sample.eif\n" +
                "Start building the Enclave Image...\n" +
                "Enclave Image successfully created.\n" +
                "{\n" +
                "  \"Measurements\": {\n" +
                "    \"HashAlgorithm\": \"Sha384 { ... }\",\n" +
                "    \"PCR0\": \"4a8f37060e650e549bc4f50ba22a945c4cf606b4e3318684b48c3c77f243e70fb837e0d60a3743ee81a7e84bac43e2bb\",\n" +
                "    \"PCR1\": \"ef5b4f1f63c3fe666bdf4a096bae53439d28a9fa70c33241c08g1479a1b6fa7cff6d100accbe01d766a19e8116b0a2c70\",\n" +
                "    \"PCR2\": \"6402895fdb4c65620633b42c2a0eb7cd085306eaac94bb01530f52665c8438b43a82079478afb1761e2460c946fde3cb\"\n" +
                "  }\n" +
                "}\n" +
                "[ec2-user@ip-00-0-00-0 ~]$ exit\n" +
                "logout";

        EnclaveMeasurements result = EnclaveMeasurements.fromBuild(logLines);
        assertThat(result.getPcr0()).isEqualTo("4a8f37060e650e549bc4f50ba22a945c4cf606b4e3318684b48c3c77f243e70fb837e0d60a3743ee81a7e84bac43e2bb");
        assertThat(result.getPcr1()).isEqualTo("ef5b4f1f63c3fe666bdf4a096bae53439d28a9fa70c33241c08g1479a1b6fa7cff6d100accbe01d766a19e8116b0a2c70");
        assertThat(result.getPcr2()).isEqualTo("6402895fdb4c65620633b42c2a0eb7cd085306eaac94bb01530f52665c8438b43a82079478afb1761e2460c946fde3cb");
    }
}