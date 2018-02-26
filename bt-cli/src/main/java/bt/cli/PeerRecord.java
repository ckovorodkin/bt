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

package bt.cli;

import bt.event.PeerSourceType;
import bt.net.Peer;
import bt.torrent.messaging.ConnectionState;
import bt.torrent.messaging.PeerInfoView;
import bt.torrent.messaging.PeerState;

import java.net.InetAddress;
import java.util.Set;

/**
 * @author Oleg Ermolaev Date: 25.02.2018 19:28
 */
class PeerRecord {
    private final Peer peer;
    private final Set<PeerSourceType> peerSourceTypes;
    private final Long connectAt;
    private final int connectAttempts;
    private final PeerState peerState;
    private final boolean timeouted;
    private final Boolean choking;
    private final Boolean interested;
    private final Boolean peerChoking;
    private final Boolean peerInterested;
    private final int pieces;
    private final int piecesTotal;
    private final long download;
    private final long upload;

    public PeerRecord(PeerInfoView peerInfo, boolean timeouted, ConnectionState connectionState) {
        this.peer = peerInfo.getPeer();
        this.peerSourceTypes = peerInfo.getPeerSourceTypes();
        this.connectAt = peerInfo.getConnectAt();
        this.connectAttempts = peerInfo.getConnectAttempts();
        this.peerState = peerInfo.getPeerState();
        this.timeouted = timeouted;
        this.choking = connectionState == null ? null : connectionState.isChoking();
        this.interested = connectionState == null ? null : connectionState.isInterested();
        this.peerChoking = connectionState == null ? null : connectionState.isPeerChoking();
        this.peerInterested = connectionState == null ? null : connectionState.isPeerInterested();
        this.pieces = peerInfo.getPieces();
        this.piecesTotal = peerInfo.getPiecesTotal();
        this.download = peerInfo.getTransferAmount().getDownload();
        this.upload = peerInfo.getTransferAmount().getUpload();
    }

    public Peer getPeer() {
        return peer;
    }

    public InetAddress getInetAddress() {
        return getPeer().getInetAddress();
    }

    public int getPort() {
        return getPeer().getPort();
    }

    public Set<PeerSourceType> getPeerSourceTypes() {
        return peerSourceTypes;
    }

    public Long getConnectAt() {
        return connectAt;
    }

    public int getConnectAttempts() {
        return connectAttempts;
    }

    public PeerState getPeerState() {
        return peerState;
    }

    public boolean isTimeouted() {
        return timeouted;
    }

    public Boolean isChoking() {
        return choking;
    }

    public Boolean isInterested() {
        return interested;
    }

    public Boolean isPeerChoking() {
        return peerChoking;
    }

    public Boolean isPeerInterested() {
        return peerInterested;
    }

    public int getPieces() {
        return pieces;
    }

    public int getPiecesTotal() {
        return piecesTotal;
    }

    public long getDownload() {
        return download;
    }

    public long getUpload() {
        return upload;
    }
}
