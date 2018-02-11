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
     * BitSet, where n-th bit
     * indicates the availability of n-th piece.
     */
    private final BitSet pieces;

    /**
     * Total number of pieces in torrent.
     */
    private final int piecesTotal;

    /**
     * Number of pieces that have status {@link PieceStatus#COMPLETE_VERIFIED}.
     */
    private volatile int piecesComplete;

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
        this.piecesComplete = pieces.cardinality();
        this.lock = new ReentrantLock();
    }

    /**
     * @return Bitmask that describes status of all pieces.
     *         If position i is set to 1, then piece with index i
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
     *         If bit i is set to 1, then piece with index i
     *         is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 1.7
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
        return piecesComplete;
    }

    /**
     * @return Number of pieces that have status different from {@link PieceStatus#COMPLETE_VERIFIED}.
     *         I.e. it's the same as {@link #getPiecesTotal()} - {@link #getPiecesComplete()}
     * @since 1.0
     */
    public int getPiecesRemaining() {
        return piecesTotal - piecesComplete;
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
            piecesComplete = pieces.cardinality();
        } finally {
            lock.unlock();
        }
    }

    private void validatePieceIndex(Integer pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= piecesTotal) {
            throw new BtException("Illegal piece index: " + pieceIndex);
        }
    }
}
