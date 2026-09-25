import http from 'k6/http';
import {check} from 'k6';

export const options = {
  scenarios: {
    constant_rps: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 30,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
  const response = http.get('http://localhost:8080/api/v1/auctions');

  check(response, {
    'is status 200': (r) => r.status === 200,
  });
}
