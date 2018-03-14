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
import java.util.Objects;

/**
 * Indicates, that a new connection with some peer has been established.
 *
 * @since 1.5
 */
public class PeerConnectedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Peer peer;
    private final boolean incoming;
    private final long connectionId;
    private final BitSet publishedPieces;

    protected PeerConnectedEvent(long id,
                                 long timestamp,
                                 TorrentId torrentId,
                                 Peer peer,
                                 boolean incoming,
                                 long connectionId,
                                 BitSet publishedPieces) {
        super(id, timestamp);
        this.torrentId = Objects.requireNonNull(torrentId);
        this.peer = Objects.requireNonNull(peer);
        this.incoming = incoming;
        this.connectionId = connectionId;
        this.publishedPieces = Objects.requireNonNull(publishedPieces);
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
     * @since 0.0
     */
    public boolean isIncoming() {
        return incoming;
    }

    /**
     * @since 0.0
     */
    public long getConnectionId() {
        return connectionId;
    }

    /**
     * @since 0.0
     */
    public BitSet getPublishedPieces() {
        return (BitSet) publishedPieces.clone();
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, torrent {" + torrentId + "}, peer {" + peer + "}, incoming {" + incoming + "}, connectionId {"
                + connectionId + "}, publishedPieces {" + publishedPieces.cardinality() + "}";
    }
}
