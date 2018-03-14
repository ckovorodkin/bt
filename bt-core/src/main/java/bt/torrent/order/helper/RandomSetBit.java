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

package bt.torrent.order.helper;

import java.util.BitSet;
import java.util.Random;

/**
 * @author Oleg Ermolaev Date: 14.03.2018 18:59
 */
public class RandomSetBit {
    private final Random random;

    public RandomSetBit() {
        this.random = new Random(System.currentTimeMillis());
    }

    public int apply(BitSet bitSet) {
        final int cardinality = bitSet.cardinality();
        if (cardinality == 0) {
            return -1;
        }
        final int position = random.nextInt(cardinality);
        int current = -1;
        for (int i = 0; i < position + 1; i++) {
            current = bitSet.nextSetBit(current + 1);
        }
        assert current != -1;
        return current;
    }
}
