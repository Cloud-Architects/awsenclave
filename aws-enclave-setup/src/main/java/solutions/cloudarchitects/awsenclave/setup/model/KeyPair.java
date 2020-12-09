package solutions.cloudarchitects.awsenclave.setup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class KeyPair {
    final String keyMaterial;
    final String keyName;

    public KeyPair(@JsonProperty("keyMaterial") String keyMaterial, @JsonProperty("keyName") String keyName) {
        this.keyMaterial = keyMaterial;
        this.keyName = keyName;
    }

    public String getKeyMaterial() {
        return keyMaterial;
    }

    public String getKeyName() {
        return keyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyPair keyPair = (KeyPair) o;
        return Objects.equals(keyMaterial, keyPair.keyMaterial) && Objects.equals(keyName, keyPair.keyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyMaterial, keyName);
    }

    @Override
    public String toString() {
        return "KeyPair{" +
                "keyMaterial='" + keyMaterial + '\'' +
                ", keyName='" + keyName + '\'' +
                '}';
    }
}
