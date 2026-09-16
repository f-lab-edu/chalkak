package com.chalkak.auction.controller;

import com.chalkak.auction.service.CameraImageDownload;
import com.chalkak.auction.service.CameraImageService;
import com.chalkak.common.util.ImageUrls;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ImageUrls.BASE_PATH)
@RequiredArgsConstructor
public class CameraImageController {

    private final CameraImageService cameraImageService;

    @GetMapping("/{imageId}")
    public ResponseEntity<Resource> download(@PathVariable Long imageId) {
        CameraImageDownload download = cameraImageService.download(imageId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .contentType(download.contentType())
            .body(download.resource());
    }
}
