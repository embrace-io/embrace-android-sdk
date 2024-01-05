package io.embrace.android.embracesdk.internal.network.http;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Counts the number of bytes written to the output stream, also captures the request body.
 */
class CountingOutputStream extends FilterOutputStream {
    private long count;

    ByteArrayOutputStream os = new ByteArrayOutputStream();

    /**
     * Wraps another output stream, counting the number of bytes written.
     *
     * @param out the output stream to be wrapped
     */
    public CountingOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * Returns the number of bytes written.
     */
    public long getCount() {
        return count;
    }

    /**
     * Returns the request body written.
     */
    byte[] getRequestBody() {
        return os.toByteArray();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        count += len;
        if (b != null) {
            os.write(b, off, len);
        }
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        count++;
        os.write(b);
    }

    // Overriding close() because FilterOutputStream's close() method pre-JDK8 has bad behavior:
    // it silently ignores any exception thrown by flush(). Instead, just close the delegate stream.
    // It should flush itself if necessary.
    @Override
    public void close() throws IOException {
        out.close();
    }
}