package com.dazo66.service;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtistImageCacheTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void downloadsImageToContentAddressedFile() throws Exception {
        byte[] content = "cached-image".getBytes(StandardCharsets.UTF_8);
        Request request = new Request.Builder().url("https://img.example/icon").build();
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("image/png"), content))
                .build());
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(Request.class))).thenReturn(call);
        ArtistImageCache cache = new ArtistImageCache(temporaryDirectory);

        String fileName = cache.download(client, request, 42, "icon");

        assertThat(fileName).matches("artist-42-icon-[0-9a-f]{32}\\.png");
        assertThat(Files.readAllBytes(temporaryDirectory.resolve(fileName))).isEqualTo(content);
        assertThat(ArtistImageCache.publicUrl(fileName))
                .isEqualTo("/api/fanbox/artist-image/" + fileName);
    }

    @Test
    void detectsJpegWhenSourceUsesOctetStreamContentType() throws Exception {
        byte[] content = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3};
        Request request = new Request.Builder().url("https://img.example/banner").build();
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/octet-stream"), content))
                .build());
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(Request.class))).thenReturn(call);
        ArtistImageCache cache = new ArtistImageCache(temporaryDirectory);

        String fileName = cache.download(client, request, 42, "banner");

        assertThat(fileName).matches("artist-42-banner-[0-9a-f]{32}\\.jpg");
        assertThat(Files.readAllBytes(temporaryDirectory.resolve(fileName))).isEqualTo(content);
    }

    @Test
    void rejectsNonImageOctetStreamResponse() throws Exception {
        byte[] content = "<html>upstream error</html>".getBytes(StandardCharsets.UTF_8);
        Request request = new Request.Builder().url("https://img.example/banner").build();
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/octet-stream"), content))
                .build());
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(Request.class))).thenReturn(call);
        ArtistImageCache cache = new ArtistImageCache(temporaryDirectory);

        assertThatThrownBy(() -> cache.download(client, request, 42, "banner"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("unsupported content type");
        assertThat(temporaryDirectory.toFile().list()).isEmpty();
    }

    @Test
    void rejectsExternalAndTraversalFileNames() {
        ArtistImageCache cache = new ArtistImageCache(temporaryDirectory);

        assertThat(ArtistImageCache.publicUrl("https://img.example/icon.jpg")).isNull();
        assertThat(cache.resolve("../artist-1-icon-0123456789abcdef0123456789abcdef.jpg")).isNull();
    }
}
