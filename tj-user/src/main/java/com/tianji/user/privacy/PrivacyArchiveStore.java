package com.tianji.user.privacy;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.model.*;
import com.qcloud.cos.exception.CosServiceException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Only authenticated application downloads are exposed; no public COS URLs. */
@Component
public final class PrivacyArchiveStore implements AutoCloseable {
    private final Path directory;
    private final COSClient cos;
    private final String bucket;
    private final long maximumBytes;

    public PrivacyArchiveStore(Environment env) {
        boolean production =
                env.getProperty("tj.production.enabled", Boolean.class, false)
                        || Arrays.asList(env.getActiveProfiles()).contains("production");
        String provider =
                env.getProperty("tj.privacy.archive.provider", production ? "COS" : "LOCAL");
        if (!Set.of("LOCAL", "COS").contains(provider) || (production && !"COS".equals(provider)))
            throw new IllegalArgumentException("生产隐私导出必须使用私有 COS 存储");
        maximumBytes = env.getProperty("tj.privacy.archive.max-bytes", Long.class, 104857600L);
        if (maximumBytes < 1024 || maximumBytes > 1073741824L)
            throw new IllegalArgumentException("隐私导出包上限无效");
        directory =
                Path.of(
                                env.getProperty(
                                        "tj.privacy.archive.directory",
                                        System.getProperty("java.io.tmpdir") + "/tianji-privacy"))
                        .toAbsolutePath()
                        .normalize();
        bucket =
                "COS".equals(provider)
                        ? env.getRequiredProperty("tj.privacy.archive.bucket")
                        : null;
        if (bucket != null) {
            if (!bucket.matches("[a-z0-9][a-z0-9-]{1,62}-[0-9]{5,20}"))
                throw new IllegalArgumentException("隐私存储桶无效");
            var config =
                    new ClientConfig(
                            new Region(env.getRequiredProperty("tj.privacy.archive.region")));
            config.setHttpProtocol(com.qcloud.cos.http.HttpProtocol.https);
            config.setConnectionTimeout(5000);
            config.setSocketTimeout(10000);
            cos =
                    new COSClient(
                            new BasicCOSCredentials(
                                    env.getRequiredProperty("tj.privacy.archive.secret-id"),
                                    env.getRequiredProperty("tj.privacy.archive.secret-key")),
                            config);
        } else cos = null;
    }

    public long maximumBytes() {
        return maximumBytes;
    }

    public Path workingFile(String id) throws IOException {
        validate(id);
        Files.createDirectories(directory);
        protect(directory, true);
        Path path = Files.createTempFile(directory, id + "-", ".zip");
        protect(path, false);
        return path;
    }

    public void save(String id, String sha256, Path path) throws IOException {
        validate(id);
        validateHash(sha256);
        if (Files.size(path) > maximumBytes)
            throw new IllegalArgumentException("EXPORT_SIZE_LIMIT");
        if (cos == null) {
            Files.move(
                    path,
                    directory.resolve(id + "-" + sha256 + ".zip"),
                    StandardCopyOption.REPLACE_EXISTING);
            protect(directory.resolve(id + "-" + sha256 + ".zip"), false);
        } else {
            verifyPrivate();
            var request =
                    new PutObjectRequest(bucket, key(id, sha256), path.toFile())
                            .withCannedAcl(CannedAccessControlList.Private);
            var metadata = new ObjectMetadata();
            metadata.setContentType("application/zip");
            metadata.setContentLength(Files.size(path));
            request.setMetadata(metadata);
            request.putCustomRequestHeader("x-cos-forbid-overwrite", "true");
            cos.putObject(request);
        }
    }

    public InputStream open(String id, String sha256) throws IOException {
        validate(id);
        validateHash(sha256);
        if (cos == null) return Files.newInputStream(directory.resolve(id + "-" + sha256 + ".zip"));
        verifyPrivate();
        return cos.getObject(bucket, key(id, sha256)).getObjectContent();
    }

    private String key(String id, String sha256) {
        return "privacy-exports/" + id + "/" + sha256 + ".zip";
    }

    private void validateHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("隐私交付校验值无效");
    }

    private void validate(String id) {
        if (id == null || !id.matches("[0-9a-f-]{36}"))
            throw new IllegalArgumentException("隐私交付标识无效");
    }

    private void verifyPrivate() {
        if (cos.getBucketAcl(bucket).getGrantsAsList().stream()
                .anyMatch(g -> !(g.getGrantee() instanceof UinGrantee)))
            throw new IllegalStateException("隐私存储桶不允许公开或全局组授权");
        try {
            String policy = cos.getBucketPolicy(bucket).getPolicyText();
            if (policy != null && !policy.isBlank()) {
                var root = JsonMapper.builder().build().readValue(policy, Map.class);
                Object statements =
                        root.containsKey("Statement")
                                ? root.get("Statement")
                                : root.get("statement");
                if (statements instanceof Map row) statements = List.of(row);
                if (!(statements instanceof Collection<?> rows))
                    throw new IllegalStateException("无法核对隐私桶策略");
                for (Object row : rows) {
                    if (!(row instanceof Map<?, ?> s)) throw new IllegalStateException("无法核对隐私桶策略");
                    String effect =
                            Objects.toString(
                                    s.containsKey("Effect") ? s.get("Effect") : s.get("effect"),
                                    "");
                    Object principal =
                            s.containsKey("Principal") ? s.get("Principal") : s.get("principal");
                    if ("allow".equalsIgnoreCase(effect)
                            && (principal == null
                                    || wildcard(principal)
                                    || s.keySet().stream()
                                            .anyMatch(
                                                    k ->
                                                            "notprincipal"
                                                                    .equalsIgnoreCase(
                                                                            k.toString()))))
                        throw new IllegalStateException("隐私桶策略不允许匿名授权");
                }
            }
        } catch (CosServiceException e) {
            if (e.getStatusCode() != 404) throw e;
        }
    }

    private boolean wildcard(Object value) {
        if (value instanceof Map<?, ?> m) return m.values().stream().anyMatch(this::wildcard);
        if (value instanceof Collection<?> c) return c.stream().anyMatch(this::wildcard);
        String s = value.toString().toLowerCase(Locale.ROOT);
        return s.contains("*")
                || s.contains("anyone")
                || s.contains("/groups/")
                || s.contains("allusers")
                || s.contains("authenticatedusers");
    }

    static void protect(Path path, boolean directory) throws IOException {
        if (Files.isSymbolicLink(path))
            throw new IOException("Private path must not be a symbolic link");
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null)
            Files.setPosixFilePermissions(
                    path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl == null)
                throw new IOException("Filesystem does not support private export permissions");
            acl.setAcl(
                    List.of(
                            AclEntry.newBuilder()
                                    .setType(AclEntryType.ALLOW)
                                    .setPrincipal(Files.getOwner(path))
                                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                                    .build()));
        }
    }

    @Override
    public void close() {
        if (cos != null) cos.shutdown();
    }
}
