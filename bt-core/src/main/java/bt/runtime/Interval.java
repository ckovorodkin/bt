/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.runtime;

import java.time.Duration;
import java.util.Random;

import static java.lang.Math.min;
import static java.lang.Math.round;

/**
 * @author Oleg Ermolaev Date: 26.02.2018 12:31
 */
public class Interval {
    private final Duration initial;
    private final Duration max;
    private final double factor;
    private final double spread;
    private final Random random;

    public Interval(Duration initial, Duration max, double factor, double spread) {
        if (initial.toMillis() < 1) {
            throw new IllegalArgumentException(String.valueOf(initial));
        }
        if (max.toMillis() < 1) {
            throw new IllegalArgumentException(String.valueOf(max));
        }
        if (factor < 1.0) {
            throw new IllegalArgumentException(String.valueOf(factor));
        }
        if (spread < 0.0 || 0.5 < spread) {
            throw new IllegalArgumentException(String.valueOf(spread));
        }
        this.initial = initial;
        this.max = max;
        this.factor = factor;
        this.spread = spread;
        this.random = spread == 0.0 ? null : new Random(System.currentTimeMillis());
    }

    public boolean isExpired(long current, long lastAttempt, int attemptCount) {
        final long elapsed = current - lastAttempt;
        final long interval = getInterval(attemptCount);
        return elapsed >= interval;
    }

    public long getInterval(int attemptCount) {
        assert attemptCount >= 0;
        double current = initial.toMillis();
        for (int i = 0; i < attemptCount; ++i) {
            current *= factor;
        }
        final long interval = min(round(current), max.toMillis());
        final int maxDelta = (int) min(round(interval * spread), Integer.MAX_VALUE);
        final int delta = maxDelta == 0 ? 0 : random.nextInt(maxDelta);
        return interval + delta - (maxDelta >> 1);
    }
}
