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

import bt.event.EventSink;
import bt.event.EventSource;
import bt.event.PeerSourceType;
import bt.logging.MDCWrapper;
import bt.metainfo.TorrentId;
import bt.net.IConnectionSource;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.statistic.TransferAmountStatistic;
import bt.torrent.PiecesStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static bt.event.PeerSourceType.INCOMING;
import static bt.torrent.messaging.PeerState.ACTIVE;
import static bt.torrent.messaging.PeerState.CONNECTING;
import static bt.torrent.messaging.PeerState.DISCONNECTED;

/**
 * @author Oleg Ermolaev Date: 24.02.2018 11:06
 */
public class PeerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerManager.class);

    private final TorrentId torrentId;

    private final IConnectionSource connectionSource;
    private final IPeerConnectionPool peerConnectionPool;
    private final TransferAmountStatistic transferAmountStatistic;
    private final PiecesStatistics piecesStatistics;
    private final TorrentWorker torrentWorker;
    private final EventSink eventSink;
    private final Config config;

    private final ConcurrentMap<Peer, PeerInfo> peerInfoMap;
    private final ConcurrentMap<Peer, PeerInfo> onlinePeerInfoMap;
    private final ConcurrentMap<Peer, PeerInfo> connectedPeerInfoMap;

    public PeerManager(TorrentId torrentId,
                       IConnectionSource connectionSource,
                       IPeerConnectionPool peerConnectionPool,
                       TransferAmountStatistic transferAmountStatistic,
                       PiecesStatistics piecesStatistics,
                       TorrentWorker torrentWorker,
                       EventSource eventSource,
                       EventSink eventSink,
                       IRuntimeLifecycleBinder lifecycleBinder,
                       Config config) {
        this.torrentId = torrentId;
        this.connectionSource = connectionSource;
        this.peerConnectionPool = peerConnectionPool;
        this.transferAmountStatistic = transferAmountStatistic;
        this.piecesStatistics = piecesStatistics;
        this.torrentWorker = torrentWorker;
        this.eventSink = eventSink;
        this.config = config;

        this.peerInfoMap = new ConcurrentHashMap<>();
        this.onlinePeerInfoMap = new ConcurrentHashMap<>();
        this.connectedPeerInfoMap = new ConcurrentHashMap<>();

        eventSource.onPeerDiscovered(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDiscovered(e.getTimestamp(), e.getPeer(), e.getPeerSourceType());
            }
        });
        eventSource.onPeerUnreachable(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerUnreachable(e.getTimestamp(), e.getPeer());
            }
        });
        eventSource.onPeerConnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerConnected(e.getTimestamp(), e.getPeer(), e.isIncoming(), e.getConnectionId());
            }
        });
        eventSource.onPeerDisconnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDisconnected(e.getTimestamp(), e.getPeer(), e.getConnectionId());
            }
        });

        final String name = "peer-manager-" + torrentId.toString().substring(0, 10);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, r -> new Thread(r, name));
        executor.scheduleAtFixedRate(this::reconnect, 1, 1, TimeUnit.SECONDS);
        lifecycleBinder.onShutdown("Shutdown peer manager", executor::shutdownNow);
    }

    public Collection<PeerInfoView> getPeerInfos() {
        return Collections.unmodifiableCollection(peerInfoMap.values());
    }

    public Collection<PeerInfoView> getOnlinePeerInfos() {
        return Collections.unmodifiableCollection(onlinePeerInfoMap.values());
    }

    public Collection<PeerInfoView> getConnectedPeerInfos() {
        return Collections.unmodifiableCollection(connectedPeerInfoMap.values());
    }

    private synchronized void reconnect() {
        try {
            final long timestamp = System.currentTimeMillis();
            final AtomicInteger reconnected = new AtomicInteger();
            peerInfoMap
                    .values()
                    .stream()
                    .filter(peerInfo -> peerInfo.getPeerState() == DISCONNECTED)
                    .filter(peerInfo -> isConnectNow(timestamp, peerInfo))
                    .filter(peerInfo -> peerConnectionPool.mightAddOutgoingConnection(torrentId,
                            peerInfo.getPeer().getInetSocketAddress()
                    ))
                    .filter(peerInfo -> peerInfo.setPeerState(timestamp, DISCONNECTED, CONNECTING))
                    .forEach(peerInfo -> new MDCWrapper().putRemoteAddress(peerInfo.getPeer()).run(() -> {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Reconnect peer: {}", peerInfo.getPeer());
                        }
                        reconnected.incrementAndGet();
                        connectionSource.getConnectionAsync(peerInfo.getPeer(), torrentId);
                    }));
            if (reconnected.get() > 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Reconnected peer count: {}", reconnected.get());
            }
        } catch (Exception | AssertionError e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Reconnection failed", e);
            }
        }
    }

    private boolean isConnectNow(long timestamp, PeerInfo peerInfo) {
        if (peerInfo.getConnectAttempts() == 0) {
            assert peerInfo.getLastConnectAttemptAt() == null;
            return true;
        }
        if (peerInfo.getConnectAt() == null) {
            final int failedConnectAttempts = peerInfo.getFailedConnectAttempts();
            final long interval = config.getPeerConnectionRetryInterval().getInterval(failedConnectAttempts);
            peerInfo.setConnectAt(timestamp + interval);
        }
        return peerInfo.getConnectAt() <= timestamp;
    }

    private synchronized void onPeerDiscovered(long timestamp, Peer peer, PeerSourceType peerSourceType) {
        final PeerInfo peerInfo = getPeerInfo(peer, peerSourceType);
        if (peerInfo.getConnectAttempts() == 0) {
            if (peerConnectionPool.mightAddOutgoingConnection(torrentId, peer.getInetSocketAddress())) {
                final boolean updated = peerInfo.setPeerState(timestamp, DISCONNECTED, CONNECTING);
                assert updated;
                connectionSource.getConnectionAsync(peer, torrentId);
            }
        }
    }

    private synchronized void onPeerUnreachable(long timestamp, Peer peer) {
        assert peerInfoMap.containsKey(peer);
        final PeerInfo peerInfo = peerInfoMap.get(peer);
        peerInfo.setPeerState(timestamp, CONNECTING, DISCONNECTED);
    }

    private synchronized void onPeerConnected(long timestamp, Peer peer, boolean incoming, long connectionId) {
        final PeerInfo peerInfo;
        if (incoming) {
            peerInfo = getPeerInfo(peer, INCOMING);
        } else {
            assert peerInfoMap.containsKey(peer);
            peerInfo = peerInfoMap.get(peer);
        }

        onlinePeerInfoMap.putIfAbsent(peer, peerInfo);

        if (!mightAddConnection()) {
            peerConnectionPool.disconnect(peer, torrentId);
            return;
        }

        if (peerInfo.getPeerState() == ACTIVE) {
            final long currentConnectionId = peerInfo.getConnectionId();
            assert currentConnectionId != connectionId;
            peerConnectionPool.disconnect(peer, torrentId);             // may cause asynchronous onPeerDisconnected()
            onPeerDisconnected(timestamp, peer, currentConnectionId);   // force synchronous onPeerDisconnected()
        }

        peerInfo.connect(timestamp, connectionId);

        connectedPeerInfoMap.putIfAbsent(peer, peerInfo);

        torrentWorker.addPeer(peer);
    }

    private synchronized void onPeerDisconnected(long timestamp, Peer peer, long connectionId) {
        assert peerInfoMap.containsKey(peer);
        final PeerInfo peerInfo = peerInfoMap.get(peer);
        if (peerInfo.disconnect(timestamp, connectionId)) {
            connectedPeerInfoMap.remove(peer);
            torrentWorker.removePeer(peer);
            piecesStatistics.removePieces(peer);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Ignore event with expired connection Id for peer {}. Expected: {{}}, actual: {{}}",
                        peer,
                        connectionId,
                        peerInfo.getConnectionId()
                );
            }
        }
    }

    private boolean mightAddConnection() {
        return connectedPeerInfoMap.size() < config.getMaxPeerConnectionsPerTorrent();
    }

    public void addPieces(Peer peer, BitSet pieces) {
        piecesStatistics.addPieces(peer, pieces);
        getPeerInfo(peer).setPieces(pieces.cardinality());
        eventSink.firePeerBitfieldUpdated(torrentId, peer, pieces, getPiecesTotal());
    }

    public void addPiece(Peer peer, int pieceIndex) {
        piecesStatistics.addPiece(peer, pieceIndex);
        //todo: track in global scope
        getPieces(peer).ifPresent(pieces -> {
            getPeerInfo(peer).setPieces(pieces.cardinality());
            final int piecesTotal = getPiecesTotal();
            eventSink.firePeerBitfieldUpdated(torrentId, peer, pieces, piecesTotal);
        });
    }

    public Optional<BitSet> getPieces(Peer peer) {
        return piecesStatistics.getPieces(peer);
    }

    public int getPiecesTotal() {
        return piecesStatistics.getPiecesTotal();
    }

    private PeerInfo getPeerInfo(Peer peer) {
        return getPeerInfo0(peer, INCOMING);
    }

    private PeerInfo getPeerInfo(Peer peer, PeerSourceType peerSourceType) {
        final PeerInfo peerInfo = getPeerInfo0(peer, peerSourceType);
        peerInfo.addPeerSourceType(peerSourceType);
        return peerInfo;
    }

    private PeerInfo getPeerInfo0(Peer peer, PeerSourceType peerSourceType) {
        //todo: limit discovered peer count
        return peerInfoMap.computeIfAbsent(peer, it -> new PeerInfo(peer,
                torrentId,
                getPiecesTotal(),
                peerSourceType,
                transferAmountStatistic.getTransferAmount(torrentId, peer)
        ));
    }
}
