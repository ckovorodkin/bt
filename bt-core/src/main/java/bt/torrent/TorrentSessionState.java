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

package bt.torrent;

import bt.net.Peer;
import bt.statistic.TransferAmount;
import bt.torrent.messaging.ConnectionState;
import bt.torrent.messaging.PeerInfoView;

import java.util.BitSet;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Provides information about a particular torrent session.
 *
 * @since 1.0
 */
public interface TorrentSessionState {

    /**
     * @since 0.0
     */
    boolean isComplete();

    /**
     * @return Total number of pieces in the torrent
     * @since 1.0
     */
    int getPiecesTotal();

    /**
     * @return Number of pieces that the local client already has
     * @since 1.7
     */
    int getPiecesComplete();

    /**
     * @return Number of pieces that the local client does not have yet
     * @since 1.7
     */
    int getPiecesIncomplete();

    /**
     * @return Number of pieces, that the local client will download
     * @since 1.0
     */
    int getPiecesRemaining();

    /**
     * @return Number of pieces that will be skipped
     * @since 1.7
     */
    int getPiecesSkipped();

    /**
     * @return Number of pieces that will NOT be skipped
     * @since 1.7
     */
    int getPiecesNotSkipped();

    /**
     * @return BitSet of pieces, that status is {@link bt.data.Bitfield.PieceStatus#COMPLETE_VERIFIED}
     * @see bt.data.Bitfield.PieceStatus
     * @since 0.0
     */
    BitSet getPieces();

    /**
     * @since 0.0
     */
    double getRatio();

    /**
     * @since 0.0
     */
    long getSelectDownload();

    /**
     * @since 0.0
     */
    long getLeftDownload();

    /**
     * @since 0.0
     */
    long getLeftVerify();

    /**
     * @return Amount of data downloaded via this session (in bytes)
     * @since 1.0
     */
    long getDownloaded();

    /**
     * @return Amount of data uploaded via this session (in bytes)
     * @since 1.0
     */
    long getUploaded();

    /**
     * @since 0.0
     */
    TransferAmount getTransferAmount(Peer peer);

    /**
     * @since 0.0
     */
    boolean isDataWorkerOverload();

    /**
     * @since 0.0
     */
    Optional<Boolean> isEncryptedConnection(Peer peer);

    /**
     * @return Collection of peers, that this session is connected to
     * @since 1.0
     */
    Set<Peer> getConnectedPeers();

    /**
     * @since 0.0
     */
    Set<Peer> getActivePeers();

    /**
     * @since 0.0
     */
    Set<Peer> getTimeoutedPeers();

    /**
     * @since 0.0
     */
    ConnectionState getConnectionState(Peer peer);

    /**
     * @since 0.0
     */
    Collection<PeerInfoView> getPeerInfos();

    /**
     * @since 0.0
     */
    Collection<PeerInfoView> getOnlinePeerInfos();

    /**
     * @since 0.0
     */
    Collection<PeerInfoView> getConnectedPeerInfos();
}
