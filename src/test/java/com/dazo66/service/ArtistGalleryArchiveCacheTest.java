package com.dazo66.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class ArtistGalleryArchiveCacheTest {
    @TempDir Path temporary;
    @Test void recursivelyPackagesAllFileTypesAndReusesCachedSnapshot() throws Exception {
        Path folder=Files.createDirectories(temporary.resolve("gallery"));
        Files.write(folder.resolve("first.txt"),new byte[]{1,2});
        Path sub=Files.createDirectories(folder.resolve("nested"));
        Files.write(sub.resolve("图片.bin"),new byte[]{3,4,5});
        ArtistGalleryArchiveCache cache=new ArtistGalleryArchiveCache(temporary.resolve("archives"));
        Path archive=cache.getOrBuild(folder.toRealPath());
        Map<String,byte[]> content=readZip(archive);
        assertThat(content.keySet()).containsExactlyInAnyOrder("first.txt","nested/图片.bin");
        assertThat(content.get("nested/图片.bin")).containsExactly((byte)3,(byte)4,(byte)5);
        byte[] bytes=Files.readAllBytes(archive);
        long modified=Files.getLastModifiedTime(archive).toMillis();
        Files.delete(folder.resolve("first.txt"));Files.write(sub.resolve("new.txt"),new byte[]{9});
        assertThat(cache.getOrBuild(folder.toRealPath())).isEqualTo(archive);
        assertThat(Files.readAllBytes(archive)).isEqualTo(bytes);
        assertThat(Files.getLastModifiedTime(archive).toMillis()).isEqualTo(modified);
    }
    @Test void accepts300FilesButRejects301WithoutPartialCache() throws Exception {
        Path folder=Files.createDirectories(temporary.resolve("three-hundred"));
        for(int i=0;i<300;i++) Files.write(folder.resolve(i+".txt"),new byte[0]);
        ArtistGalleryArchiveCache cache=new ArtistGalleryArchiveCache(temporary.resolve("archives"));
        assertThat(readZip(cache.getOrBuild(folder.toRealPath()))).hasSize(300);
        Path tooMany=Files.createDirectories(temporary.resolve("too-many"));
        for(int i=0;i<301;i++) Files.write(tooMany.resolve(i+".txt"),new byte[0]);
        assertThatThrownBy(()->cache.getOrBuild(tooMany.toRealPath())).hasMessageContaining("413");
        assertThat(cache.archiveFile(tooMany)).doesNotExist();
    }
    @Test void rejectsTotalUncompressedSizeOver500MbBeforeWriting() throws Exception {
        Path folder=Files.createDirectories(temporary.resolve("large"));
        for(String name:Arrays.asList("a.bin","b.bin")) try(RandomAccessFile file=new RandomAccessFile(folder.resolve(name).toFile(),"rw")){file.setLength(250L*1024*1024+1);}
        ArtistGalleryArchiveCache cache=new ArtistGalleryArchiveCache(temporary.resolve("archives"));
        assertThatThrownBy(()->cache.getOrBuild(folder.toRealPath())).hasMessageContaining("413");
        assertThat(cache.archiveFile(folder)).doesNotExist();
        assertThat(temporary.resolve("archives")).doesNotExist();
    }
    @Test void rejectsEmptyFolder() throws Exception {
        Path folder=Files.createDirectories(temporary.resolve("empty"));
        ArtistGalleryArchiveCache cache=new ArtistGalleryArchiveCache(temporary.resolve("archives"));
        assertThatThrownBy(()->cache.getOrBuild(folder.toRealPath())).hasMessageContaining("400");
    }
    @Test void concurrentClicksPublishOneCompleteArchive() throws Exception {
        Path folder=Files.createDirectories(temporary.resolve("concurrent"));
        Files.write(folder.resolve("image.bin"),new byte[1024*1024]);
        ArtistGalleryArchiveCache cache=new ArtistGalleryArchiveCache(temporary.resolve("archives"));
        ExecutorService executor=Executors.newFixedThreadPool(4);
        try {
            List<Future<Path>> requests=new ArrayList<>();
            for(int i=0;i<4;i++) requests.add(executor.submit(()->cache.getOrBuild(folder.toRealPath())));
            for(Future<Path> request:requests) assertThat(readZip(request.get(10,TimeUnit.SECONDS)).get("image.bin")).hasSize(1024*1024);
            try(java.util.stream.Stream<Path> paths=Files.list(temporary.resolve("archives"))){assertThat(paths.count()).isEqualTo(1);}
        } finally {executor.shutdownNow();}
    }
    @Test void refusesSymbolicLinksToFilesOutsideGallery() throws Exception {
        Path folder=Files.createDirectories(temporary.resolve("links"));
        Path secret=Files.write(temporary.resolve("secret.txt"),new byte[]{1});
        try {Files.createSymbolicLink(folder.resolve("secret.txt"),secret);}
        catch(IOException|UnsupportedOperationException e){org.junit.jupiter.api.Assumptions.assumeTrue(false,"OS does not allow symbolic link fixture");}
        ArtistGalleryArchiveCache cache=new ArtistGalleryArchiveCache(temporary.resolve("archives"));
        assertThatThrownBy(()->cache.getOrBuild(folder.toRealPath())).hasMessageContaining("400");
        assertThat(cache.archiveFile(folder)).doesNotExist();
    }
    private Map<String,byte[]> readZip(Path archive) throws Exception {
        Map<String,byte[]> result=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(archive),java.nio.charset.StandardCharsets.UTF_8)) {
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){ByteArrayOutputStream output=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1)output.write(buffer,0,n);result.put(entry.getName(),output.toByteArray());}
        }
        return result;
    }
}
