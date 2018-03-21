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

package bt.torrent.order;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 18.03.2018 2:48
 */
public abstract class AbstractPieceOrder implements PieceOrder {
    private final BitSet mask;

    public AbstractPieceOrder(BitSet mask) {
        this.mask = mask;
    }

    @Override
    public BitSet getMask() {
        return (BitSet) mask.clone();
    }

    protected BitSet getComplexMask(BitSet mask) {
        final BitSet complexMask = (BitSet) this.mask.clone();
        complexMask.and(mask);
        return complexMask;
    }
}
