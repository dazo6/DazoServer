package com.dazo66.android;

import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class UploadTransferTest {
    @Test public void preservesBytesPartOrderAndTheSameServerSession() throws Exception {
        byte[] data = new byte[UploadTransfer.CHUNK_BYTES * 2 + 37];
        for (int i = 0; i < data.length; i++) data[i] = (byte)i;
        ByteArrayOutputStream sent = new ByteArrayOutputStream(); AtomicInteger calls = new AtomicInteger();
        ArrayList<Long> progress = new ArrayList<>();
        UploadTransfer.send(new ByteArrayInputStream(data), data.length, () -> {}, (part, index, count, id, report) -> {
            assertEquals(calls.getAndIncrement(), index); assertEquals(3, count);
            assertEquals(index == 0 ? "" : "server-session", id);
            assertEquals(index < 2 ? UploadTransfer.CHUNK_BYTES : 37, part.length);
            report.changed(Math.min(16384, part.length)); sent.write(part); return "server-session";
        }, progress::add);
        assertArrayEquals(data, sent.toByteArray()); assertEquals(3, calls.get());
        assertTrue(progress.contains((long)UploadTransfer.CHUNK_BYTES + 16384));
        assertEquals((long)data.length, (long)progress.get(progress.size()-1));
        for (long n : progress) assertTrue(n >= 0 && n <= data.length);
    }
    @Test public void cancellationStopsBeforeReadingOrSendingAnotherPart() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(); AtomicInteger calls = new AtomicInteger();
        byte[] data = new byte[UploadTransfer.CHUNK_BYTES + 10];
        assertThrows(InterruptedIOException.class, () -> UploadTransfer.send(new ByteArrayInputStream(data), data.length,
            () -> UploadTransfer.checkInterrupted(cancelled.get()), (part, index, count, id, report) -> {
                calls.incrementAndGet(); cancelled.set(true); return "session";
            }, n -> {}));
        assertEquals(1, calls.get());
        InputStream unreadable = new InputStream() { @Override public int read() { throw new AssertionError("Read after cancellation"); } };
        assertThrows(InterruptedIOException.class, () -> UploadTransfer.send(unreadable, 1,
            () -> UploadTransfer.checkInterrupted(true), (a,b,c,d,e) -> { throw new AssertionError("Sent after cancellation"); }, n -> {}));
    }
    @Test public void aCommittedLastResponseWinsACancellationRace() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        UploadTransfer.send(new ByteArrayInputStream(new byte[]{1}), 1, () -> UploadTransfer.checkInterrupted(cancelled.get()),
            (part, index, count, id, report) -> { cancelled.set(true); return ""; }, n -> {});
    }
    @Test public void neverStartsAnotherSessionWhenTheServerOmitsTheFirstPartId() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> UploadTransfer.send(new ByteArrayInputStream(new byte[UploadTransfer.CHUNK_BYTES+1]), UploadTransfer.CHUNK_BYTES+1,
            () -> {}, (a,b,c,d,e) -> { calls.incrementAndGet(); return ""; }, n -> {}));
        assertEquals(1, calls.get());
    }
    @Test public void rejectsTruncatedEmptyAndOversizeSourcesBeforeCommitting() {
        AtomicInteger calls = new AtomicInteger();
        UploadTransfer.Sender sender = (a,b,c,d,e) -> { calls.incrementAndGet(); return ""; };
        assertThrows(EOFException.class, () -> UploadTransfer.send(new ByteArrayInputStream(new byte[]{1}), 2, () -> {}, sender, n -> {}));
        for (long size : new long[]{0, UploadTransfer.MAX_BYTES+1})
            assertThrows(IllegalArgumentException.class, () -> UploadTransfer.send(new ByteArrayInputStream(new byte[0]), size, () -> {}, sender, n -> {}));
        assertEquals(0, calls.get());
    }
}
