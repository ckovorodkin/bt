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

package bt.processor.torrent;

import bt.data.TorrentFileInfo;
import bt.metainfo.Torrent;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.fileselector.SelectionResult;
import bt.torrent.order.ComplexPieceOrder;
import bt.torrent.order.PieceOrder;
import bt.torrent.order.RandomPieceOrder;
import bt.torrent.order.RandomizedRarestPieceOrder;
import bt.torrent.order.RarestPieceOrder;
import bt.torrent.order.SequentialPieceOrder;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static bt.torrent.fileselector.SelectionResult.SKIP_PRIORITY;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class ChooseFilesStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {
    private final TorrentRegistry torrentRegistry;

    public ChooseFilesStage(ProcessingStage<C> next, TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(C context) {
        Torrent torrent = context.getTorrent().get();
        TorrentDescriptor descriptor = torrentRegistry.getDescriptor(torrent.getTorrentId()).get();

        final List<TorrentFileInfo> files = descriptor.getDataDescriptor().getTorrentFileInfos();
        final Collection<SelectionResult> selectionResults = context.getFileSelector().selectFiles(files);

        final Collection<PieceOrder> pieceOrders = new ArrayList<>();

        selectionResults
                .stream()
                .map(SelectionResult::getPriority)
                .filter(priority -> priority > SKIP_PRIORITY)
                .collect(toSet())
                .stream()
                .sorted((o1, o2) -> o2 - o1)
                .forEach(priority -> selectionResults
                        .stream()
                        .filter(selectionResult -> selectionResult.getPriority() == priority)
                        .collect(groupingBy(PieceOrderTypeKey::create, LinkedHashMap::new, toList()))
                        .forEach((pieceOrderTypeKey, byPriorityAndPieceOrderSelectionResults) -> {
                            final BitSet prefetchComplexMask = byPriorityAndPieceOrderSelectionResults
                                    .stream()
                                    .map(this::getPrefetchPieces)
                                    .collect(BitSet::new, BitSet::or, BitSet::or);
                            if (prefetchComplexMask.cardinality() > 0) {
                                final PieceOrder pieceOrder = createPieceOrder(pieceOrderTypeKey, prefetchComplexMask);
                                pieceOrders.add(pieceOrder);
                            }
                            final BitSet complexMask = byPriorityAndPieceOrderSelectionResults
                                    .stream()
                                    .map(SelectionResult::getTorrentFileInfo)
                                    .map(this::getPieces)
                                    .collect(BitSet::new, BitSet::or, BitSet::or);
                            assert complexMask.cardinality() > 0;
                            final PieceOrder pieceOrder = createPieceOrder(pieceOrderTypeKey, complexMask);
                            pieceOrders.add(pieceOrder);
                        }));

        final PieceOrder pieceOrder = new ComplexPieceOrder(pieceOrders);

        assert getMaskDifferentBitCount(selectionResults, pieceOrder) == 0;

        context.getPieceOrder().setDelegate(pieceOrder);
    }

    private int getMaskDifferentBitCount(Collection<SelectionResult> selectionResults, PieceOrder pieceOrder) {
        final BitSet mask = selectionResults
                .stream()
                .filter(selectionResult -> selectionResult.getPriority() > SKIP_PRIORITY)
                .map(SelectionResult::getTorrentFileInfo)
                .map(this::getPieces)
                .collect(BitSet::new, BitSet::or, BitSet::or);
        mask.xor(pieceOrder.getMask());
        return mask.cardinality();
    }

    private BitSet getPrefetchPieces(SelectionResult selectionResult) {
        final TorrentFileInfo torrentFileInfo = selectionResult.getTorrentFileInfo();
        final long torrentFileSize = torrentFileInfo.getTorrentFile().getSize();

        final long prefetchHeadLength = selectionResult.getPrefetchHeadLength();
        final long prefetchTailLength = selectionResult.getPrefetchTailLength();
        if (prefetchHeadLength + prefetchTailLength >= torrentFileSize) {
            return getPieces(torrentFileInfo);
        }

        final BitSet pieces = new BitSet();

        final long pieceLength = torrentFileInfo.getPieceLength();

        if (prefetchHeadLength > 0) {
            assert prefetchHeadLength < torrentFileSize;
            final long prefetchHeadEndOffset = torrentFileInfo.getOffset() + prefetchHeadLength;
            final int prefetchHeadEndPieceIndex = (int) ((prefetchHeadEndOffset + (pieceLength - 1)) / pieceLength);
            assert prefetchHeadEndOffset <= prefetchHeadEndPieceIndex * pieceLength;
            assert torrentFileInfo.getFirstPieceIndex() < prefetchHeadEndPieceIndex;
            IntStream.range(torrentFileInfo.getFirstPieceIndex(), prefetchHeadEndPieceIndex).forEach(pieces::set);
        }

        if (prefetchTailLength > 0) {
            assert prefetchTailLength < torrentFileSize;
            final long prefetchTailStartOffset = torrentFileInfo.getOffset() + torrentFileSize - prefetchTailLength;
            final int prefetchTailStartPieceIndex = (int) (prefetchTailStartOffset / pieceLength);
            assert torrentFileInfo.getFirstPieceIndex() <= prefetchTailStartPieceIndex;
            assert prefetchTailStartPieceIndex <= torrentFileInfo.getLastPieceIndex();
            IntStream.range(prefetchTailStartPieceIndex, torrentFileInfo.getLastPieceIndex() + 1).forEach(pieces::set);
        }

        return pieces;
    }

    private BitSet getPieces(TorrentFileInfo torrentFileInfo) {
        final BitSet pieces = new BitSet();
        IntStream
                .range(torrentFileInfo.getFirstPieceIndex(), torrentFileInfo.getLastPieceIndex() + 1)
                .forEach(pieces::set);
        return pieces;
    }

    private PieceOrder createPieceOrder(PieceOrderTypeKey typeKey, BitSet mask) {
        if (typeKey.isRarest()) {
            return typeKey.isRandom() ? new RandomizedRarestPieceOrder(mask) : new RarestPieceOrder(mask);
        } else {
            return typeKey.isRandom() ? new RandomPieceOrder(mask) : new SequentialPieceOrder(mask);
        }
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.FILES_CHOSEN;
    }

    private static class PieceOrderTypeKey {
        private final boolean rarest;
        private final boolean random;

        private PieceOrderTypeKey(boolean rarest, boolean random) {
            this.rarest = rarest;
            this.random = random;
        }

        public static PieceOrderTypeKey create(SelectionResult selectionResult) {
            return new PieceOrderTypeKey(selectionResult.isRarest(), selectionResult.isRandom());
        }

        public boolean isRarest() {
            return rarest;
        }

        public boolean isRandom() {
            return random;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PieceOrderTypeKey that = (PieceOrderTypeKey) o;
            return rarest == that.rarest && random == that.random;
        }

        @Override
        public int hashCode() {
            return Objects.hash(rarest, random);
        }
    }
}
