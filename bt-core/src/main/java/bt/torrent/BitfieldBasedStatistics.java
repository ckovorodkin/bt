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
 * @since 1.0
 */
public class BitfieldBasedStatistics {

    private final Bitfield localBitfield;
    private final Map<Peer, Bitfield> peerBitfields;
    private final BitSetAccumulator accumulator;

    /**
     * Create statistics, based on the local peer's bitfield.
     *
     * @since 1.0
     */
    public BitfieldBasedStatistics(Bitfield localBitfield) {
        this.localBitfield = localBitfield;
        this.peerBitfields = new ConcurrentHashMap<>();
        this.accumulator = new BitSetAccumulator(localBitfield.getPiecesTotal());
    }

    /**
     * Add peer's bitfield.
     * For each piece, that the peer has, total count will be incremented by 1.
     *
     * @since 1.0
     */
    public void addBitfield(Peer peer, Bitfield bitfield) {
        validateBitfieldLength(bitfield);
        final Bitfield previous = peerBitfields.put(peer, bitfield);
        synchronized (accumulator) {
            if (previous != null) { //todo is it ok?
                accumulator.remove(previous.getBitSet());
            }
            accumulator.add(bitfield.getBitSet());
        }
    }

    /**
     * Remove peer's bitfield.
     * For each piece, that the peer has, total count will be decremented by 1.
     *
     * @since 1.0
     */
    public void removeBitfield(Peer peer) {
        Bitfield bitfield = peerBitfields.remove(peer);
        if (bitfield == null) {
            return;
        }
        synchronized (accumulator) {
            accumulator.remove(bitfield.getBitSet());
        }
    }

    private void validateBitfieldLength(Bitfield bitfield) {
        if (bitfield.getPiecesTotal() != accumulator.getLength()) {
            throw new IllegalArgumentException("Bitfield has invalid length (" + bitfield.getPiecesTotal() +
                    "). Expected number of pieces: " + accumulator.getLength());
        }
    }

    /**
     * Update peer's bitfield by indicating that the peer has a given piece.
     * Total count of the specified piece will be incremented by 1.
     *
     * @since 1.0
     */
    public void addPiece(Peer peer, Integer pieceIndex) {
        Bitfield bitfield = peerBitfields.get(peer);
        if (bitfield == null) {
            bitfield = new Bitfield(accumulator.getLength());
            Bitfield existing = peerBitfields.putIfAbsent(peer, bitfield);
            if (existing != null) {
                bitfield = existing;
            }
        }

        markPieceVerified(bitfield, pieceIndex);
    }

    private synchronized void markPieceVerified(Bitfield bitfield, Integer pieceIndex) {
        if (!bitfield.isVerified(pieceIndex)) {
            synchronized (accumulator) {
                bitfield.markVerified(pieceIndex);
                accumulator.add(pieceIndex);
            }
        }
    }

    /**
     * Get peer's bitfield, if present.
     *
     * @since 1.0
     */
    public Optional<Bitfield> getPeerBitfield(Peer peer) {
        return Optional.ofNullable(peerBitfields.get(peer));
    }

    public int getPiecesTotal() {
        return accumulator.getLength();
    }

    public double getRatio() {
        final double ratio;
        final BitSet local = localBitfield.getBitSet();
        synchronized (accumulator) {
            ratio = accumulator.getRatio(local);
        }
        return ratio;
    }


    public int next(PieceOrder pieceOrder, BitSet mask, Peer peer) {
        final Bitfield bitfield = peerBitfields.get(peer);
        if (bitfield == null) {
            return -1;
        }
        final BitSet mixedMask = bitfield.getBitSet();
        mixedMask.and(mask);
        synchronized (accumulator) {
            return pieceOrder.next(accumulator, mixedMask);
        }
    }
}
