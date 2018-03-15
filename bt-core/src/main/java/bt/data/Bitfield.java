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

package bt.data;

import bt.BtException;

import java.util.BitSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Status of torrent's data.
 *
 * Instances of this class are thread-safe.
 *
 * @since 1.0
 */
public class Bitfield {

    // TODO: use EMPTY and PARTIAL instead of INCOMPLETE
    /**
     * Status of a particular piece.
     *
     * @since 1.0
     */
    public enum PieceStatus {
        /*EMPTY, PARTIAL,*/INCOMPLETE, COMPLETE, COMPLETE_VERIFIED
    }

    /**
     * BitSet indicating verification of pieces.
     * If the n-th bit is set, then the n-th piece is verified.
     */
    private final BitSet verified;

    /**
     * Bitmask indicating pieces that should be skipped.
     * If the n-th bit is set, then the n-th piece should be skipped.
     */
    private volatile BitSet skipped;

    /**
     * BitSet indicating availability of pieces.
     * If the n-th bit is set, then the n-th piece is complete.
     */
    private final BitSet complete;

    /**
     * Total number of pieces in torrent.
     */
    private final int piecesTotal;

    private final ReentrantLock lock;

    /**
     * Creates empty bitfield.
     *
     * @param piecesTotal Total number of pieces in torrent.
     * @since 1.0
     */
    public Bitfield(int piecesTotal) {
        this.verified = new BitSet(piecesTotal);
        this.skipped = new BitSet(piecesTotal);
        this.complete = new BitSet(piecesTotal);
        this.piecesTotal = piecesTotal;
        this.lock = new ReentrantLock();
    }

    /**
     * @since 0.0
     */
    public BitSet getVerified() {
        lock.lock();
        try {
            return (BitSet) verified.clone();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @since 0.0
     */
    public BitSet getSkipped() {
        lock.lock();
        try {
            return (BitSet) skipped.clone();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @since 0.0
     */
    public BitSet getComplete() {
        lock.lock();
        try {
            return (BitSet) complete.clone();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return BitSet that describes status of all pieces.
     * If the n-th bit is set, then the n-th piece
     * is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 0.0
     */
    public BitSet getCompleteVerified() {
        lock.lock();
        try {
            final BitSet completeVerified = (BitSet) complete.clone();
            completeVerified.and(verified);
            return completeVerified;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return verified && !skipped && !complete
     * @since 0.0
     */
    public BitSet getRemaining() {
        lock.lock();
        try {
            final BitSet remaining = (BitSet) verified.clone();
            remaining.andNot(skipped);
            remaining.andNot(complete);
            return remaining;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Total number of pieces in torrent.
     * @since 1.0
     */
    public int getPiecesTotal() {
        return piecesTotal;
    }

    /**
     * @return Number of pieces that have been verified.
     * @since 0.0
     */
    public int getPiecesVerified() {
        return getVerified().cardinality();
    }

    /**
     * @return Number of pieces that have status {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.0
     */
    public int getPiecesCompleteVerified() {
        return getCompleteVerified().cardinality();
    }

    /**
     * @return Number of pieces that have status different {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.7
     */
    public int getPiecesIncomplete() {
        return piecesTotal - getPiecesCompleteVerified();
    }

    /**
     * @return Number of pieces that have status different from {@link PieceStatus#COMPLETE_VERIFIED}
     *         and should NOT be skipped.
     * @since 1.0
     */
    public int getPiecesRemaining() {
        return getRemaining().cardinality();
    }

    /**
     * @return Number of pieces that should be skipped
     * @since 1.7
     */
    public int getPiecesSkipped() {
        lock.lock();
        try {
            return skipped.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that should NOT be skipped
     * @since 1.7
     */
    public int getPiecesNotSkipped() {
        return piecesTotal - getPiecesSkipped();
    }

    /**
     * @param pieceIndex Piece index (0-based)
     * @return Status of the corresponding piece.
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public PieceStatus getPieceStatus(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        final boolean verified;
        final boolean complete;
        lock.lock();
        try {
            verified = this.verified.get(pieceIndex);
            complete = this.complete.get(pieceIndex);
        } finally {
            lock.unlock();
        }

        return complete ? verified ? PieceStatus.COMPLETE_VERIFIED : PieceStatus.COMPLETE : PieceStatus.INCOMPLETE;
    }

    /**
     * Shortcut method to find out if the piece has been downloaded.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded
     * @since 1.1
     */
    public boolean isComplete(int pieceIndex) {
        PieceStatus pieceStatus = getPieceStatus(pieceIndex);
        return (pieceStatus == PieceStatus.COMPLETE || pieceStatus == PieceStatus.COMPLETE_VERIFIED);
    }

    /**
     * Mark piece as complete.
     *
     * @param pieceIndex Piece index (0-based)
     * @since 0.0
     */
    public void markComplete(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        lock.lock();
        try {
            complete.set(pieceIndex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Shortcut method to find out if the piece has been downloaded and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded and verified
     * @since 0.0
     */
    public boolean isCompleteVerified(int pieceIndex) {
        PieceStatus pieceStatus = getPieceStatus(pieceIndex);
        return pieceStatus == PieceStatus.COMPLETE_VERIFIED;
    }

    /**
     * Mark piece as verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @since 0.0
     */
    public void markVerified(int pieceIndex, boolean correct) {
        validatePieceIndex(pieceIndex);

        lock.lock();
        try {
            verified.set(pieceIndex);
            if (!correct) {
                complete.clear(pieceIndex);
            }
        } finally {
            lock.unlock();
        }
    }

    private void validatePieceIndex(Integer pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= getPiecesTotal()) {
            throw new BtException("Illegal piece index: " + pieceIndex +
                    ", expected 0.." + (getPiecesTotal() - 1));
        }
    }

    /**
     * Mark a piece as skipped
     *
     * @since 1.7
     */
    public void skip(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        lock.lock();
        try {
            skipped.set(pieceIndex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark a piece as not skipped
     *
     * @since 1.7
     */
    public void unskip(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        lock.lock();
        try {
            skipped.clear(pieceIndex);
        } finally {
            lock.unlock();
        }
    }
}
