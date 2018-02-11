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
import bt.net.Peer;
import bt.runtime.Config;
import bt.torrent.PiecesStatistics;
import bt.torrent.order.PieceOrder;
import bt.torrent.order.RandomPieceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Assignments {

    private static final Logger LOGGER = LoggerFactory.getLogger(Assignments.class);

    private Config config;

    private Bitfield localBitfield;
    private PieceOrder pieceOrder;
    private PiecesStatistics pieceStatistics;

    private BitSet assignedPieces;
    private Map<Peer, Assignment> assignments;
    private Set<Peer> peers;

    private final PieceOrder endgamePieceOrder;

    public Assignments(
            Bitfield localBitfield, PieceOrder pieceOrder, PiecesStatistics pieceStatistics, Config config) {
        this.localBitfield = localBitfield;
        this.pieceOrder = pieceOrder;
        this.pieceStatistics = pieceStatistics;
        this.config = config;

        this.assignedPieces = new BitSet();
        this.assignments = new HashMap<>();
        this.peers = new HashSet<>();

        this.endgamePieceOrder = new RandomPieceOrder();
    }

    public double getRatio() {
        return pieceStatistics.getRatio();
    }

    public Assignment get(Peer peer) {
        return assignments.get(peer);
    }

    public void remove(Assignment assignment) {
        assignment.abort();
        assignments.remove(assignment.getPeer());
        assignedPieces.clear(assignment.getPiece());
    }

    public int getPiecesRemaining() {
        return localBitfield.getPiecesRemaining();
    }

    public int count() {
        return assignments.size();
    }

    public int workersCount() {
        return peers.size();
    }

    public Optional<Assignment> assign(Peer peer) {
        final BitSet mask = localBitfield.getPieces();
        mask.flip(0, pieceStatistics.getPiecesTotal());

        final PieceOrder pieceOrder;
        final boolean endgame = isEndgame();
        if (endgame) {
            // take random piece to minimize number of pieces
            // requested from different peers at the same time
            pieceOrder = this.endgamePieceOrder;
        } else {
            mask.andNot(assignedPieces);
            pieceOrder = this.pieceOrder;
        }

        final int next = pieceStatistics.next(pieceOrder, mask, peer);

        final Optional<Integer> selectedPiece = next == -1 ? Optional.empty() : Optional.of(next);

        if (LOGGER.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            buf.append("Trying to claim next assignment for peer ");
            buf.append(peer);
            buf.append(". Number of remaining pieces: ");
            buf.append(localBitfield.getPiecesRemaining());
            buf.append(", number of pieces in progress: ");
            buf.append(assignedPieces.cardinality());
            buf.append(", endgame: ").append(endgame);
            buf.append(". ");
            if (selectedPiece.isPresent()) {
                buf.append(" => Assigning piece #");
                buf.append(selectedPiece.get());
                buf.append(" to current peer");
            } else {
                buf.append(" => No pieces to assign.");
            }
            LOGGER.debug(buf.toString());
        }

        return selectedPiece.isPresent() ? Optional.of(assign(peer, selectedPiece.get())) : Optional.empty();
    }

    private boolean isEndgame() {
        // if all remaining pieces are requested,
        // that would mean that we have entered the "endgame" mode
        return localBitfield.getPiecesRemaining() <= assignedPieces.cardinality();
    }

    private Assignment assign(Peer peer, Integer piece) {
        Assignment assignment = new Assignment(peer, piece, config.getMaxPieceReceivingTime());
        assignments.put(peer, assignment);
        assignedPieces.set(piece);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Assigning piece #{} to peer: {}", piece, peer);
        }
        return assignment;
    }

    /**
     * @return Collection of peers that have interesting pieces and can be given an assignment
     */
    // TODO: select from seeders first
    public Set<Peer> getInteresting(Set<Peer> ready, Set<Peer> choking) {
        final Set<Peer> result = new HashSet<>();
        final BitSet mask = localBitfield.getPieces();
        mask.flip(0, pieceStatistics.getPiecesTotal());
        if (!isEndgame()) {
            mask.andNot(assignedPieces);
        }
        for (Peer peer : ready) {
            final int next = pieceStatistics.next(pieceOrder, mask, peer);
            if (next != -1) {
                result.add(peer);
            }
        }
        peers.clear();
        peers.addAll(result);
        //noinspection Convert2streamapi
        for (Peer peer : choking) {
            if (hasInterestingPieces(peer, mask)) {
                result.add(peer);
            }
        }
        return result;
    }

    private boolean hasInterestingPieces(Peer peer, BitSet mask) {
        final Optional<BitSet> piecesOptional = pieceStatistics.getPieces(peer);
        if (!piecesOptional.isPresent()) {
            return false;
        }
        final BitSet pieces = piecesOptional.get();
        pieces.and(mask);
        return pieces.cardinality() > 0;
    }
}
