#!/bin/bash
set -e

# 메시지 하나를 최대 몇 번 받을지 (첫 수신 포함, 재시도 횟수 = 이 값 - 1)
# 예: 3이면 1번 처리 + 2번 재시도. 앱은 이 횟수째에도 실패하면 입찰 실패 응답 후 DLQ로 직접 옮긴다
BID_QUEUE_MAX_RECEIVE_COUNT=${BID_QUEUE_MAX_RECEIVE_COUNT:-3}

# 메시지를 받은 뒤 다른 컨슈머에게 안 보이게 숨기는 시간(초)
# 이 시간 안에 처리 완료(ack = 큐에서 삭제)를 못 하면 메시지가 다시 보이고 재수신된다
# 즉 재시도 간격이자, 재시도 동안 같은 경매(같은 메시지 그룹)의 다음 입찰이 기다리는 시간
BID_QUEUE_VISIBILITY_TIMEOUT=${BID_QUEUE_VISIBILITY_TIMEOUT:-10}

# DLQ(Dead Letter Queue): 처리에 실패한 메시지를 따로 보관하는 큐. 원래 큐로 되돌리지 않는다 (입찰 순서가 깨지므로)
# FIFO 큐의 DLQ는 FIFO여야 한다
# MessageRetentionPeriod: 메시지 보관 기간(초), 1209600 = 14일(최대값)
awslocal sqs create-queue --queue-name bid-dlq.fifo \
  --attributes FifoQueue=true,MessageRetentionPeriod=1209600

# 리전/계정이 바뀌어도 맞도록 DLQ의 ARN(AWS 리소스 식별자)을 하드코딩하지 않고 조회한다
BID_DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "$(awslocal sqs get-queue-url --queue-name bid-dlq.fifo --query QueueUrl --output text)" \
  --attribute-names QueueArn \
  --query Attributes.QueueArn --output text)

# 입찰 큐
# FifoQueue: 같은 메시지 그룹(= 경매) 안에서는 보낸 순서대로, 한 번에 하나씩 처리된다
# ContentBasedDeduplication: 중복 제거 ID를 안 주면 본문 해시로 대신한다 (프로듀서는 requestId를 직접 준다)
# RedrivePolicy: 수신 횟수가 maxReceiveCount를 넘으면 SQS가 deadLetterTargetArn(DLQ)으로 자동으로 옮긴다
awslocal sqs create-queue --queue-name bid-queue.fifo --attributes "{
  \"FifoQueue\": \"true\",
  \"ContentBasedDeduplication\": \"true\",
  \"VisibilityTimeout\": \"${BID_QUEUE_VISIBILITY_TIMEOUT}\",
  \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${BID_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${BID_QUEUE_MAX_RECEIVE_COUNT}\\\"}\"
}"
