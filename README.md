<p align="center">
  <img src="docs/images/logo.svg" width="120" alt="chalkak logo">
</p>

<h1 align="center">찰칵!</h1>

## 프로젝트 개요

찰칵은 중고 카메라를 경매로 사고파는 서비스입니다.
판매자가 경매를 등록하면 구매자는 충전한 포인트로 입찰하고, 마감 시각이 지나면 경매가 종료됩니다.

### 주요 기능

- **회원**: 회원가입, 로그인
- **경매**: 이미지를 포함한 경매 등록, 카테고리·상태 등급·키워드 검색과 정렬을 지원하는 목록 조회, 상세·상태 조회
- **포인트**: 포인트 충전·조회, 입찰 시 포인트 잠금(상위 입찰이 들어오면 이전 입찰자 포인트 해제)
- **입찰**: SQS FIFO 큐로 입찰 요청을 접수하고 비동기로 처리, 입찰 요청 결과 조회
- **마감 연장**: 마감 5분 전 이내 입찰 시 5분 연장(최대 25분)
- **실시간 알림**: SSE로 경매 상태 변경을 구독자 전체에 전달하고, 입찰자 본인에게 입찰 수락/거절 결과 전달
- **경매 마감**: 마감 스케줄러가 마감 시각이 지난 경매를 낙찰/유찰 상태로 종료

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.5, Spring Security, Spring Data JPA, QueryDSL 5.1 |
| Database | PostgreSQL 16 |
| Messaging | AWS SQS (FIFO), Spring Cloud AWS 3.4, LocalStack(로컬 환경) |
| Monitoring | Prometheus, Grafana, Tempo, OpenTelemetry, postgres-exporter |
| Load Test | k6 |
| Test | JUnit 5, Testcontainers |
| Infra | Docker, Docker Compose |
