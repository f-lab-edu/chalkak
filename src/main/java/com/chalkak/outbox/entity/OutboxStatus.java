package com.chalkak.outbox.entity;

public enum OutboxStatus {
  PENDING,    // 저장됨, 아직 발행 안 됨
  PUBLISHED,  // SQS 발행 완료
  FAILED      // 영구 발행 실패, 실패로 확정 (재발행 안 함)
}
