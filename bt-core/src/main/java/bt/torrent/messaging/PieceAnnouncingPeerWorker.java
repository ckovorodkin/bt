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

package bt.torrent.messaging;

import bt.data.Bitfield;
import bt.protocol.Have;
import bt.protocol.Message;
import bt.torrent.order.helper.RandomSetBit;

import java.util.BitSet;

/**
 * @since 1.0
 */
class PieceAnnouncingPeerWorker implements PeerWorker {
    private final PeerWorker delegate;
    private final Bitfield bitfield;
    private final BitSet publishedPieces;

    private final RandomSetBit randomSetBit;

    PieceAnnouncingPeerWorker(PeerWorker delegate, Bitfield bitfield, BitSet publishedPieces) {
        this.delegate = delegate;
        this.bitfield = bitfield;
        this.publishedPieces = publishedPieces;
        this.randomSetBit = new RandomSetBit();
    }

    @Override
    public ConnectionState getConnectionState() {
        return delegate.getConnectionState();
    }

    @Override
    public void accept(Message message) {
        delegate.accept(message);
    }

    @Override
    public Message get() {
        final int pieceIndex = getUnpublishedPieceIndex();
        if (pieceIndex != -1) {
            //todo oe: low priority, limit bulk size
            publishedPieces.set(pieceIndex);
            return new Have(pieceIndex);
        }
        return delegate.get();
    }

    private int getUnpublishedPieceIndex() {
        final BitSet unpublishedPieces = getUnpublishedPieces();
        return randomSetBit.apply(unpublishedPieces);
    }

    private BitSet getUnpublishedPieces() {
        final BitSet unpublishedPieces = bitfield.getCompleteVerified();
        unpublishedPieces.andNot(publishedPieces);
        return unpublishedPieces;
    }
}
