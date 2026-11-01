package com.chalkak.outbox.entity;

public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED // 영구 발행 실패, 실패로 확정 (재발행 안 함)
}
