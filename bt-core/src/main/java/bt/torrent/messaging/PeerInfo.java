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

package bt.torrent.messaging;

import bt.event.PeerSourceType;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.statistic.TransferAmount;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * @author Oleg Ermolaev Date: 18.02.2018 8:44
 */
public class PeerInfo implements PeerInfoView {
    private final Peer peer;
    private final TorrentId torrentId;
    private final int piecesTotal;
    private final Set<PeerSourceType> peerSourceTypes;
    private final AtomicReference<PeerState> peerState;
    private volatile Long connectAt;
    private final AtomicReference<Long> connectionId;
    private final AtomicInteger connectAttempts;
    private final AtomicInteger failedConnectAttempts;
    private final AtomicReference<Long> lastConnectAttemptAt;
    private final TransferAmount transferAmount;
    private volatile long onConnectTransferAmount;
    private volatile int pieces;

    public PeerInfo(Peer peer,
                    TorrentId torrentId,
                    int piecesTotal,
                    PeerSourceType peerSourceType,
                    TransferAmount transferAmount) {
        this.peer = requireNonNull(peer);
        this.torrentId = requireNonNull(torrentId);
        this.piecesTotal = piecesTotal;
        this.peerSourceTypes = ConcurrentHashMap.newKeySet();
        this.peerSourceTypes.add(requireNonNull(peerSourceType));
        this.peerState = new AtomicReference<>(PeerState.DISCONNECTED);
        this.connectAt = null;
        this.connectionId = new AtomicReference<>(null);
        this.connectAttempts = new AtomicInteger(0);
        this.failedConnectAttempts = new AtomicInteger();
        this.lastConnectAttemptAt = new AtomicReference<>(null);
        this.transferAmount = requireNonNull(transferAmount);
        this.onConnectTransferAmount = 0;
        this.pieces = 0;
    }

    @Override
    public Peer getPeer() {
        return peer;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public int getPiecesTotal() {
        return piecesTotal;
    }

    @Override
    public Set<PeerSourceType> getPeerSourceTypes() {
        return Collections.unmodifiableSet(peerSourceTypes);
    }

    void addPeerSourceType(PeerSourceType peerSourceType) {
        this.peerSourceTypes.add(peerSourceType);
    }

    @Override
    public PeerState getPeerState() {
        return peerState.get();
    }

    boolean setPeerState(long timestamp, PeerState expect, PeerState update) {
        final boolean updated = peerState.compareAndSet(expect, update);
        if (updated) {
            if (update == PeerState.CONNECTING) {
                connectAt = null;
                connectAttempts.incrementAndGet();
            }
            if (expect == PeerState.CONNECTING && update == PeerState.DISCONNECTED) {
                lastConnectAttemptAt.set(timestamp);
                failedConnectAttempts.incrementAndGet();
            }
        }
        return updated;
    }

    @Override
    public Long getConnectAt() {
        return connectAt;
    }

    public void setConnectAt(Long connectAt) {
        this.connectAt = connectAt;
    }

    public long getOnConnectTransferAmount() {
        return onConnectTransferAmount;
    }

    public void setOnConnectTransferAmount(long onConnectTransferAmount) {
        this.onConnectTransferAmount = onConnectTransferAmount;
    }

    public Long getConnectionId() {
        return connectionId.get();
    }

    public void connect(@SuppressWarnings("UnusedParameters") long timestamp, long connectionId) {
        assert this.connectionId.get() == null;
        peerState.set(PeerState.ACTIVE);
        this.connectionId.set(connectionId);
        onConnectTransferAmount = transferAmount.getDownload() + transferAmount.getUpload();
    }

    public boolean disconnect(long timestamp, long connectionId) {
        // can't use "this.connectionId.compareAndSet(connectionId, null);"
        // because this.connectionId is AtomicReference<Long>, not AtomicLong
        final Long currentConnectionId = this.connectionId.get();
        final boolean disconnected = currentConnectionId != null && currentConnectionId == connectionId &&
                this.connectionId.compareAndSet(currentConnectionId, null);
        if (disconnected) {
            assert peerState.get() == PeerState.ACTIVE;
            peerState.set(PeerState.DISCONNECTED);
            if (lastConnectAttemptAt.get() == null) {
                failedConnectAttempts.set(0);
            } else {
                final long onDisconnectTransferAmount = transferAmount.getDownload() + transferAmount.getUpload();
                final long perConnectionTransferAmount = onDisconnectTransferAmount - onConnectTransferAmount;
                if (perConnectionTransferAmount == 0) {
                    failedConnectAttempts.incrementAndGet();
                } else {
                    failedConnectAttempts.set(0);
                }
            }
            lastConnectAttemptAt.set(timestamp);
        }
        return disconnected;
    }

    @Override
    public int getConnectAttempts() {
        return connectAttempts.get();
    }

    public int getFailedConnectAttempts() {
        return failedConnectAttempts.get();
    }

    public Long getLastConnectAttemptAt() {
        return lastConnectAttemptAt.get();
    }

    @Override
    public TransferAmount getTransferAmount() {
        return transferAmount;
    }

    @Override
    public int getPieces() {
        return pieces;
    }

    public void setPieces(int pieces) {
        this.pieces = pieces;
    }
}
