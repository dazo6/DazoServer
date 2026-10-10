package com.dazo66.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.*;

/** Cached folder snapshots. Only completed ZIPs become visible to readers. */
public final class ArtistGalleryArchiveCache {
    static final long MAX_BYTES=500L*1024*1024;
    static final int MAX_FILES=300;
    private static final Object[] LOCKS=new Object[64];
    private static final Semaphore BUILD_SLOTS=new Semaphore(2,true);
    static { for(int i=0;i<LOCKS.length;i++) LOCKS[i]=new Object(); }
    private final Path cacheRoot;
    public ArtistGalleryArchiveCache(Path cacheRoot) {this.cacheRoot=cacheRoot.toAbsolutePath().normalize();}
    public Path archiveFile(Path directory) {
        String key=directory.toAbsolutePath().normalize().toString();
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")) key=key.toLowerCase(Locale.ROOT);
        return cacheRoot.resolve("folder-"+UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))+".zip");
    }
    public Path getOrBuild(Path directory) throws IOException {
        Path target=archiveFile(directory);
        synchronized(LOCKS[Math.floorMod(target.hashCode(),LOCKS.length)]) {
            if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)) return target;
            try {BUILD_SLOTS.acquire();}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Archive build interrupted");}
            try {return build(directory,target);} finally {BUILD_SLOTS.release();}
        }
    }
    private Path build(Path directory,Path target) throws IOException {
        List<Path> files=new ArrayList<>();
        final long[] bytes={0};
        Files.walkFileTree(directory,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                if(!dir.toRealPath().startsWith(directory)) throw invalidLink();
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                if(!attrs.isRegularFile() || attrs.isSymbolicLink() || !file.toRealPath().startsWith(directory)) throw invalidLink();
                files.add(file);
                if(files.size()>MAX_FILES) throw limit("最多支持 300 个文件");
                if(attrs.size()>MAX_BYTES-bytes[0]) throw limit("文件总大小不能超过 500 MB");
                bytes[0]+=attrs.size();return FileVisitResult.CONTINUE;
            }
        });
        if(files.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前文件夹没有可下载的文件");
        files.sort(Comparator.comparing(file->directory.relativize(file).toString()));
        Files.createDirectories(cacheRoot);
        Path temporary=Files.createTempFile(cacheRoot,"archive-",".tmp");
        try {
            long copied=0;
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)),StandardCharsets.UTF_8)) {
                zip.setLevel(Deflater.BEST_SPEED);
                byte[] buffer=new byte[65536];
                for(Path file:files) {
                    if(!file.toRealPath().startsWith(directory)) throw invalidLink();
                    String name=directory.relativize(file).toString().replace(File.separatorChar,'/');
                    ZipEntry entry=new ZipEntry(name);entry.setTime(Files.getLastModifiedTime(file,LinkOption.NOFOLLOW_LINKS).toMillis());
                    zip.putNextEntry(entry);
                    try(InputStream input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
                        int count;
                        while((count=input.read(buffer))!=-1) {
                            copied+=count;
                            if(copied>MAX_BYTES) throw limit("文件总大小不能超过 500 MB");
                            zip.write(buffer,0,count);
                        }
                    }
                    zip.closeEntry();
                }
            }
            try {Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,target);}
            return target;
        } finally {Files.deleteIfExists(temporary);}
    }
    private static ResponseStatusException limit(String reason){return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,reason);}
    private static ResponseStatusException invalidLink(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件夹包含链接或非普通文件，无法打包");}
}
