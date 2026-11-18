package com.dazo66.android;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Stream and seek with the same account routing as API requests, without buffering the whole video. */
@androidx.media3.common.util.UnstableApi
final class AuthenticatedMediaSource extends BaseDataSource {
    private final ApiClient api;
    private ApiClient.HttpStream stream;
    private long remaining;
    AuthenticatedMediaSource(ApiClient api) { super(true); this.api = api; }
    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);
        try { stream = api.openStream(spec.uri.toString(), spec.position, spec.length); }
        catch (Exception error) { throw error instanceof IOException ? (IOException)error : new IOException(error); }
        remaining = stream.length; transferStarted(spec); return remaining;
    }
    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (remaining == 0) return C.RESULT_END_OF_INPUT;
        int count;
        try { count = stream.input.read(buffer, offset, remaining < 0 ? length : (int)Math.min(length, remaining)); }
        catch (IOException error) { api.streamFailed(stream); throw error; }
        if (count < 0) return C.RESULT_END_OF_INPUT;
        if (remaining >= 0) remaining -= count;
        bytesTransferred(count); return count;
    }
    @Override public Uri getUri() { return stream == null ? null : Uri.parse(stream.address); }
    @Override public Map<String, List<String>> getResponseHeaders() { return stream == null ? Collections.emptyMap() : stream.connection.getHeaderFields(); }
    @Override public void close() throws IOException {
        if (stream == null) return;
        try { stream.close(); } finally { stream = null; transferEnded(); }
    }
}
