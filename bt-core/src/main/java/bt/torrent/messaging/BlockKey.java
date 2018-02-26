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

package bt.torrent.messaging;

import bt.protocol.Piece;

import java.util.Arrays;

/**
 * Keys, that can be tested for equality with each other.
 *
 * @since 1.0
 */
class BlockKey {

    private final int[] key;

    private BlockKey(int pieceIndex, int offset, int length) {
        this.key = new int[]{pieceIndex, offset, length};
    }

    /**
     * Create a unique key for a block request, cancel request or received piece.
     *
     * @since 0.0
     */
    public static BlockKey buildBlockKey(Piece piece) {
        return buildBlockKey(piece.getPieceIndex(), piece.getOffset(), piece.getBlock().length);
    }

    /**
     * Create a unique key for a block request, cancel request or received piece.
     *
     * @since 1.0
     */
    public static BlockKey buildBlockKey(int pieceIndex, int offset, int length) {
        return new BlockKey(pieceIndex, offset, length);
    }

    public int getPieceIndex() {
        return key[0];
    }

    public int getOffset() {
        return key[1];
    }

    public int getLength() {
        return key[2];
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(key);
    }

    @Override
    public boolean equals(Object obj) {
        //noinspection SimplifiableIfStatement
        if (obj == null || !BlockKey.class.equals(obj.getClass())) {
            return false;
        }
        return (obj == this) || Arrays.equals(key, ((BlockKey) obj).key);
    }
}
