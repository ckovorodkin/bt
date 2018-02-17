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

package bt.statistic;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.Objects;

/**
 * @author Oleg Ermolaev Date: 17.02.2018 9:43
 */
public final class PeerKey {
    private final TorrentId torrentId;
    private final Peer peer;

    public PeerKey(TorrentId torrentId, Peer peer) {
        Objects.requireNonNull(torrentId);
        Objects.requireNonNull(peer);
        this.torrentId = torrentId;
        this.peer = peer;
    }

    public TorrentId getTorrentId() {
        return torrentId;
    }

    public Peer getPeer() {
        return peer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PeerKey peerKey = (PeerKey) o;
        return Objects.equals(torrentId, peerKey.torrentId) && Objects.equals(peer, peerKey.peer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(torrentId, peer);
    }
}
