package solutions.cloudarchitects.awsenclave.model;

import java.util.Objects;

public class Ec2Instance {
    private final String instanceId;
    private final String domainAddress;

    public Ec2Instance(String instanceId, String domainAddress) {
        this.instanceId = instanceId;
        this.domainAddress = domainAddress;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getDomainAddress() {
        return domainAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ec2Instance that = (Ec2Instance) o;
        return Objects.equals(instanceId, that.instanceId) &&
                Objects.equals(domainAddress, that.domainAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, domainAddress);
    }

    @Override
    public String toString() {
        return "Ec2Instance{" +
                "instanceId='" + instanceId + '\'' +
                ", domainAddress='" + domainAddress + '\'' +
                '}';
    }
}
