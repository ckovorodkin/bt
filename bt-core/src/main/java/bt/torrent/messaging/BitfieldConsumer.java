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

import bt.net.Peer;
import bt.protocol.Bitfield;
import bt.protocol.Have;
import bt.torrent.annotation.Consumes;

import java.util.BitSet;

/**
 * Consumes peer bitfield.
 *
 * <p>Note that the local bitfield is sent to a remote peer
 * during the connection initialization sequence.
 *
 * @see bt.net.BitfieldConnectionHandler
 * @since 1.0
 */
public class BitfieldConsumer {
    private PeerManager peerManager;

    public BitfieldConsumer(PeerManager peerManager) {
        this.peerManager = peerManager;
    }

    @Consumes
    public void consume(Bitfield bitfieldMessage, MessageContext context) {
        Peer peer = context.getPeer();
        final BitSet pieces = BitSet.valueOf(bitfieldMessage.getBitfield());
        peerManager.addPieces(peer, pieces);
    }

    @Consumes
    public void consume(Have have, MessageContext context) {
        Peer peer = context.getPeer();
        peerManager.addPiece(peer, have.getPieceIndex());
    }
}
