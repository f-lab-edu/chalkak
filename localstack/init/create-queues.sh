#!/bin/bash
awslocal sqs create-queue --queue-name bid-queue.fifo --attributes FifoQueue=true,ContentBasedDeduplication=true
