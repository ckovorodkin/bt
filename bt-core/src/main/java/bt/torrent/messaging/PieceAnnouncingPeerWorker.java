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

import bt.protocol.Have;
import bt.protocol.Message;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * @since 1.0
 */
class PieceAnnouncingPeerWorker implements PeerWorker {
    private final PeerWorker delegate;
    private final Supplier<Iterable<? extends PeerWorker>> activePeerWorkersSupplier;
    private final Queue<Have> pieceAnnouncements;

    PieceAnnouncingPeerWorker(
            PeerWorker delegate, Supplier<Iterable<? extends PeerWorker>> activePeerWorkersSupplier) {
        this.delegate = delegate;
        this.activePeerWorkersSupplier = activePeerWorkersSupplier;
        this.pieceAnnouncements = new ConcurrentLinkedQueue<>();
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
        Message message = pieceAnnouncements.poll();
        if (message != null) {
            //todo oe: low priority
            return message;
        }

        message = delegate.get();
        if (message != null && Have.class.equals(message.getClass())) {
            Have have = (Have) message;
            activePeerWorkersSupplier.get().forEach(worker -> {
                if (this != worker && PieceAnnouncingPeerWorker.class.isAssignableFrom(worker.getClass())) {
                    PieceAnnouncingPeerWorker.class.cast(worker).getPieceAnnouncements().add(have);
                }
            });
        }
        return message;
    }

    private Queue<Have> getPieceAnnouncements() {
        return pieceAnnouncements;
    }
}
