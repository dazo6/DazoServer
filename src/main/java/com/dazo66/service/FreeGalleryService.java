package com.dazo66.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dazo66.dashboard.FreeGalleryRequest;
import com.dazo66.entity.FreeGallery;
import com.dazo66.mapper.FreeGalleryMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class FreeGalleryService {

    private static final long REGISTERED_ROOTS_CACHE_MILLIS = 30_000L;
    private final FreeGalleryMapper mapper;
    private volatile List<Path> registeredRoots = Collections.emptyList();
    private volatile long registeredRootsExpiresAt;

    public FreeGalleryService(FreeGalleryMapper mapper) {
        this.mapper = mapper;
    }

    public List<FreeGallery> list(String keyword) {
        QueryWrapper<FreeGallery> query = new QueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            query.and(wrapper -> wrapper.like("name", keyword.trim())
                    .or().like("folder_path", keyword.trim()));
        }
        query.orderByDesc("gmt_modified").orderByDesc("id");
        return mapper.selectList(query);
    }

    public FreeGallery create(FreeGalleryRequest request) {
        Path folder = requireDirectory(request == null ? null : request.getFolderPath());
        ensurePathAvailable(folder.toString(), null);
        Date now = new Date();
        FreeGallery gallery = new FreeGallery()
                .setFolderPath(folder.toString())
                .setName(resolveName(request == null ? null : request.getName(), folder))
                .setGmtCreate(now)
                .setGmtModified(now);
        mapper.insert(gallery);
        invalidateRegisteredRoots();
        return gallery;
    }

    public FreeGallery update(int id, FreeGalleryRequest request) {
        FreeGallery existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("自由画廊不存在");
        }
        Path folder = requireDirectory(request == null ? null : request.getFolderPath());
        ensurePathAvailable(folder.toString(), id);
        existing.setFolderPath(folder.toString())
                .setName(resolveName(request == null ? null : request.getName(), folder))
                .setGmtModified(new Date());
        mapper.updateById(existing);
        invalidateRegisteredRoots();
        return existing;
    }

    public boolean delete(int id) {
        boolean deleted = mapper.deleteById(id) > 0;
        if (deleted) {
            invalidateRegisteredRoots();
        }
        return deleted;
    }

    public boolean isRegisteredDirectory(Path directory) {
        if (directory == null) {
            return false;
        }
        for (Path registered : registeredRoots()) {
            if (directory.startsWith(registered)) {
                return true;
            }
        }
        return false;
    }

    private List<Path> registeredRoots() {
        long now = System.currentTimeMillis();
        if (now < registeredRootsExpiresAt) {
            return registeredRoots;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now < registeredRootsExpiresAt) {
                return registeredRoots;
            }
            List<Path> roots = new ArrayList<>();
            for (FreeGallery gallery : mapper.selectList(null)) {
                try {
                    Path registered = Paths.get(gallery.getFolderPath()).toAbsolutePath().normalize();
                    if (Files.isDirectory(registered)) {
                        roots.add(registered.toRealPath());
                    }
                } catch (InvalidPathException | IOException ignored) {
                    // An unavailable registration should not grant filesystem access.
                }
            }
            registeredRoots = Collections.unmodifiableList(roots);
            registeredRootsExpiresAt = now + REGISTERED_ROOTS_CACHE_MILLIS;
            return registeredRoots;
        }
    }

    private void invalidateRegisteredRoots() {
        registeredRootsExpiresAt = 0L;
    }

    private Path requireDirectory(String folderPath) {
        if (StringUtils.isBlank(folderPath)) {
            throw new IllegalArgumentException("请输入文件夹路径");
        }
        if (folderPath.trim().length() > 2048) {
            throw new IllegalArgumentException("文件夹路径不能超过 2048 个字符");
        }
        try {
            Path folder = Paths.get(folderPath.trim());
            if (!folder.isAbsolute()) {
                throw new IllegalArgumentException("请输入文件夹绝对路径");
            }
            if (!Files.isDirectory(folder)) {
                throw new IllegalArgumentException("文件夹不存在或无法访问");
            }
            return folder.toRealPath();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("文件夹路径格式无效");
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件夹不存在或无法访问");
        }
    }

    private String resolveName(String name, Path folder) {
        String trimmed = StringUtils.trimToNull(name);
        if (trimmed != null) {
            if (trimmed.length() > 256) {
                throw new IllegalArgumentException("文件夹名称不能超过 256 个字符");
            }
            return trimmed;
        }
        Path fileName = folder.getFileName();
        return fileName == null ? folder.toString() : fileName.toString();
    }

    private void ensurePathAvailable(String folderPath, Integer currentId) {
        QueryWrapper<FreeGallery> query = new QueryWrapper<FreeGallery>().eq("folder_path", folderPath);
        if (currentId != null) {
            query.ne("id", currentId);
        }
        if (mapper.selectCount(query) > 0) {
            throw new IllegalArgumentException("该文件夹已经注册");
        }
    }
}
