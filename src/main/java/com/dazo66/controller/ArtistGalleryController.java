package com.dazo66.controller;

import com.dazo66.security.*;
import com.dazo66.dashboard.ArtistGalleryData;
import com.dazo66.dashboard.ArtistGalleryItem;
import com.dazo66.service.ArtistGalleryCache;
import com.dazo66.service.FreeGalleryService;
import com.dazo66.util.ResultEntity;
import net.coobird.thumbnailator.Thumbnails;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves downloaded images and videos for a folder-based gallery.
 */
@RestController
@RequestMapping("api/fanbox")
public class ArtistGalleryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtistGalleryController.class);
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif", "jfif"
    ));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "webm", "mov", "m4v", "ogv", "ogg", "mkv", "avi"
    ));
    private static final Object[] THUMBNAIL_LOCKS = thumbnailLocks();
    private static final Semaphore VIDEO_THUMBNAIL_SLOTS = new Semaphore(2);
    private static final int THUMBNAIL_MAX_WIDTH = 600;
    private static final int THUMBNAIL_MAX_HEIGHT = 1200;
    private static final Pattern LEADING_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

    private final ArtistGalleryCache galleryCache;
    private final FreeGalleryService freeGalleryService;
    private final Path downloadRoot;
    private final Path thumbnailCacheRoot;
    private final com.dazo66.service.ArtistGalleryArchiveCache archiveCache;

    @Autowired
    public ArtistGalleryController(@Value("${fanbox.save.path}") String downloadPath,
                                   ArtistGalleryCache galleryCache,
                                   FreeGalleryService freeGalleryService) {
        this(downloadPath,
                Paths.get("").toAbsolutePath().normalize().resolve("cache").resolve("gallery-thumbnails"),
                galleryCache, freeGalleryService);
    }

    ArtistGalleryController(String downloadPath,
                            Path thumbnailCacheRoot) {
        this(downloadPath, thumbnailCacheRoot,
                new ArtistGalleryCache(Paths.get(downloadPath),
                        thumbnailCacheRoot.resolveSibling("gallery-indexes"), new com.fasterxml.jackson.databind.ObjectMapper()),
                null);
    }

    ArtistGalleryController(String downloadPath,
                            Path thumbnailCacheRoot,
                            ArtistGalleryCache galleryCache) {
        this(downloadPath, thumbnailCacheRoot, galleryCache, null);
    }

    ArtistGalleryController(String downloadPath,
                            Path thumbnailCacheRoot,
                            ArtistGalleryCache galleryCache,
                            FreeGalleryService freeGalleryService) {
        this.galleryCache = galleryCache;
        this.freeGalleryService = freeGalleryService;
        this.downloadRoot = Paths.get(downloadPath).toAbsolutePath().normalize();
        this.thumbnailCacheRoot = thumbnailCacheRoot.toAbsolutePath().normalize();
        this.archiveCache = new com.dazo66.service.ArtistGalleryArchiveCache(this.thumbnailCacheRoot.resolveSibling("gallery-archives"));
    }

    @org.springframework.web.bind.annotation.PostMapping("gallery/upload")
    @ACL(type=AclType.UPDATE,point="$arg1")
    public ResponseEntity<ResultEntity<String>> uploadGalleryFile(@RequestParam String folderPath,
            @RequestParam org.springframework.web.multipart.MultipartFile file) throws IOException {
        Path directory = galleryDirectory(folderPath);
        if(directory==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"画廊目录不存在");
        AclAccess.checkPath(AclType.UPDATE,directory.toString());
        if(file.isEmpty()) throw new IllegalArgumentException("请选择非空文件");
        if(file.getSize()>500L*1024*1024) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,"文件最大500MB");
        String name=file.getOriginalFilename();
        if(name==null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || name.contains(":") || name.endsWith(".") || name.endsWith(" ") || name.chars().anyMatch(c->c<32))
            throw new IllegalArgumentException("文件名无效");
        Path target=directory.resolve(name);
        AclAccess.checkPath(AclType.UPDATE,target.toString());
        Path temporary=Files.createTempFile(directory,".gallery-upload-",".part");
        try {
            try(java.io.InputStream input=file.getInputStream();java.io.OutputStream output=Files.newOutputStream(temporary)) {
                byte[] buffer=new byte[65536];long total=0;int count;
                while((count=input.read(buffer))!=-1) {
                    total+=count;
                    if(total>500L*1024*1024) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,"文件最大500MB");
                    output.write(buffer,0,count);
                }
            }
            Files.move(temporary,target);
        } catch(FileAlreadyExistsException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"同名文件已存在，请更改文件名后上传");
        } finally {Files.deleteIfExists(temporary);}
        galleryCache.rebuildAuthorized(directory.toString());
        return ResponseEntity.ok(ResultEntity.successWithData(name));
    }

    private static final long UPLOAD_LIMIT=500L*1024*1024;
    private static final int CHUNK_SIZE=512*1024;
    private static final long UPLOAD_IDLE_MS=15L*60*1000;
    private final java.util.concurrent.ConcurrentHashMap<String,ChunkUpload> chunkUploads=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class ChunkUpload {
        final String userId,name;final Path directory,temporary;final long totalSize;final int partCount;
        int nextPart;long touched=System.currentTimeMillis();
        ChunkUpload(String userId,String name,Path directory,Path temporary,long totalSize,int partCount){this.userId=userId;this.name=name;this.directory=directory;this.temporary=temporary;this.totalSize=totalSize;this.partCount=partCount;}
    }
    @org.springframework.web.bind.annotation.PostMapping("gallery/upload-chunk")
    @ACL(type=AclType.UPDATE,point="$arg1")
    public ResponseEntity<ResultEntity<Map<String,Object>>> uploadGalleryChunk(@RequestParam String folderPath,
            @RequestParam org.springframework.web.multipart.MultipartFile file,@RequestParam String filename,
            @RequestParam long totalSize,@RequestParam int partIndex,@RequestParam int partCount,
            @RequestParam(required=false) String uploadId) throws IOException {
        Path directory=galleryDirectory(folderPath);
        if(directory==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"画廊目录不存在");
        AclAccess.checkPath(AclType.UPDATE,directory.toString());
        if(filename==null || filename.isEmpty() || filename.equals(".") || filename.equals("..") || filename.contains("/") || filename.contains("\\") || filename.contains(":") || filename.endsWith(".") || filename.endsWith(" ") || filename.chars().anyMatch(c->c<32))throw new IllegalArgumentException("文件名无效");
        Path target=directory.resolve(filename);AclAccess.checkPath(AclType.UPDATE,target.toString());
        if(totalSize<1 || totalSize>UPLOAD_LIMIT)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,"文件最大500MB");
        if(partCount!=(int)((totalSize+CHUNK_SIZE-1)/CHUNK_SIZE) || partIndex<0 || partIndex>=partCount)throw new IllegalArgumentException("上传分片参数无效");
        long expected=Math.min(CHUNK_SIZE,totalSize-(long)partIndex*CHUNK_SIZE);
        if(file.getSize()!=expected)throw new IllegalArgumentException("分片大小不正确");
        ChunkUpload state;
        if(uploadId==null || uploadId.isEmpty()) {
            if(partIndex!=0)throw new IllegalArgumentException("请从首个分片开始上传");
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"同名文件已存在，请更改文件名后上传");
            synchronized(chunkUploads) {
                for(Map.Entry<String,ChunkUpload> entry:chunkUploads.entrySet())if(entry.getValue().userId.equals(CurrentUser.require().userId))synchronized(entry.getValue()){discardChunk(entry.getKey(),entry.getValue());}
                Path staging=thumbnailCacheRoot.resolveSibling("gallery-uploads");Files.createDirectories(staging);
                state=new ChunkUpload(CurrentUser.require().userId,filename,directory,Files.createTempFile(staging,"chunk-",".part"),totalSize,partCount);
                uploadId=UUID.randomUUID().toString();chunkUploads.put(uploadId,state);
            }
        } else {
            state=chunkUploads.get(uploadId);
            if(state==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"上传已过期，请重新选择文件");
        }
        synchronized(state) {
            if(!state.userId.equals(CurrentUser.require().userId))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"不能操作他人的上传");
            if(!state.directory.equals(directory) || !state.name.equals(filename) || state.totalSize!=totalSize || state.partCount!=partCount || state.nextPart!=partIndex)throw new IllegalArgumentException("上传分片顺序或目标不一致");
            state.touched=System.currentTimeMillis();
            try {
                try(java.io.InputStream input=file.getInputStream();java.io.OutputStream output=Files.newOutputStream(state.temporary,StandardOpenOption.APPEND)) {
                    byte[] buffer=new byte[65536];long copied=0;int count;
                    while((count=input.read(buffer))!=-1){copied+=count;if(copied>expected)throw new IllegalArgumentException("分片大小不正确");output.write(buffer,0,count);}
                    if(copied!=expected)throw new IllegalArgumentException("分片数据不完整");
                }
                state.nextPart++;state.touched=System.currentTimeMillis();
                boolean complete=state.nextPart==state.partCount;
                if(complete){Files.move(state.temporary,target);chunkUploads.remove(uploadId,state);galleryCache.rebuildAuthorized(directory.toString());}
                Map<String,Object> result=new LinkedHashMap<>();result.put("uploadId",uploadId);result.put("completed",complete);result.put("name",filename);
                return ResponseEntity.ok(ResultEntity.successWithData(result));
            } catch(FileAlreadyExistsException e) {
                discardChunk(uploadId,state);throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"同名文件已存在，请更改文件名后上传");
            } catch(IOException | RuntimeException e){discardChunk(uploadId,state);throw e;}
        }
    }
    private void discardChunk(String id,ChunkUpload state) throws IOException {chunkUploads.remove(id,state);Files.deleteIfExists(state.temporary);}
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=300000)
    public void cleanAbandonedUploads() {
        long cutoff=System.currentTimeMillis()-UPLOAD_IDLE_MS;
        for(Map.Entry<String,ChunkUpload> entry:chunkUploads.entrySet())synchronized(entry.getValue()) {
            if(entry.getValue().touched<cutoff)try{discardChunk(entry.getKey(),entry.getValue());}catch(IOException e){LOGGER.warn("无法清理上传临时文件",e);}
        }
        Path staging=thumbnailCacheRoot.resolveSibling("gallery-uploads");
        if(Files.isDirectory(staging))try(DirectoryStream<Path> paths=Files.newDirectoryStream(staging,"chunk-*.part")) {
            for(Path temporary:paths){boolean active=chunkUploads.values().stream().anyMatch(u->u.temporary.equals(temporary));if(!active && Files.getLastModifiedTime(temporary).toMillis()<cutoff)Files.deleteIfExists(temporary);}
        }catch(IOException e){LOGGER.warn("无法清理上传暂存目录",e);}
    }

    @GetMapping("gallery")
    @ACL(type=AclType.READ,point="$arg1")
    public ResponseEntity<ResultEntity<ArtistGalleryData>> getGallery(
            @RequestParam String folderPath,
            @RequestParam(required = false, defaultValue = "album") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String order) {
        Path directory = galleryDirectory(folderPath);
        if (directory == null) {
            return ResponseEntity.notFound().build();
        }
        AclAccess.checkPath(AclType.READ,directory.toString());
        String normalizedFolderPath = directory.toString();
        try {
            List<ArtistGalleryCache.CachedImage> cachedImages = galleryCache.getOrBuildAuthorized(normalizedFolderPath);
            List<ArtistGalleryItem> items = new ArrayList<>(cachedImages.size());
            for (ArtistGalleryCache.CachedImage cachedImage : cachedImages) {
                // Use cached entries without checking their existence or resolving real paths.
                Path source=directory.resolve(cachedImage.getName()).normalize();
                String permissionPath=GlobPermissions.canonical(source.toString());
                if(directory.equals(source.getParent()) && AclAccess.can(AclType.READ,permissionPath)) {
                    ArtistGalleryItem item=toGalleryItem(cachedImage,directory);
                    if("folder".equals(cachedImage.getType()) && !canReadCachedTree(permissionPath)) item.setPosterUrl(null);
                    items.add(item);
                }
            }
            if ("random".equalsIgnoreCase(sortBy)) Collections.shuffle(items);
            else items.sort(galleryComparator(sortBy, order));
            return galleryResponse(normalizedFolderPath, items);
        } catch (IOException exception) {
            LOGGER.warn("Unable to read gallery folder {}", normalizedFolderPath, exception);
            return ResponseEntity.status(500).body(ResultEntity.failed("读取图库失败"));
        }
    }

    @GetMapping("gallery/file")
    @ACL(type=AclType.READ,point="$arg1")
    public ResponseEntity<Resource> getGalleryFile(@RequestParam String folderPath,
                                                   @RequestParam String path,
                                                   @RequestParam(required = false,
                                                           defaultValue = "false") boolean thumbnail) {
        if (path == null || path.trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path directory = galleryDirectory(folderPath);
        if (directory == null) {
            return ResponseEntity.notFound().build();
        }
        Path requested = resolveGalleryEntry(directory, path);
        if (requested == null) {
            return ResponseEntity.notFound().build();
        }
        AclAccess.checkPath(AclType.READ,requested.toString());
        Path servedFile;
        if (Files.isDirectory(requested)) {
            if(!canReadTree(requested)) return ResponseEntity.status(403).build();
            servedFile = galleryCache.posterFile(requested.toString());
            if (!Files.isRegularFile(servedFile)) {
                return ResponseEntity.notFound().build();
            }
        } else {
            servedFile = requested;
        }
        if (thumbnail && Files.isRegularFile(requested) && isImage(requested)) {
            try {
                servedFile = cachedThumbnail(requested, directory.toString());
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Unable to create gallery thumbnail for {}", requested, exception);
                servedFile = requested;
            }
        } else if (thumbnail && Files.isRegularFile(requested) && isVideo(requested)) {
            try {
                servedFile = cachedVideoThumbnail(requested, directory.toString());
            } catch (IOException exception) {
                LOGGER.warn("无法生成视频首帧缩略图: {}", requested, exception);
                return ResponseEntity.status(500).build();
            }
        }
        FileSystemResource resource = new FileSystemResource(servedFile);
        MediaType mediaType = mediaType(servedFile);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .header("Accept-Ranges", "bytes")
                .cacheControl(CacheControl.noStore().cachePrivate());
        if(!isImage(servedFile) && !isVideo(servedFile)) response.header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(servedFile.getFileName().toString(),StandardCharsets.UTF_8).build().toString());
        try {
            response.contentLength(resource.contentLength());
            response.lastModified(Files.getLastModifiedTime(requested).toMillis());
        } catch (IOException ignored) {
            // The resource can still be streamed when its size is unavailable.
        }
        return response.body(resource);
    }

    @GetMapping("/gallery/archive")
    public ResultEntity<Map<String,String>> prepareArchive(@RequestParam String folderPath) throws IOException {
        Path directory=authorizedArchiveDirectory(folderPath);
        archiveCache.getOrBuild(directory);
        Map<String,String> data=new LinkedHashMap<>();
        data.put("url","/api/fanbox/gallery/archive/download?folderPath="+URLEncoder.encode(directory.toString(),StandardCharsets.UTF_8.name()));
        data.put("filename",archiveDownloadName(directory));
        return ResultEntity.successWithData(data);
    }

    @GetMapping("/gallery/archive/download")
    public ResponseEntity<Resource> downloadArchive(@RequestParam String folderPath) throws IOException {
        Path directory=authorizedArchiveDirectory(folderPath);
        Path archive=archiveCache.archiveFile(directory);
        if(!Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .cacheControl(CacheControl.noStore()).contentLength(Files.size(archive))
                .header("Content-Disposition",org.springframework.http.ContentDisposition.attachment()
                        .filename(archiveDownloadName(directory),StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(archive));
    }

    private Path authorizedArchiveDirectory(String folderPath) {
        CurrentUser.require();
        Path directory=galleryDirectory(folderPath);
        if(directory==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Gallery folder not found");
        AclAccess.checkPath(AclType.READ,directory.toString());
        if(!canReadTree(directory)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"没有当前文件夹全部内容的阅读权限");
        return directory;
    }
    private String archiveDownloadName(Path directory) {
        return (directory.getFileName()==null ? "gallery" : directory.getFileName().toString())+".zip";
    }

    private boolean canReadCachedTree(String permissionPath) {
        CurrentUser u=CurrentUser.require();
        return u.superAdmin || GlobPermissions.subset(Collections.singletonList("read:"+permissionPath+"/*"),u.permissions);
    }

    private boolean canReadTree(Path dir) {
        CurrentUser u=CurrentUser.require();
        return u.superAdmin || GlobPermissions.subset(Collections.singletonList("read:"+AclAccess.path(dir.toString())+"/*"),u.permissions);
    }

    private ResponseEntity<ResultEntity<ArtistGalleryData>> galleryResponse(
            String folderPath, List<ArtistGalleryItem> images) {
        return ResponseEntity.ok(ResultEntity.successWithData(new ArtistGalleryData(folderPath, images)));
    }

    private Comparator<ArtistGalleryItem> galleryComparator(String sortBy, String order) {
        boolean ascending = "asc".equalsIgnoreCase(order);
        Comparator<ArtistGalleryItem> foldersFirst = Comparator.comparing(
                item -> !"folder".equals(item.getType()));
        Comparator<ArtistGalleryItem> itemComparator;
        if ("album".equalsIgnoreCase(sortBy)) {
            itemComparator = (left, right) -> compareAlbumItems(left, right, ascending);
            return foldersFirst.thenComparing(itemComparator);
        }
        itemComparator = "name".equalsIgnoreCase(sortBy)
                ? Comparator.comparing(ArtistGalleryItem::getName, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparingLong(ArtistGalleryItem::getCreatedAt);
        if (!ascending) {
            itemComparator = itemComparator.reversed();
        }
        return foldersFirst.thenComparing(itemComparator)
                .thenComparing(ArtistGalleryItem::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private int compareAlbumItems(ArtistGalleryItem left, ArtistGalleryItem right, boolean ascending) {
        LocalDate leftDate = leadingDate(left.getName());
        LocalDate rightDate = leadingDate(right.getName());
        if (leftDate != null && rightDate == null) {
            return -1;
        }
        if (leftDate == null && rightDate != null) {
            return 1;
        }
        if (leftDate != null) {
            int dateComparison = leftDate.compareTo(rightDate);
            if (dateComparison != 0) {
                return ascending ? dateComparison : -dateComparison;
            }
        }
        return String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName());
    }

    private LocalDate leadingDate(String fileName) {
        Matcher matcher = LEADING_DATE.matcher(fileName == null ? "" : fileName);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private ArtistGalleryItem toGalleryItem(ArtistGalleryCache.CachedImage image, Path directory)
            throws IOException {
        if ("folder".equals(image.getType())) {
            Path childDirectory = directory.resolve(image.getName()).normalize();
            ArtistGalleryItem folder = new ArtistGalleryItem(image.getName(), "folder",
                    childDirectory.toString(), image.getCreatedAt());
            folder.setPosterUrl("/api/fanbox/gallery/file?folderPath=" + encode(directory.toString())
                    + "&path=" + encode(image.getName()));
            return folder;
        }
        String type = "video".equals(image.getType()) ? "video" : "image";
        return new ArtistGalleryItem(image.getName(), type, null, image.getCreatedAt());
    }

    private Path galleryDirectory(String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return null;
        }
        try {
            Path directory = Paths.get(folderPath);
            if (!directory.isAbsolute()) {
                return null;
            }
            directory = directory.toAbsolutePath().normalize();
            if (!Files.isDirectory(directory)) {
                return null;
            }
            Path realRoot = Files.isDirectory(downloadRoot) ? downloadRoot.toRealPath() : downloadRoot;
            Path realDirectory = directory.toRealPath();
            boolean inDownloadRoot = realDirectory.startsWith(realRoot);
            boolean inRegisteredGallery = freeGalleryService != null
                    && freeGalleryService.isRegisteredDirectory(realDirectory);
            return inDownloadRoot || inRegisteredGallery ? realDirectory : null;
        } catch (InvalidPathException | IOException exception) {
            return null;
        }
    }

    private Path resolveGalleryEntry(Path galleryDirectory, String fileName) {
        try {
            Path relative = Paths.get(fileName);
            if (relative.isAbsolute() || relative.getNameCount() != 1) {
                return null;
            }
            Path requested = galleryDirectory.resolve(relative).normalize();
            if (!galleryDirectory.equals(requested.getParent())
                    || (!Files.isRegularFile(requested) && !Files.isDirectory(requested))) {
                return null;
            }
            Path realRequested = requested.toRealPath();
            return galleryDirectory.equals(realRequested.getParent())
                    && (Files.isDirectory(realRequested) || isSupportedMedia(realRequested))
                    ? realRequested : null;
        } catch (InvalidPathException | IOException exception) {
            return null;
        }
    }

    private Path cachedThumbnail(Path source, String folderPath) throws IOException {
        Path target = thumbnailFile(source, folderPath);
        if (!target.startsWith(thumbnailCacheRoot)) {
            throw new IOException("缩略图缓存路径无效");
        }
        synchronized (thumbnailLock(target)) {
            if (isFreshThumbnail(source, target)) {
                return target;
            }
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "thumbnail-", ".jpg");
            try {
                Thumbnails.of(source.toFile())
                        .size(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT)
                        .outputFormat("jpg")
                        .outputQuality(0.82d)
                        .toFile(temporary.toFile());
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return target;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private Path cachedVideoThumbnail(Path source, String folderPath) throws IOException {
        Path target = thumbnailFile(source, folderPath);
        synchronized (thumbnailLock(target)) {
            if (isFreshThumbnail(source, target)) {
                return target;
            }
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "video-thumbnail-", ".jpg");
            try {
                extractVideoFrame(source, temporary);
                if (Files.size(temporary) == 0) {
                    throw new IOException("视频首帧为空");
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return target;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private Path thumbnailFile(Path source, String folderPath) throws IOException {
        String directoryCacheKey = stableCacheKey(folderPath);
        String imageCacheKey = stableCacheKey(source.getFileName().toString()) + ".jpg";
        Path target = thumbnailCacheRoot.resolve(directoryCacheKey).resolve(imageCacheKey).normalize();
        if (!target.startsWith(thumbnailCacheRoot)) {
            throw new IOException("缩略图缓存路径无效");
        }
        return target;
    }

    void extractVideoFrame(Path source, Path target) throws IOException {
        boolean slotAcquired;
        try {
            slotAcquired = VIDEO_THUMBNAIL_SLOTS.tryAcquire(60, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("等待视频首帧提取时被中断");
        }
        if (!slotAcquired) {
            throw new IOException("等待视频首帧提取超时");
        }
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source.toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();
            Frame frame = grabber.grabImage();
            if (frame == null) {
                throw new IOException("视频没有可提取的画面");
            }
            BufferedImage image = converter.convert(frame);
            if (image == null) {
                throw new IOException("无法转换视频首帧");
            }
            Thumbnails.of(image)
                    .size(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT)
                    .outputFormat("jpg")
                    .outputQuality(0.82d)
                    .toFile(target.toFile());
        } catch (FrameGrabber.Exception | RuntimeException exception) {
            throw new IOException("无法提取视频首帧: " + source, exception);
        } finally {
            VIDEO_THUMBNAIL_SLOTS.release();
        }
    }

    private boolean isFreshThumbnail(Path source, Path thumbnail) throws IOException {
        return Files.isRegularFile(thumbnail)
                && Files.size(thumbnail) > 0
                && Files.getLastModifiedTime(thumbnail).toMillis()
                >= Files.getLastModifiedTime(source).toMillis();
    }

    private Object thumbnailLock(Path target) {
        return THUMBNAIL_LOCKS[(target.toString().hashCode() & Integer.MAX_VALUE) % THUMBNAIL_LOCKS.length];
    }

    private static Object[] thumbnailLocks() {
        Object[] locks = new Object[64];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private String stableCacheKey(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    private boolean isImage(Path path) {
        return IMAGE_EXTENSIONS.contains(extension(path));
    }

    private boolean isVideo(Path path) {
        return VIDEO_EXTENSIONS.contains(extension(path));
    }

    private boolean isSupportedMedia(Path path) {
        String extension = extension(path);
        return IMAGE_EXTENSIONS.contains(extension) || VIDEO_EXTENSIONS.contains(extension);
    }

    private String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private MediaType mediaType(Path path) {
        switch (extension(path)) {
            case "mp4":
                return MediaType.parseMediaType("video/mp4");
            case "webm":
                return MediaType.parseMediaType("video/webm");
            case "ogv":
            case "ogg":
                return MediaType.parseMediaType("video/ogg");
            case "mov":
                return MediaType.parseMediaType("video/quicktime");
            case "m4v":
                return MediaType.parseMediaType("video/x-m4v");
            case "mkv":
                return MediaType.parseMediaType("video/x-matroska");
            case "avi":
                return MediaType.parseMediaType("video/x-msvideo");
            default:
                return MediaTypeFactory.getMediaType(path.getFileName().toString())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }
    }
}
