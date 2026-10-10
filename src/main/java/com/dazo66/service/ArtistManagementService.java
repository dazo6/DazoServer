package com.dazo66.service;

import com.dazo66.security.*;
import com.dazo66.config.KemonoProperties;
import com.dazo66.dashboard.ArtistCreateRequest;
import com.dazo66.dashboard.ArtistManagementItem;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps artist URL parsing and presentation rules on the server.
 */
@Service
public class ArtistManagementService {

    private static final Pattern KEMONO_ARTIST_PATH =
            Pattern.compile("^/(?:api/v1/)?([^/]+)/user/([^/?#]+)(?:/.*)?$");
    private static final Pattern NUMERIC_ID = Pattern.compile("^\\d+$");

    @Autowired
    private KemonoProperties kemonoProperties;
    @Value("${fanbox.save.path}")
    private String downloadPath;

    public Artist parse(ArtistCreateRequest request) {
        if (request == null || StringUtils.isBlank(request.getUrl())) {
            throw new IllegalArgumentException("请输入画师主页 URL");
        }

        URI uri;
        try {
            uri = URI.create(request.getUrl().trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("画师 URL 格式不正确");
        }
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("请输入完整的 HTTP(S) 画师主页 URL");
        }

        Artist artist;
        if (kemonoProperties.isConfiguredOrigin(uri.toString())) {
            artist = parseKemono(uri);
        } else {
            artist = parseFanbox(uri);
        }

        String requestedName = StringUtils.trimToNull(request.getName());
        if (requestedName != null) {
            artist.setName(requestedName);
        }
        artist.setCompression(request.getCompression() == null || request.getCompression());
        artist.setEnable(true);
        return artist;
    }

    public ArtistManagementItem toItem(Artist artist) {
        ArtistDomainEnum domain = effectiveDomain(artist);
        String name = StringUtils.defaultIfBlank(artist.getName(), artist.getArtistId());
        String type = StringUtils.defaultString(artist.getType());
        if (domain == ArtistDomainEnum.KEMONO && StringUtils.isNotBlank(type)
                && !type.toLowerCase(Locale.ROOT).startsWith("kemono-")) {
            type = "kemono-" + type;
        }

        ArtistManagementItem item = new ArtistManagementItem();
        item.setId(artist.getId());
        item.setName(name);
        item.setIcon(ArtistImageCache.publicUrl(artist.getIcon()));
        item.setBanner(ArtistImageCache.publicUrl(artist.getBanner()));
        item.setFolderPath(resolveFolderPath(artist));
        if(CurrentUser.get()!=null) {
            java.util.Map<String,Boolean> caps=new java.util.LinkedHashMap<>();
            caps.put("canOpenGallery",AclAccess.canPath(AclType.READ,item.getFolderPath()));
            caps.put("canStatus",AclAccess.can(AclType.UPDATE,"op:artist.status") && AclAccess.canPath(AclType.UPDATE,item.getFolderPath()));
            caps.put("canCompression",AclAccess.can(AclType.UPDATE,"op:artist.compression") && AclAccess.canPath(AclType.UPDATE,item.getFolderPath()));
            item.setCapabilities(caps);
        }
        item.setUrl(buildArtistUrl(artist, domain));
        item.setType(type);
        item.setEnable(Boolean.TRUE.equals(artist.getEnable()));
        item.setCompression(artist.getCompression() == null || artist.getCompression());
        item.setGmtCreate(artist.getGmtCreate());
        item.setLastUpdate(artist.getLastUpdate());
        return item;
    }

    /**
     * Calculates the absolute gallery folder from the current download root
     * and artist name. The value is presentation data and is not persisted.
     */
    public String resolveFolderPath(Artist artist) {
        if (artist == null) {
            return null;
        }
        try {
            Path root = StringUtils.isBlank(downloadPath)
                    ? Paths.get("").toAbsolutePath().normalize()
                    : Paths.get(downloadPath).toAbsolutePath().normalize();
            String directoryName = StringUtil.cleanSym(artist.getName());
            if (directoryName.isEmpty()) {
                directoryName = StringUtil.cleanSym(artist.getArtistId());
            }
            return root.resolve(directoryName).normalize().toString();
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    public ArtistDomainEnum effectiveDomain(Artist artist) {
        if (artist.getDomain() != null) {
            return artist.getDomain();
        }
        // Rows created before the domain column existed used numeric Kemono IDs.
        return NUMERIC_ID.matcher(StringUtils.defaultString(artist.getArtistId())).matches()
                ? ArtistDomainEnum.KEMONO : ArtistDomainEnum.FANBOX;
    }

    private Artist parseKemono(URI uri) {
        Matcher matcher = KEMONO_ARTIST_PATH.matcher(uri.getRawPath());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Kemono URL 应为 /服务/user/画师ID 格式");
        }
        String type = decode(matcher.group(1));
        String artistId = decode(matcher.group(2));
        return new Artist()
                .setName(artistId)
                .setArtistId(artistId)
                .setType(type)
                .setDomain(ArtistDomainEnum.KEMONO);
    }

    private Artist parseFanbox(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String creatorId = null;
        if (host.endsWith(".fanbox.cc") && !"www.fanbox.cc".equals(host)
                && !"api.fanbox.cc".equals(host)) {
            creatorId = host.substring(0, host.length() - ".fanbox.cc".length());
        } else if ("www.fanbox.cc".equals(host)) {
            String path = StringUtils.removeStart(uri.getPath(), "/");
            if (path.startsWith("@")) {
                creatorId = path.substring(1).split("/", 2)[0];
            }
        }
        if (StringUtils.isBlank(creatorId)) {
            throw new IllegalArgumentException("仅支持 Fanbox 画师主页或当前 Kemono 站点的画师页");
        }
        creatorId = decode(creatorId);
        return new Artist()
                .setName(creatorId)
                .setArtistId(creatorId)
                .setType("fanbox")
                .setDomain(ArtistDomainEnum.FANBOX);
    }

    private String buildArtistUrl(Artist artist, ArtistDomainEnum domain) {
        if (domain == ArtistDomainEnum.KEMONO) {
            return kemonoProperties.absoluteUrl(
                    kemonoProperties.artistPath(artist.getType(), artist.getArtistId()));
        }
        return "https://www.fanbox.cc/@" + artist.getArtistId();
    }

    private String decode(String value) {
        return UriUtils.decode(value, StandardCharsets.UTF_8);
    }
}
