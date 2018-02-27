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

import bt.data.Bitfield;
import bt.metainfo.TorrentId;
import bt.net.IMessageDispatcher;
import bt.net.Peer;
import bt.protocol.Interested;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.runtime.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Objects.requireNonNull;

/**
 * Manages peer workers.
 *
 * @since 1.0
 */
public class TorrentWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(TorrentWorker.class);

    private static final Duration UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL = Duration.ofSeconds(5);

    private TorrentId torrentId;
    private IMessageDispatcher dispatcher;
    private Config config;

    private IPeerWorkerFactory peerWorkerFactory;
    private ConcurrentMap<Peer, PeerWorker> peerMap;
    private final int MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    private Map<Peer, Long> timeoutedPeers;
    private Queue<Peer> disconnectedPeers;
    private Map<Peer, Message> interestUpdates;
    private long lastUpdatedAssignments;

    private Bitfield bitfield;
    private Assignments assignments;

    private final long dispatcherId;

    public TorrentWorker(TorrentId torrentId,
                         IMessageDispatcher dispatcher,
                         IPeerWorkerFactory peerWorkerFactory,
                         Bitfield bitfield,
                         Assignments assignments,
                         Config config) {
        this.torrentId = requireNonNull(torrentId);
        this.dispatcher = requireNonNull(dispatcher);
        this.config = requireNonNull(config);

        this.peerWorkerFactory = requireNonNull(peerWorkerFactory);
        this.peerMap = new ConcurrentHashMap<>();
        this.MAX_CONCURRENT_ACTIVE_CONNECTIONS = config.getMaxConcurrentlyActivePeerConnectionsPerTorrent();
        this.timeoutedPeers = new ConcurrentHashMap<>();
        this.disconnectedPeers = new LinkedBlockingQueue<>();
        this.interestUpdates = new ConcurrentHashMap<>();

        this.bitfield = requireNonNull(bitfield);
        this.assignments = requireNonNull(assignments);

        this.dispatcherId = dispatcher.nextId();
    }

    public double getRatio() {
        return assignments.getRatio();
    }

    /**
     * Called when a peer joins the torrent processing session.
     *
     * @since 1.0
     */
    void addPeer(Peer peer) {
        final PeerWorker worker = createPeerWorker(peer);
        final PeerWorker existing = peerMap.putIfAbsent(peer, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(torrentId, peer, dispatcherId, message -> consume(peer, message));
            dispatcher.addMessageSupplier(torrentId, peer, dispatcherId, () -> produce(peer));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Added connection for peer: " + peer);
            }
        }
    }

    private void consume(Peer peer, Message message) {
        getWorker(peer).ifPresent(worker -> worker.accept(message));
    }

    private Message produce(Peer peer) {
        Message message = null;

        final Optional<PeerWorker> workerOptional = getWorker(peer);
        if (workerOptional.isPresent()) {
            final PeerWorker worker = workerOptional.get();

            if (bitfield.getPiecesRemaining() > 0 || assignments.count() > 0) {
                inspectAssignment(peer, worker, assignments);
                if (shouldUpdateAssignments(assignments)) {
                    processDisconnectedPeers();
                    processTimeoutedPeers();
                    updateAssignments(assignments);
                }
                Message interestUpdate = interestUpdates.remove(peer);
                message = (interestUpdate == null) ? worker.get() : interestUpdate;
            } else {
                message = worker.get();
            }
        }

        return message;
    }

    private Optional<PeerWorker> getWorker(Peer peer) {
        return Optional.ofNullable(peerMap.get(peer));
    }

    private void inspectAssignment(Peer peer, PeerWorker peerWorker, Assignments assignments) {
        ConnectionState connectionState = peerWorker.getConnectionState();
        Assignment assignment = assignments.get(peer);
        boolean shouldAssign;
        if (assignment != null) {
            final Assignment.Status status = assignment.getStatus();
            switch (status) {
                case ACTIVE: {
                    shouldAssign = false;
                    break;
                }
                case DONE: {
                    // assign next piece
                    assignments.remove(assignment);
                    shouldAssign = true;
                    break;
                }
                case TIMEOUT: {
                    timeoutedPeers.put(peer, System.currentTimeMillis());
                    assignments.remove(assignment);
                    shouldAssign = false;
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Peer assignment removed due to TIMEOUT: {}", assignment);
                    }
                    break;
                }
                default: {
                    throw new IllegalStateException("Unexpected status: " + status.name());
                }
            }
        } else {
            shouldAssign = !timeoutedPeers.containsKey(peer);
        }

        if (connectionState.isPeerChoking()) {
            if (assignment != null) {
                assignments.remove(assignment);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Peer assignment removed due to CHOKING: {}", assignment);
                }
            }
        } else if (shouldAssign) {
            if (mightCreateMoreAssignments(assignments)) {
                Optional<Assignment> newAssignment = assignments.assign(peer);
                if (newAssignment.isPresent()) {
                    newAssignment.get().start(connectionState);
                }
            }
        }
    }

    private boolean shouldUpdateAssignments(Assignments assignments) {
        return (timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL.toMillis()
                    && mightUseMoreAssignees(assignments))
            || timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL.toMillis();
    }

    private boolean mightUseMoreAssignees(Assignments assignments) {
        return assignments.workersCount() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private boolean mightCreateMoreAssignments(Assignments assignments) {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private long timeSinceLastUpdated() {
        return System.currentTimeMillis() - lastUpdatedAssignments;
    }

    private void processDisconnectedPeers() {
        Peer disconnectedPeer;
        while ((disconnectedPeer = disconnectedPeers.poll()) != null) {
            dispatcher.removeMessageConsumer(torrentId, disconnectedPeer, dispatcherId);
            dispatcher.removeMessageSupplier(torrentId, disconnectedPeer, dispatcherId);
            Assignment assignment = assignments.get(disconnectedPeer);
            if (assignment != null) {
                assignments.remove(assignment);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(
                            "Peer assignment removed due to DISCONNECT: peer {}, assignment {}",
                            disconnectedPeer,
                            assignment
                    );
                }
            }
            timeoutedPeers.remove(disconnectedPeer);
        }
    }

    private void processTimeoutedPeers() {
        Iterator<Map.Entry<Peer, Long>> timeoutedPeersIter = timeoutedPeers.entrySet().iterator();
        while (timeoutedPeersIter.hasNext()) {
            Map.Entry<Peer, Long> entry = timeoutedPeersIter.next();
            if (System.currentTimeMillis() - entry.getValue() >= config.getTimeoutedAssignmentPeerBanDuration().toMillis()) {
                timeoutedPeersIter.remove();
            }
        }
    }

    private void updateAssignments(Assignments assignments) {
        interestUpdates.clear();

        Set<Peer> ready = new HashSet<>();
        Set<Peer> choking = new HashSet<>();

        peerMap.forEach((peer, worker) -> {
            boolean timeouted = timeoutedPeers.containsKey(peer);
            boolean disconnected = disconnectedPeers.contains(peer);
            if (!timeouted && !disconnected) {
                if (worker.getConnectionState().isPeerChoking()) {
                    choking.add(peer);
                } else {
                    ready.add(peer);
                }
            }
        });

        Set<Peer> interesting = assignments.getInteresting(ready, choking);

        ready.stream().filter(peer -> !interesting.contains(peer)).forEach(peer -> {
            getWorker(peer).ifPresent(worker -> {
                ConnectionState connectionState = worker.getConnectionState();
                if (connectionState.isInterested()) {
                    interestUpdates.put(peer, NotInterested.instance());
                    connectionState.setInterested(false);
                }
            });
        });

        choking.forEach(peer -> {
            getWorker(peer).ifPresent(worker -> {
                ConnectionState connectionState = worker.getConnectionState();
                if (interesting.contains(peer)) {
                    if (!connectionState.isInterested()) {
                        interestUpdates.put(peer, Interested.instance());
                        connectionState.setInterested(true);
                    }
                } else if (connectionState.isInterested()) {
                    interestUpdates.put(peer, NotInterested.instance());
                    connectionState.setInterested(false);
                }
            });
        });

        lastUpdatedAssignments = System.currentTimeMillis();
    }

    private PieceAnnouncingPeerWorker createPeerWorker(Peer peer) {
        final PeerWorker peerWorker = peerWorkerFactory.createPeerWorker(torrentId, peer);
        return new PieceAnnouncingPeerWorker(peerWorker, () -> peerMap.values());
    }

    /**
     * Called when a peer leaves the torrent processing session.
     *
     * @since 1.0
     */
    public void removePeer(Peer peer) {
        PeerWorker removed = peerMap.remove(peer);
        if (removed != null) {
            disconnectedPeers.add(peer);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Removed connection for peer: " + peer);
            }
        }
    }

    /**
     * Get all peers, that this torrent worker is currently working with.
     *
     * @since 1.0
     */
    public Set<Peer> getPeers() {
        return peerMap.keySet();
    }

    public Set<Peer> getActivePeers() {
        final Set<Peer> peers = new HashSet<>(peerMap.keySet());
        peers.removeAll(timeoutedPeers.keySet());
        return peers;
    }

    public Set<Peer> getTimeoutedPeers() {
        return timeoutedPeers.keySet();
    }
    /**
     * Get the current state of a connection with a particular peer.
     *
     * @return Connection state or null, if the peer is not connected to this torrent worker
     * @since 1.0
     */
    public ConnectionState getConnectionState(Peer peer) {
        PeerWorker worker = peerMap.get(peer);
        return (worker == null) ? null : worker.getConnectionState();
    }

}
