package com.chalkak.auction.controller.response;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionSummaryResponse(
    Long id,
    CameraInfo camera,
    SellerInfo seller,
    BigDecimal currentPrice,
    AuctionStatus status,
    LocalDateTime closesAt
) {
  // TODO: User에 실제 nickname 필드가 추가되면 임시 닉네임 생성 로직을 제거한다.
  private static final String TEMP_NICKNAME_PREFIX = "판매자";

  public static AuctionSummaryResponse from(Auction auction, String thumbnailImage) {
    Camera camera = auction.getCamera();
    Long sellerId = camera.getOwner().getId();

    return new AuctionSummaryResponse(
        auction.getId(),
        CameraInfo.from(camera, thumbnailImage),
        SellerInfo.from(sellerId, TEMP_NICKNAME_PREFIX + sellerId),
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
      String thumbnailImage
  ) {
    private static CameraInfo from(Camera camera, String thumbnailImage) {
      return new CameraInfo(
          camera.getId(),
          camera.getCategory(),
          camera.getBrand(),
          camera.getModelName(),
          camera.getConditionGrade(),
          thumbnailImage
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

