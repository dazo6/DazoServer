package com.dazo66.android;

import java.net.HttpURLConnection;
import java.io.InputStream;

final class UploadControl {
    volatile boolean stopped;
    volatile HttpURLConnection connection;
    volatile InputStream source;
    void check() throws Exception { UploadTransfer.checkInterrupted(stopped); }
    void stop() {
        stopped = true;
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
        InputStream input = source;
        if (input != null) try { input.close(); } catch (Exception ignored) {}
    }
}
