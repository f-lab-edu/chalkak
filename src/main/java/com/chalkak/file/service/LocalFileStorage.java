package com.chalkak.file.service;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.util.FileUtils;
import com.chalkak.file.exception.FileErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path uploadDir;

    public LocalFileStorage(@Value("${file.upload-dir:build/uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
    }

    @Override
    public String upload(MultipartFile file) {
        try {
            Files.createDirectories(uploadDir);
            String key = UUID.randomUUID() + FileUtils.extractExtension(file.getOriginalFilename());
            file.transferTo(uploadDir.resolve(key));
            return key;
        } catch (IOException e) {
            throw new BusinessException(FileErrorCode.UPLOAD_FAILED);
        }
    }

    @Override
    public Resource download(String key) {
        Resource resource = new FileSystemResource(uploadDir.resolve(key));
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
        }
        return resource;
    }
}
