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
import bt.protocol.Message;

import java.io.IOException;
import java.nio.channels.SocketChannel;

class MockPeerConnectionFactory implements IPeerConnectionFactory {
    @Override
    public ConnectionResult createOutgoingConnection(Peer peer, TorrentId torrentId) {
        return ConnectionResult.success(new PeerConnection() {
            @Override
            public Peer getRemotePeer() {
                return peer;
            }

            @Override
            public TorrentId setTorrentId(TorrentId torrentId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TorrentId getTorrentId() {
                return torrentId;
            }

            @Override
            public Message readMessageNow() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Message readMessage(long timeout) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void postMessage(Message message) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getLastActive() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void closeQuietly() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isClosed() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() throws IOException {
                throw new UnsupportedOperationException();
            }
        });
    }

    @Override
    public ConnectionResult createIncomingConnection(Peer peer, SocketChannel channel) {
        throw new UnsupportedOperationException();
    }
}
