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
import java.util.Collection;
import java.util.Optional;

/**
 * @author Oleg Ermolaev Date: 18.03.2018 2:53
 */
public class ComplexPieceOrder extends AbstractPieceOrder {
    private final Collection<PieceOrder> pieceOrders;

    public ComplexPieceOrder(Collection<PieceOrder> pieceOrders) {
        super(getComplexMask(pieceOrders));
        this.pieceOrders = pieceOrders;
    }

    private static BitSet getComplexMask(Collection<PieceOrder> pieceOrders) {
        if (pieceOrders.isEmpty()) {
            return new BitSet();
        }
        final BitSet mask = new BitSet();
        pieceOrders.forEach(pieceOrder -> mask.or(pieceOrder.getMask()));
        return mask;
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        for (PieceOrder pieceOrder : pieceOrders) {
            final int next = pieceOrder.next(accumulator, mask);
            if (next != -1) {
                return next;
            }
        }
        return -1;
    }

    @Override
    public Optional<BitSet> getCurrentMask(int pieceIndex) {
        for (PieceOrder pieceOrder : pieceOrders) {
            final Optional<BitSet> currentMask = pieceOrder.getCurrentMask(pieceIndex);
            if (currentMask.isPresent()) {
                return currentMask;
            }
        }
        return Optional.empty();
    }
}
