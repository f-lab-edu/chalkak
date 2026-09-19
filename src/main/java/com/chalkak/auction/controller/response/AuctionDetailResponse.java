package com.chalkak.auction.controller.response;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import com.chalkak.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AuctionDetailResponse(
    Long id,
    CameraInfo camera,
    SellerInfo seller,
    BigDecimal startPrice,
    BigDecimal currentPrice,
    AuctionStatus status,
    LocalDateTime closesAt
) {
    public static AuctionDetailResponse from(Auction auction, List<String> imageUrls) {
        Camera camera = auction.getCamera();

        return new AuctionDetailResponse(
            auction.getId(),
            CameraInfo.from(camera, imageUrls),
            SellerInfo.from(camera.getOwner()),
            auction.getStartPrice(),
            auction.getCurrentPrice(),
            auction.getStatus(),
            auction.getExtendedClosesAt()
        );
    }

    public record CameraInfo(
        Long id,
        CameraCategory category,
        String brand,
        String modelName,
        CameraConditionGrade conditionGrade,
        String description,
        List<String> imageUrls
    ) {
        private static CameraInfo from(Camera camera, List<String> imageUrls) {
            return new CameraInfo(
                camera.getId(),
                camera.getCategory(),
                camera.getBrand(),
                camera.getModelName(),
                camera.getConditionGrade(),
                camera.getDescription(),
                imageUrls
            );
        }
    }

    public record SellerInfo(
        Long id,
        String nickname
    ) {
        private static SellerInfo from(User owner) {
            return new SellerInfo(owner.getId(), owner.getNickname());
        }
    }
}
