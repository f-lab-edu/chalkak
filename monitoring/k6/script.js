import http from 'k6/http';
import { check } from 'k6';

const AUCTION_ID = __ENV.AUCTION_ID || 1;

export const options = {
  vus: 10,
  duration: '30s',
};

export default function () {
  const response = http.get(`http://localhost:8080/api/v1/auctions/${AUCTION_ID}`);

  check(response, {
    'is status 200': (r) => r.status === 200,
  });
}
