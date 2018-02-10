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

package bt.event;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.BitSet;

/**
 * Indicates, that local information about some peer's data has been updated.
 *
 * @since 1.5
 */
public class PeerBitfieldUpdatedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Peer peer;
    private final BitSet pieces;
    private final int piecesTotal;

    protected PeerBitfieldUpdatedEvent(
            long id, long timestamp, TorrentId torrentId, Peer peer, BitSet pieces, int piecesTotal) {
        super(id, timestamp);
        this.torrentId = torrentId;
        this.peer = peer;
        this.pieces = pieces;
        this.piecesTotal = piecesTotal;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.5
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @since 1.5
     */
    public BitSet getPieces() {
        return pieces;
    }

    /**
     * @since 1.7
     */
    public int getPiecesTotal() {
        return piecesTotal;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, torrent {" + torrentId + "}, peer {" + peer + "}";
    }
}
