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

import bt.data.Bitfield;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.metainfo.TorrentId;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerConnection;
import bt.statistic.TransferAmount;
import bt.statistic.TransferAmountStatistic;
import bt.torrent.data.DataWorker;
import bt.torrent.messaging.ConnectionState;
import bt.torrent.messaging.PeerInfoView;
import bt.torrent.messaging.PeerManager;
import bt.torrent.messaging.TorrentWorker;
import bt.torrent.order.PieceOrder;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultTorrentSessionState implements TorrentSessionState {

    private final TorrentId torrentId;
    private final TorrentDescriptor descriptor;
    private final PieceOrder pieceOrder;
    private final TorrentWorker worker;
    private final PeerManager peerManager;
    private final DataWorker dataWorker;
    private final TransferAmountStatistic transferAmountStatistic;
    private final IPeerConnectionPool peerConnectionPool;

    private final BitSet emptyPieces;


    public DefaultTorrentSessionState(TorrentId torrentId,
                                      TorrentDescriptor descriptor,
                                      PieceOrder pieceOrder,
                                      TorrentWorker worker,
                                      PeerManager peerManager,
                                      DataWorker dataWorker,
                                      TransferAmountStatistic transferAmountStatistic,
                                      IPeerConnectionPool peerConnectionPool) {
        this.torrentId = torrentId;
        this.descriptor = descriptor;
        this.pieceOrder = pieceOrder;
        this.worker = worker;
        this.peerManager = peerManager;
        this.dataWorker = dataWorker;
        this.transferAmountStatistic = transferAmountStatistic;
        this.peerConnectionPool = peerConnectionPool;
        this.emptyPieces = new BitSet();
    }

    @Override
    public boolean isComplete() {
        if (descriptor.getDataDescriptor() == null) {
            return false;
        }
        final Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        return bitfield.getPiecesVerified() == bitfield.getPiecesTotal() && worker.getRemaining().cardinality() == 0;
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
            return descriptor.getDataDescriptor().getBitfield().getPiecesCompleteVerified();
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
            final BitSet remaining = descriptor.getDataDescriptor().getBitfield().getRemaining();
            remaining.and(pieceOrder.getMask());
            return remaining.cardinality();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesSkipped() {
        return getPiecesTotal() - getPiecesNotSkipped();
    }

    @Override
    public int getPiecesNotSkipped() {
        if (descriptor.getDataDescriptor() != null) {
            return pieceOrder.getMask().cardinality();
        } else {
            return 1;
        }
    }

    @Override
    public BitSet getProcessingPieces() {
        if (descriptor.getDataDescriptor() == null) {
            emptyPieces.clear();
            return emptyPieces;
        }
        final Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        final BitSet remaining = bitfield.getRemaining();
        final BitSetAccumulator accumulator = new BitSetAccumulator(bitfield.getPiecesTotal());
        final Set<Peer> activePeers = getActivePeers();
        activePeers.forEach(peer -> Optional
                .ofNullable(getConnectionState(peer))
                .map(ConnectionState::getPiece)
                .ifPresent(pieceIndex -> peerManager.getPieces(peer).ifPresent(peerPieces -> {
                    final BitSet currentMask = pieceOrder.getCurrentMask(pieceIndex).orElse(singleBitSet(pieceIndex));
                    currentMask.and(peerPieces);
                    currentMask.and(remaining);
                    accumulator.add(currentMask);
                })));
        return accumulator.getOrdinal();
    }

    private static BitSet singleBitSet(int pieceIndex) {
        final BitSet bitSet = new BitSet(pieceIndex + 1);
        bitSet.set(pieceIndex);
        return bitSet;
    }

    @Override
    public BitSet getPieces() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getCompleteVerified();
        }
        emptyPieces.clear();
        return emptyPieces;
    }

    @Override
    public double getRatio() {
        return worker.getRatio();
    }

    @Override
    public long getSelectDownload() {
        final DataDescriptor dataDescriptor = descriptor.getDataDescriptor();
        if (dataDescriptor == null) {
            return 0;
        }
        final AtomicLong amount = new AtomicLong();
        final List<ChunkDescriptor> chunkDescriptors = dataDescriptor.getChunkDescriptors();
        final Bitfield bitfield = dataDescriptor.getBitfield();
        final BitSet selected = pieceOrder.getMask();
        for (int pieceIndex = selected.nextSetBit(0); //br
             0 <= pieceIndex && pieceIndex < bitfield.getPiecesTotal();
             pieceIndex = selected.nextSetBit(pieceIndex + 1)) {
            final ChunkDescriptor chunkDescriptor = chunkDescriptors.get(pieceIndex);
            final long chunkSize = chunkDescriptor.getData().length();
            amount.addAndGet(chunkSize);
        }
        return amount.get();
    }

    @Override
    public long getLeftDownload() {
        final DataDescriptor dataDescriptor = descriptor.getDataDescriptor();
        if (dataDescriptor == null) {
            return 0;
        }
        final AtomicLong amount = new AtomicLong();
        final List<ChunkDescriptor> chunkDescriptors = dataDescriptor.getChunkDescriptors();
        final Bitfield bitfield = dataDescriptor.getBitfield();
        //final BitSet remaining = bitfield.getRemaining();
        final BitSet remaining = new BitSet();
        remaining.flip(0, bitfield.getPiecesTotal());
        remaining.and(pieceOrder.getMask());
        remaining.andNot(bitfield.getComplete());
        for (int pieceIndex = remaining.nextSetBit(0); //br
             0 <= pieceIndex && pieceIndex < bitfield.getPiecesTotal();
             pieceIndex = remaining.nextSetBit(pieceIndex + 1)) {
            final ChunkDescriptor chunkDescriptor = chunkDescriptors.get(pieceIndex);
            final long blockSize = chunkDescriptor.blockSize();
            final long chunkSize = chunkDescriptor.getData().length();
            for (int blockIndex = 0; blockIndex < chunkDescriptor.blockCount(); blockIndex++) {
                if (!chunkDescriptor.isPresent(blockIndex)) {
                    final long offset = blockIndex * blockSize;
                    final long length = Math.min(blockSize, chunkSize - offset);
                    amount.addAndGet(length);
                }
            }
        }
        return amount.get();
    }

    @Override
    public long getLeftVerify() {
        final DataDescriptor dataDescriptor = descriptor.getDataDescriptor();
        if (dataDescriptor == null) {
            return 0;
        }
        final AtomicLong amount = new AtomicLong();
        final List<ChunkDescriptor> chunkDescriptors = dataDescriptor.getChunkDescriptors();
        final Bitfield bitfield = dataDescriptor.getBitfield();
        final BitSet selected = bitfield.getVerified();
        selected.flip(0, bitfield.getPiecesTotal());
        for (int pieceIndex = selected.nextSetBit(0); //br
             0 <= pieceIndex && pieceIndex < bitfield.getPiecesTotal();
             pieceIndex = selected.nextSetBit(pieceIndex + 1)) {
            final ChunkDescriptor chunkDescriptor = chunkDescriptors.get(pieceIndex);
            final long chunkSize = chunkDescriptor.getData().length();
            amount.addAndGet(chunkSize);
        }
        return amount.get();
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
    public Optional<Boolean> isEncryptedConnection(Peer peer) {
        final PeerConnection connection = peerConnectionPool.getConnection(peer, torrentId);
        return Optional.ofNullable(connection).map(PeerConnection::isEncrypted);
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
