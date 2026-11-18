package com.dazo66.android;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** All configured origins belong to one account/server scope; external redirects never gain credentials. */
final class ServerRoutes {
    interface Attempt<T> { T run(String address, boolean trusted) throws Exception; }
    static final class Failure extends IOException {
        final boolean retrySafe;
        Failure(String message, Throwable cause, boolean retrySafe) { super(message, cause); this.retrySafe = retrySafe; }
    }
    private final List<String> origins;
    private volatile String active;
    ServerRoutes(String primary, List<String> backups) {
        LinkedHashSet<String> values = new LinkedHashSet<>(); values.add(normalize(primary));
        for (String backup : backups) if (!backup.trim().isEmpty()) values.add(normalize(backup));
        if (values.size() > 6) throw new IllegalArgumentException("最多添加 5 个备用地址");
        origins = new ArrayList<>(values); active = origins.get(0);
    }
    static String normalize(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())) || uri.getPort() > 65535 || uri.getPort() == 0)
                throw new IllegalArgumentException();
            return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) { throw new IllegalArgumentException("请输入服务器根地址，例如 https://example.com"); }
    }
    String primary() { return origins.get(0); }
    String active() { return active; }
    List<String> backups() { return new ArrayList<>(origins.subList(1, origins.size())); }
    boolean trusted(String address) { return relative(address) != null; }
    private String relative(String address) {
        if (address.startsWith("/")) return address;
        for (String origin : origins) if (address.equals(origin) || address.startsWith(origin + "/") || address.startsWith(origin + "?"))
            return address.substring(origin.length());
        return null;
    }
    String canonical(String address) { String path = relative(address); return path == null ? address : primary() + path; }
    List<String> addresses(String address) {
        String path = relative(address);
        List<String> result = new ArrayList<>();
        if (path == null) { result.add(address); return result; }
        result.add(active + path);
        for (String origin : origins) if (!origin.equals(active)) result.add(origin + path);
        return result;
    }
    <T> T execute(String address, Attempt<T> attempt) throws Exception {
        boolean trusted = trusted(address);
        Failure last = null;
        for (String target : addresses(address)) {
            try {
                T value = attempt.run(target, trusted);
                if (trusted) active = URI.create(target).getScheme() + "://" + URI.create(target).getRawAuthority();
                return value;
            } catch (Failure failure) {
                last = failure;
                if (!trusted || !failure.retrySafe) throw failure;
            }
        }
        throw new Failure("所有服务器地址均暂时无法连接，请检查网络后重试", last, true);
    }
}
