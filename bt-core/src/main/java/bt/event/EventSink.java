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
 * Provides API to generate runtime events.
 *
 * @since 1.5
 */
public interface EventSink {

    /**
     * Generate event, that a new peer has been discovered for some torrent.
     *
     * @since 1.5
     */
    void firePeerDiscovered(TorrentId torrentId, Peer peer, PeerSourceType peerSourceType);

    /**
     * Generate event, that a new outgoing connection with some peer can't be established.
     *
     * @since 0.0
     */
    void firePeerUnreachable(TorrentId torrentId, Peer peer);

    /**
     * Generate event, that a new connection with some peer has been established.
     *
     * @since 1.5
     */
    void firePeerConnected(TorrentId torrentId, Peer peer, boolean incoming, long connectionId, BitSet publishedPieces);

    /**
     * Generate event, that a connection with some peer has been terminated.
     *
     * @since 1.5
     */
    void firePeerDisconnected(TorrentId torrentId, Peer peer, long connectionId);

    /**
     * Generate event, that local information about some peer's data has been updated.
     *
     * @since 1.5
     */
    void firePeerBitfieldUpdated(TorrentId torrentId, Peer peer, BitSet pieces, int piecesTotal);

    /**
     * Generate event, that processing of some torrent has begun.
     *
     * @since 1.5
     */
    void fireTorrentStarted(TorrentId torrentId);

    /**
     * Generate event, that processing of some torrent has finished.
     *
     * @since 1.5
     */
    void fireTorrentStopped(TorrentId torrentId);
}
