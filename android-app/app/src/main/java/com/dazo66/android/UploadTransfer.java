package com.dazo66.android;

import java.io.InputStream;
import java.io.EOFException;
import java.io.InterruptedIOException;

/** The server permits one chunk session per user. Transfer files sequentially in bounded parts. */
final class UploadTransfer {
    static final int CHUNK_BYTES = 512 * 1024;
    static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;
    interface Cancellation { void check() throws Exception; }
    interface Progress { void changed(long bytes); }
    interface Sender { String send(byte[] part, int index, int count, String uploadId, Progress progress) throws Exception; }
    static void send(InputStream input, long size, Cancellation cancellation, Sender sender, Progress progress) throws Exception {
        if (size < 1 || size > MAX_BYTES) throw new IllegalArgumentException("文件大小必须在1字节到2GB之间");
        int count = (int)((size + CHUNK_BYTES - 1) / CHUNK_BYTES);
        String uploadId = ""; long completed = 0;
        for (int index = 0; index < count; index++) {
            cancellation.check();
            int wanted = (int)Math.min(CHUNK_BYTES, size - completed), offset = 0;
            byte[] part = new byte[wanted];
            while (offset < wanted) {
                cancellation.check();
                int n = input.read(part, offset, wanted - offset);
                if (n < 0) throw new EOFException("文件读取中断");
                offset += n;
            }
            cancellation.check();
            final long base = completed;
            uploadId = sender.send(part, index, count, uploadId, bytes -> progress.changed(base + Math.min(wanted, bytes)));
            if (count > 1 && index < count - 1 && (uploadId == null || uploadId.isEmpty()))
                throw new IllegalStateException("服务器未返回上传会话");
            completed += wanted;
            progress.changed(completed);
        }
        // A successful last response means the file was committed, even if cancel was tapped concurrently.
    }
    static void checkInterrupted(boolean cancelled) throws InterruptedIOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("上传已中断");
    }
}
