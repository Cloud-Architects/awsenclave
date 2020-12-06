package solutions.cloudarchitects.awsenclave.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnclaveMeasurements {
    private static final Pattern PCR_0 = Pattern.compile("\"PCR0\": \"([a-zA-Z0-9]+)\"", Pattern.MULTILINE);
    private static final Pattern PCR_1 = Pattern.compile("\"PCR1\": \"([a-zA-Z0-9]+)\"", Pattern.MULTILINE);
    private static final Pattern PCR_2 = Pattern.compile("\"PCR2\": \"([a-zA-Z0-9]+)\"", Pattern.MULTILINE);

    private final String pcr0;
    private final String pcr1;
    private final String pcr2;

    public EnclaveMeasurements(String pcr0, String pcr1, String pcr2) {
        this.pcr0 = pcr0;
        this.pcr1 = pcr1;
        this.pcr2 = pcr2;
    }

    public String getPcr0() {
        return pcr0;
    }

    public String getPcr1() {
        return pcr1;
    }

    public String getPcr2() {
        return pcr2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnclaveMeasurements that = (EnclaveMeasurements) o;
        return Objects.equals(pcr0, that.pcr0) && Objects.equals(pcr1, that.pcr1) && Objects.equals(pcr2, that.pcr2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pcr0, pcr1, pcr2);
    }

    @Override
    public String toString() {
        return "EnclaveMeasurements{" +
                "pcr0='" + pcr0 + '\'' +
                ", pcr1='" + pcr1 + '\'' +
                ", pcr2='" + pcr2 + '\'' +
                '}';
    }

    public static EnclaveMeasurements fromBuild(String logLines) {
        String pcr0 = extractMeasurement(logLines, PCR_0);
        String pcr1 = extractMeasurement(logLines, PCR_1);
        String pcr2 = extractMeasurement(logLines, PCR_2);

        return new EnclaveMeasurements(pcr0, pcr1, pcr2);
    }

    private static String extractMeasurement(String logLines, Pattern pattern) {
        Matcher matcher = pattern.matcher(logLines);
        matcher.find();
        return matcher.group(1);
    }
}
