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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

class MockPeerConnectionPool implements IPeerConnectionPool {
    private final ConcurrentMap<Peer, PeerConnection> map = new ConcurrentHashMap<>();

    @Override
    public PeerConnection getConnection(Peer peer) {
        return map.get(peer);
    }

    @Override
    public void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor) {
        map
                .values()
                .stream()
                .filter(peerConnection -> torrentId.equals(peerConnection.getTorrentId()))
                .forEach(visitor::accept);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public PeerConnection addConnectionIfAbsent(PeerConnection connection) {
        return Optional.ofNullable(map.putIfAbsent(connection.getRemotePeer(), connection)).orElse(connection);
    }
}
