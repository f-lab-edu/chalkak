package com.chalkak.auction.service;

import com.chalkak.auction.entity.CameraImage;
import com.chalkak.auction.repository.CameraImageRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.common.util.FileUtils;
import com.chalkak.file.service.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CameraImageService {

    private final CameraImageRepository cameraImageRepository;
    private final FileStorage fileStorage;

    public CameraImageDownload download(Long imageId) {
        CameraImage cameraImage = getCameraImage(imageId);
        Resource resource = fileStorage.download(cameraImage.getImageKey());
        MediaType contentType = resolveContentType(cameraImage.getImageKey());

        return new CameraImageDownload(resource, contentType);
    }

    private CameraImage getCameraImage(Long imageId) {
        return cameraImageRepository.findById(imageId)
            .orElseThrow(() -> new BusinessException(
                CommonErrorCode.NOT_FOUND, CommonErrorCode.NOT_FOUND.formatted("이미지")));
    }

    private MediaType resolveContentType(String imageKey) {
        String extension = FileUtils.extractExtension(imageKey);
        return switch (extension) {
            case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG;
            case ".png" -> MediaType.IMAGE_PNG;
            case ".gif" -> MediaType.IMAGE_GIF;
            case ".webp" -> MediaType.valueOf("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
