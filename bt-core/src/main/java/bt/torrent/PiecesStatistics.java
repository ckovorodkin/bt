/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
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

package bt.torrent;

import bt.data.Bitfield;
import bt.net.Peer;
import bt.torrent.order.PieceOrder;

import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acts as a storage for peers' bitfields and provides aggregate piece statistics.
 * This class is thread-safe.
 *
 * @since 0.0
 */
public class PiecesStatistics {

    private final Map<Peer, BitSet> peerPiecesMap;
    private final BitSetAccumulator accumulator;

    /**
     * Create statistics, based on the local peer's bitfield.
     *
     * @since 0.0
     */
    public PiecesStatistics(int piecesTotal) {
        this.peerPiecesMap = new ConcurrentHashMap<>();
        this.accumulator = new BitSetAccumulator(piecesTotal);
    }

    /**
     * Add peer's bitfield.
     *
     * @since 0.0
     */
    public void addPieces(Peer peer, BitSet pieces) {
        addPieces0(peer, copyOf(pieces));
    }

    private void addPieces0(Peer peer, BitSet pieces) {
        validateLength(pieces.length());
        synchronized (accumulator) {
            final BitSet previous = peerPiecesMap.put(peer, pieces);
            if (previous != null) { //todo is it ok?
                remove0(previous);
            }
            accumulator.add(pieces);
        }
    }

    /**
     * Remove peer's bitfield.
     *
     * @since 0.0
     */
    public void removePieces(Peer peer) {
        if (!peerPiecesMap.containsKey(peer)) {
            return;
        }
        synchronized (accumulator) {
            final BitSet previous = peerPiecesMap.remove(peer);
            if (previous != null) {
                remove0(previous);
            }
        }
    }

    private void remove0(BitSet previous) {
        final BitSet remainder = accumulator.remove(previous);
        if (remainder.cardinality() > 0) {
            assert false;
            accumulator.clear();
            peerPiecesMap.values().forEach(accumulator::add);
        }
    }

    private void validateLength(int length) {
        if (length > accumulator.getLength()) {
            throw new IllegalArgumentException("Bitfield has invalid length (" + length +
                    "). Expected number of pieces: " + accumulator.getLength());
        }
    }

    /**
     * Update peer's bitfield by indicating that the peer has a given piece.
     *
     * @since 0.0
     */
    public void addPiece(Peer peer, int pieceIndex) {
        validateLength(pieceIndex);
        synchronized (accumulator) {
            BitSet pieces = peerPiecesMap.get(peer);
            if (pieces == null) {
                pieces = new BitSet(accumulator.getLength());
                final BitSet previous = peerPiecesMap.put(peer, pieces);
                assert previous == null;
            }
            if (!pieces.get(pieceIndex)) {
                pieces.set(pieceIndex);
                accumulator.add(pieceIndex);
            }
        }
    }

    /**
     * Get peer's bitfield, if present.
     *
     * @since 0.0
     */
    public Optional<BitSet> getPieces(Peer peer) {
        return Optional.ofNullable(peerPiecesMap.get(peer)).flatMap(pieces -> {
            synchronized (accumulator) {
                return Optional.of(copyOf(pieces));
            }
        });
    }

    public int getPiecesTotal() {
        return accumulator.getLength();
    }

    public double getRatio(Bitfield localBitfield) {
        final double ratio;
        final BitSet local = localBitfield.getCompleteVerified();
        synchronized (accumulator) {
            ratio = accumulator.getRatio(local);
        }
        return ratio;
    }


    public int next(PieceOrder pieceOrder, BitSet mask, Peer peer) {
        if (!peerPiecesMap.containsKey(peer)) {
            return -1;
        }
        synchronized (accumulator) {
            final BitSet pieces = peerPiecesMap.get(peer);
            if (pieces == null) {
                return -1;
            }
            final BitSet mixedMask = copyOf(pieces);
            mixedMask.and(mask);
            return pieceOrder.next(accumulator, mixedMask);
        }
    }

    private BitSet copyOf(BitSet bitSet) {
        return (BitSet) bitSet.clone();
    }
}
