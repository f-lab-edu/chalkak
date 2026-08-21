package com.chalkak.auction.service;

import com.chalkak.auction.controller.request.AuctionRequest;
import com.chalkak.auction.controller.response.AuctionResponse;
import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraImage;
import com.chalkak.auction.exception.AuctionErrorCode;
import com.chalkak.auction.repository.AuctionRepository;
import com.chalkak.auction.repository.CameraImageRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.file.service.FileStorage;
import com.chalkak.user.entity.User;
import com.chalkak.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private static final int MIN_IMAGE_COUNT = 3;

    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final CameraImageRepository cameraImageRepository;
    private final AuctionRepository auctionRepository;
    private final FileStorage fileStorage;

    @Transactional
    public AuctionResponse register(Long ownerId, AuctionRequest request, List<MultipartFile> images) {
        validateImageCount(images);
        User owner = getOwner(ownerId);

        Camera camera = cameraRepository.save(Camera.register(
            owner, request.category(), request.brand(), request.modelName(),
            request.conditionGrade(), request.description()));

        List<CameraImage> cameraImages = images.stream()
            .map(image -> CameraImage.attach(camera, fileStorage.upload(image)))
            .toList();
        cameraImageRepository.saveAll(cameraImages);

        Auction auction = auctionRepository.save(Auction.start(camera, request.startPrice(), request.closesAt()));

        return AuctionResponse.from(auction);
    }

    private User getOwner(Long ownerId) {
        return userRepository.findById(ownerId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, CommonErrorCode.NOT_FOUND.formatted("판매자")));
    }

    private void validateImageCount(List<MultipartFile> images) {
        if (images == null || images.size() < MIN_IMAGE_COUNT) {
            throw new BusinessException(AuctionErrorCode.INVALID_IMAGE_COUNT);
        }
    }
}
