package com.chalkak.auction.controller.response;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import com.chalkak.user.entity.User;
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
  public static AuctionSummaryResponse from(Auction auction, String thumbnailImage) {
    Camera camera = auction.getCamera();

    return new AuctionSummaryResponse(
        auction.getId(),
        CameraInfo.from(camera, thumbnailImage),
        SellerInfo.from(camera.getOwner()),
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
    private static SellerInfo from(User owner) {
      return new SellerInfo(owner.getId(), owner.getNickname());
    }
  }
}

