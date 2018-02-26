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

package bt.net;

import bt.metainfo.TorrentId;

import java.net.SocketAddress;
import java.util.function.Consumer;

/**
 * Provides connection pooling.
 *
 * @since 1.0
 */
public interface IPeerConnectionPool {

    /**
     * @return Connection for a given peer, if exists; null otherwise
     * @since 1.0
     */
    PeerConnection getConnection(Peer peer);

    /**
     * Visit connections for a given torrent ID.
     *
     * @since 1.5
     */
    void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor);

    /**
     * @return Number of established connections
     * @since 1.6
     */
    int size();

    /**
     * @since 1.7
     */
    boolean mightAddIncomingConnection(SocketAddress remoteAddress);

    /**
     * @since 1.7
     */
    boolean mightAddOutgoingConnection(TorrentId torrentId, SocketAddress remoteAddress);

    /**
     * @return Newly added or existing connection
     * @since 1.6
     */
    PeerConnection addConnectionIfAbsent(PeerConnection connection);

    /**
     * @since 1.7
     */
    void disconnect(Peer peer);
}
