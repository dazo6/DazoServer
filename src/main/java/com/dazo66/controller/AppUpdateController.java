package com.dazo66.controller;

import com.dazo66.security.CurrentUser;
import com.dazo66.service.AppReleaseStore;
import com.dazo66.util.ResultEntity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

@RestController
@RequestMapping("/api/app-updates")
public class AppUpdateController {
    private final AppReleaseStore store;
    public AppUpdateController(AppReleaseStore store) { this.store = store; }

    @GetMapping("/latest")
    public ResultEntity<ObjectNode> latest() throws IOException {
        CurrentUser.require();
        return ResultEntity.successWithData(store.latest());
    }

    @GetMapping("/{version}/apk")
    public ResponseEntity<Resource> download(@PathVariable String version) throws IOException {
        CurrentUser.require();
        ObjectNode info = store.metadata(version);
        // Spring's Resource converter provides authenticated HEAD and Range/206 downloads.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(info.path("size").asLong())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"DazoGallery-" + version + ".apk\"")
                .header("X-App-Version", version)
                .header("X-App-Version-Code", info.path("versionCode").asText())
                .header("X-App-Package", info.path("packageName").asText())
                .header("X-App-Min-Sdk", info.path("minSdk").asText())
                .header("X-App-Sha256", info.path("sha256").asText())
                .header("X-App-Certificate-Sha256", info.path("certificateSha256").asText())
                .eTag("\"" + info.path("sha256").asText() + "\"")
                .body(new FileSystemResource(store.apk(version)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultEntity<ObjectNode> upload(@RequestParam MultipartFile file, @RequestParam String manifest,
                                            @RequestParam String description) throws IOException {
        if (!CurrentUser.require().superAdmin) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以发布应用更新");
        return ResultEntity.successWithData(store.upload(file, manifest, description));
    }
}
