import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const reservationSuccess = new Counter('reservation_success');
const seatTakenConflict = new Counter('seat_taken_conflict');
const serverErrors = new Counter('server_errors');
const unexpectedStatus = new Counter('unexpected_status');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        hotSeatRace: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 20000,
            maxDuration: '10m'
        }
    }
};

export function setup() {

    const payload = JSON.stringify({
        name: `hot-seat-race-${Date.now()}`,
        seats: ['HOT-1'],
        price_paise: 25000
    });

    const response = http.post(
        `${BASE_URL}/shows`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    const created = check(response, {
        'show created successfully': (r) => r.status === 201
    });

    if (!created) {
        throw new Error(
            `Unable to create test show. Status=${response.status}, body=${response.body}`
        );
    }

    const show = response.json();

    console.log(`Created test show: ${show.id}`);

    return {
        showId: show.id
    };
}

export default function (data) {

    const attemptId = exec.scenario.iterationInTest;

    const payload = JSON.stringify({
        seats: ['HOT-1'],
        idempotency_key: `hot-seat-key-${attemptId}`
    });

    const response = http.post(
        `${BASE_URL}/shows/${data.showId}/reserve`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer hot-seat-user-${attemptId}`
            }
        }
    );

    if (response.status === 201) {
        reservationSuccess.add(1);
        return;
    }

    if (response.status === 409) {
        seatTakenConflict.add(1);
        return;
    }

    if (response.status >= 500) {
        serverErrors.add(1);
        return;
    }

    unexpectedStatus.add(1);
}

export function teardown(data) {

    const response = http.get(
        `${BASE_URL}/shows/${data.showId}`
    );

    const inventoryCorrect = check(response, {
        'show fetched successfully': (r) => r.status === 200,

        'exactly one seat confirmed': (r) => {
            if (r.status !== 200) {
                return false;
            }

            const show = r.json();

            return show.counts.confirmed === 1;
        },

        'no seats remain available': (r) => {
            if (r.status !== 200) {
                return false;
            }

            const show = r.json();

            return show.counts.available === 0;
        },

        'inventory reconciliation holds': (r) => {
            if (r.status !== 200) {
                return false;
            }

            const show = r.json();

            return (
                show.counts.available +
                show.counts.held +
                show.counts.confirmed
                === show.counts.total
            );
        }
    });

    if (!inventoryCorrect) {
        console.error(
            `Final inventory verification failed: ${response.body}`
        );
    } else {
        console.log(
            `Hot-seat test completed successfully for show ${data.showId}`
        );
    }
}