package com.chalkak.outbox.entity;

import com.chalkak.common.entity.BaseEntity;
import com.chalkak.common.util.TimeUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "outbox", indexes = @Index(name = "idx_outbox_status_id", columnList = "status, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Outbox extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true)
  UUID requestId;

  @Column(nullable = false)
  Long auctionId;

  @Column(nullable = false, columnDefinition = "TEXT")
  String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  OutboxStatus status;

  LocalDateTime publishedAt;

  private Outbox(UUID requestId, Long auctionId, String payload) {
    this.requestId = requestId;
    this.auctionId = auctionId;
    this.payload = payload;
    this.status = OutboxStatus.PENDING;
  }

  public static Outbox pending(UUID requestId, Long auctionId, String payload) {
    return new Outbox(requestId, auctionId, payload);
  }

  public void markPublished() {
    this.status = OutboxStatus.PUBLISHED;
    this.publishedAt = TimeUtils.now();
  }

  public void markFailed() {
    this.status = OutboxStatus.FAILED;
  }
}
