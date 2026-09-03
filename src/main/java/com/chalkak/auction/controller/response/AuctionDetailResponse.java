package com.chalkak.auction.controller.response;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
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
    // TODO: User에 실제 nickname 필드가 추가되면 임시 닉네임 생성 로직을 제거한다.
    private static final String TEMP_NICKNAME_PREFIX = "판매자";

    public static AuctionDetailResponse from(Auction auction, List<String> imageKeys) {
        Camera camera = auction.getCamera();
        Long sellerId = camera.getOwner().getId();

        return new AuctionDetailResponse(
            auction.getId(),
            CameraInfo.from(camera, imageKeys),
            SellerInfo.from(sellerId, TEMP_NICKNAME_PREFIX + sellerId),
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
        // TODO: FileStorage에 다운로드 URL 조회 기능이 추가되면 key 대신 다운로드 가능한 경로를 내려준다.
        List<String> imageKeys
    ) {
        private static CameraInfo from(Camera camera, List<String> imageKeys) {
            return new CameraInfo(
                camera.getId(),
                camera.getCategory(),
                camera.getBrand(),
                camera.getModelName(),
                camera.getConditionGrade(),
                camera.getDescription(),
                imageKeys
            );
        }
    }

    public record SellerInfo(
        Long id,
        String nickname
    ) {
        private static SellerInfo from(Long id, String nickname) {
            return new SellerInfo(id, nickname);
        }
    }
}
