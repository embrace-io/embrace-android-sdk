package io.embrace.android.embracesdk.network.http;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

import io.embrace.android.embracesdk.utils.Consumer;

/**
 * Counts the bytes read from an input stream and invokes a callback once the stream has reached
 * the end.
 */
final class CountingInputStreamWithCallback extends FilterInputStream {
    /**
     * The mark.
     */
    private volatile long streamMark = -1;
    /**
     * The callback to be invoked with num of bytes after reaching the end of the stream.
     */
    private final Consumer<Long, byte[]> callback;

    /**
     * true if the callback has been invoked, false otherwise.
     */
    private volatile boolean callbackCompleted;

    /**
     * The count of the number of bytes which have been read.
     */
    private final AtomicLong count = new AtomicLong(0);

    private final boolean shouldCaptureBody;

    ByteArrayOutputStream os = new ByteArrayOutputStream();


    /**
     * Wraps another input stream, counting the number of bytes read.
     *
     * @param in the input stream to be wrapped
     */
    CountingInputStreamWithCallback(InputStream in,
                                    boolean shouldCaptureBody,
                                    @NonNull Consumer<Long, byte[]> callback) {
        super(in);
        this.callback = callback;
        this.shouldCaptureBody = shouldCaptureBody;
    }

    /**
     * Returns the number of bytes read.
     */
    public long getCount() {
        return count.longValue();
    }

    @Override
    public int read() throws IOException {
        int result = in.read();
        if (result != -1) {
            count.incrementAndGet();
            byte[] resultByte = {Integer.valueOf(result).byteValue()};
            conditionallyCaptureBody(resultByte, 0, 1);
        } else if (!callbackCompleted) {
            notifyCallback();
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = in.read(b, off, len);
        if (result != -1) {
            count.addAndGet(result);
            conditionallyCaptureBody(b, off, result);
        } else if (!callbackCompleted) {
            notifyCallback();
        }
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        long result = in.skip(n);
        count.addAndGet(result);
        return result;
    }

    @Override
    public synchronized void mark(int readlimit) {
        in.mark(readlimit);
        streamMark = count.longValue();
        // it's okay to mark even if mark isn't supported, as reset won't work
    }

    @Override
    public synchronized void reset() throws IOException {
        if (!in.markSupported()) {
            throw new IOException("Mark not supported");
        }
        if (streamMark == -1) {
            throw new IOException("Mark not set");
        }

        in.reset();
        count.set(streamMark);
        callbackCompleted = false;
    }

    private void conditionallyCaptureBody(byte[] b, int off, int len) {
        if (!shouldCaptureBody) {
            return;
        }

        if (b != null) {
            os.write(b, off, len);
        }
    }

    private void notifyCallback() {
        callbackCompleted = true;
        callback.accept(count.longValue(), os.toByteArray());
    }
}