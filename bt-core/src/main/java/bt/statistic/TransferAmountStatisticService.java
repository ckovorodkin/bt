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

package bt.statistic;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author Oleg Ermolaev Date: 17.02.2018 9:12
 */
public class TransferAmountStatisticService implements TransferAmountStatistic {
    private final TransferAmountHolder root;
    private final ConcurrentHashMap<TorrentId, TransferAmountHolder> torrentStatisticMap;
    private final ConcurrentHashMap<PeerKey, TransferAmountHolder> torrentPeerStatisticMap;

    public TransferAmountStatisticService() {
        this.root = new TransferAmountHolder(null);
        this.torrentStatisticMap = new ConcurrentHashMap<>();
        this.torrentPeerStatisticMap = new ConcurrentHashMap<>();
    }

    @Override
    public TransferAmount getTransferAmount() {
        return root;
    }

    @Override
    public TransferAmount getTransferAmount(TorrentId torrentId) {
        return get(torrentStatisticMap, torrentId, () -> root);
    }

    @Override
    public TransferAmount getTransferAmount(TorrentId torrentId, Peer peer) {
        return getTransferAmountHolder(torrentId, peer);
    }

    @Override
    public TransferAmountHandler getTransferAmountHandler(TorrentId torrentId, Peer peer) {
        return getTransferAmountHolder(torrentId, peer);
    }

    private TransferAmountHolder getTransferAmountHolder(TorrentId torrentId, Peer peer) {
        return get(
                torrentPeerStatisticMap,
                new PeerKey(torrentId, peer),
                () -> get(torrentStatisticMap, torrentId, () -> root)
        );
    }

    private <K> TransferAmountHolder get(ConcurrentHashMap<K, TransferAmountHolder> map,
                                         K key,
                                         Supplier<TransferAmountHolder> parentSupplier) {
        Objects.requireNonNull(key);
        TransferAmountHolder transferAmountHolder = map.get(key);
        if (transferAmountHolder == null) {
            transferAmountHolder = new TransferAmountHolder(parentSupplier.get());
            final TransferAmountHolder existing = map.putIfAbsent(key, transferAmountHolder);
            if (existing != null) {
                transferAmountHolder = existing;
            }
        }
        return transferAmountHolder;
    }
}
