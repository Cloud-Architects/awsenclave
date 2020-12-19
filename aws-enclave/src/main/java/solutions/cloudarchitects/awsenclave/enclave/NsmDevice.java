package solutions.cloudarchitects.awsenclave.enclave;

import solutions.cloudarchitects.awsenclave.enclave.model.NsmRequest;
import solutions.cloudarchitects.awsenclave.enclave.model.NsmResponse;
import solutions.cloudarchitects.awsenclave.enclave.model.request.DescribePCRRequest;
import solutions.cloudarchitects.awsenclave.enclave.model.response.DescribePCRResponse;

import java.io.Closeable;
import java.io.IOException;

public final class NsmDevice implements Closeable {
    private boolean closed = false;
    private boolean initialized = false;

    private NsmDeviceImpl implementation;

    public void initialize() {
        if (!initialized) {
            implementation = new NsmDeviceImpl();
            implementation.initialize();
        }
    }

    public DescribePCRResponse describePCR(DescribePCRRequest request) {
        checkState();

        NsmResponse nsmResponse = implementation.processRequest(new NsmRequest(request));

        return nsmResponse.getDescribePCR();
    }

    private void checkState() {
        if (isClosed()) {
            throw new IllegalStateException("Device closed");
        }
        if (!initialized) {
            throw new IllegalStateException("Device not initialized");
        }
    }

    @Override
    public synchronized void close(){
        if (isClosed()) {
            return;
        }
        if (initialized) {
            implementation.close();
        }
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
