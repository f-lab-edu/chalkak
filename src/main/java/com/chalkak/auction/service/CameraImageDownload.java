package com.chalkak.auction.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record CameraImageDownload(
    Resource resource,
    MediaType contentType
) {
}
