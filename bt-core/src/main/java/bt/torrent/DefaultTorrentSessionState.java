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
import bt.torrent.messaging.TorrentWorker;

import java.util.BitSet;
import java.util.Collections;
import java.util.Set;

public class DefaultTorrentSessionState implements TorrentSessionState {

    private final TorrentId torrentId;
    private final TransferAmountStatistic transferAmountStatistic;

    private final BitSet emptyPieces;

    private final TorrentDescriptor descriptor;
    private final TorrentWorker worker;

    public DefaultTorrentSessionState(TorrentId torrentId,
                                      TorrentDescriptor descriptor,
                                      TorrentWorker worker,
                                      TransferAmountStatistic transferAmountStatistic) {
        this.torrentId = torrentId;
        this.transferAmountStatistic = transferAmountStatistic;
        this.emptyPieces = new BitSet();
        this.descriptor = descriptor;
        this.worker = worker;
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
    public int getPiecesRemaining() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesRemaining();
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
    public Set<Peer> getConnectedPeers() {
        return Collections.unmodifiableSet(worker.getPeers());
    }
}
