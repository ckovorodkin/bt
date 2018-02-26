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
        /*EMPTY, PARTIAL,*/INCOMPLETE, /*COMPLETE,*/ COMPLETE_VERIFIED
    }

    /**
     * BitSet indicating availability of pieces.
     * If the n-th bit is set, then the n-th piece is complete and verified.
     */
    private final BitSet pieces;

    /**
     * Bitmask indicating pieces that should be skipped.
     * If the n-th bit is set, then the n-th piece should be skipped.
     */
    private volatile BitSet skipped;

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
        this.pieces = new BitSet(piecesTotal);
        this.piecesTotal = piecesTotal;
        this.lock = new ReentrantLock();
    }

    /**
     * @return Bitmask that describes status of all pieces.
     *         If the n-th bit is set, then the n-th piece
     *         is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 1.0
     */
    public byte[] getBitmask() {
        lock.lock();
        try {
            final byte[] bytes = pieces.toByteArray();
            final int length = (piecesTotal + 7) / 8;
            if (bytes.length == length) {
                return bytes;
            }
            final byte[] result = new byte[length];
            System.arraycopy(bytes, 0, result, 0, bytes.length);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return BitSet that describes status of all pieces.
     *         If the n-th bit is set, then the n-th piece
     *         is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 0.0
     */
    public BitSet getPieces() {
        lock.lock();
        try {
            return (BitSet) pieces.clone();
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
     * @return Number of pieces that have status {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.0
     */
    public int getPiecesComplete() {
        lock.lock();
        try {
            return pieces.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that have status different {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.7
     */
    public int getPiecesIncomplete() {
        lock.lock();
        try {
            return getPiecesTotal() - pieces.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that have status different from {@link PieceStatus#COMPLETE_VERIFIED}
     *         and should NOT be skipped.
     * @since 1.0
     */
    public int getPiecesRemaining() {
        lock.lock();
        try {
            if (skipped == null) {
                return getPiecesTotal() - getPiecesComplete();
            } else {
                BitSet bitmask = getPieces();
                bitmask.or(skipped);
                return getPiecesTotal() - bitmask.cardinality();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that should be skipped
     * @since 1.7
     */
    public int getPiecesSkipped() {
        if (skipped == null) {
            return 0;
        }

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
        if (skipped == null) {
            return piecesTotal;
        }

        lock.lock();
        try {
            return piecesTotal - skipped.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param pieceIndex Piece index (0-based)
     * @return Status of the corresponding piece.
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public PieceStatus getPieceStatus(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        boolean verified;
        lock.lock();
        try {
            verified = pieces.get(pieceIndex);
        } finally {
            lock.unlock();
        }

        return verified ? PieceStatus.COMPLETE_VERIFIED : PieceStatus.INCOMPLETE;
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
        return (/*pieceStatus == PieceStatus.COMPLETE ||*/ pieceStatus == PieceStatus.COMPLETE_VERIFIED);
    }

    /**
     * Shortcut method to find out if the piece has been downloaded and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded and verified
     * @since 1.1
     */
    public boolean isVerified(int pieceIndex) {
        PieceStatus pieceStatus = getPieceStatus(pieceIndex);
        return pieceStatus == PieceStatus.COMPLETE_VERIFIED;
    }

    /**
     * Mark piece as complete and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public void markVerified(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        lock.lock();
        try {
            pieces.set(pieceIndex);
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
            if (skipped == null) {
                skipped = new BitSet(getPiecesTotal());
            }
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

        if (skipped != null) {
            lock.lock();
            try {
                skipped.clear(pieceIndex);
            } finally {
                lock.unlock();
            }
        }
    }
}
