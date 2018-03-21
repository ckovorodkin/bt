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

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 18.03.2018 4:23
 */
public class PieceOrderDelegate implements PieceOrder {
    private volatile PieceOrder delegate;

    public PieceOrderDelegate() {
    }

    public PieceOrderDelegate(PieceOrder delegate) {
        this.delegate = delegate;
    }

    public PieceOrder getDelegate() {
        return delegate;
    }

    public void setDelegate(PieceOrder delegate) {
        this.delegate = delegate;
    }

    @Override
    public BitSet getMask() {
        if (delegate == null) {
            return new BitSet();
        }
        return delegate.getMask();
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        if (delegate == null) {
            return -1;
        }
        return delegate.next(accumulator, mask);
    }
}
