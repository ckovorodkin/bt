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

import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.statistic.TransferAmount;
import bt.statistic.TransferAmountStatistic;
import bt.torrent.data.DataWorker;
import bt.torrent.messaging.ConnectionState;
import bt.torrent.messaging.PeerInfoView;
import bt.torrent.messaging.PeerManager;
import bt.torrent.messaging.TorrentWorker;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class DefaultTorrentSessionState implements TorrentSessionState {

    private final TorrentId torrentId;
    private final TorrentDescriptor descriptor;
    private final TorrentWorker worker;
    private final PeerManager peerManager;
    private final DataWorker dataWorker;
    private final TransferAmountStatistic transferAmountStatistic;

    private final BitSet emptyPieces;


    public DefaultTorrentSessionState(TorrentId torrentId,
                                      TorrentDescriptor descriptor,
                                      TorrentWorker worker,
                                      PeerManager peerManager,
                                      DataWorker dataWorker,
                                      TransferAmountStatistic transferAmountStatistic) {
        this.torrentId = torrentId;
        this.descriptor = descriptor;
        this.worker = worker;
        this.peerManager = peerManager;
        this.dataWorker = dataWorker;
        this.transferAmountStatistic = transferAmountStatistic;
        this.emptyPieces = new BitSet();
    }

    @Override
    public int getPiecesTotal() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesTotal();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesComplete() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesComplete();
        } else {
            return 0;
        }
    }

    @Override
    public int getPiecesIncomplete() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesIncomplete();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesRemaining() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesRemaining();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesSkipped() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesSkipped();
        } else {
            return 0;
        }
    }

    @Override
    public int getPiecesNotSkipped() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesNotSkipped();
        } else {
            return 1;
        }
    }

    @Override
    public BitSet getPieces() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPieces();
        }
        emptyPieces.clear();
        return emptyPieces;
    }

    @Override
    public double getRatio() {
        return worker.getRatio();
    }

    @Override
    public long getDownloaded() {
        final TransferAmount transferAmount = transferAmountStatistic.getTransferAmount(torrentId);
        return transferAmount.getDownload();
    }

    @Override
    public long getUploaded() {
        final TransferAmount transferAmount = transferAmountStatistic.getTransferAmount(torrentId);
        return transferAmount.getUpload();
    }

    @Override
    public TransferAmount getTransferAmount(Peer peer) {
        return transferAmountStatistic.getTransferAmount(torrentId, peer);
    }

    @Override
    public Set<Peer> getConnectedPeers() {
        return Collections.unmodifiableSet(worker.getPeers());
    }

    @Override
    public boolean isDataWorkerOverload() {
        return dataWorker.isOverload();
    }

    @Override
    public Set<Peer> getActivePeers() {
        return Collections.unmodifiableSet(worker.getActivePeers());
    }

    @Override
    public Set<Peer> getTimeoutedPeers() {
        return Collections.unmodifiableSet(worker.getTimeoutedPeers());
    }

    @Override
    public ConnectionState getConnectionState(Peer peer) {
        return worker.getConnectionState(peer);
    }

    @Override
    public Collection<PeerInfoView> getPeerInfos() {
        return peerManager.getPeerInfos();
    }

    @Override
    public Collection<PeerInfoView> getOnlinePeerInfos() {
        return peerManager.getOnlinePeerInfos();
    }

    @Override
    public Collection<PeerInfoView> getConnectedPeerInfos() {
        return peerManager.getConnectedPeerInfos();
    }
}
