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

import bt.data.Bitfield;
import bt.data.TorrentFileInfo;
import bt.metainfo.Torrent;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.runtime.Config;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.fileselector.SelectionResult;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class ChooseFilesStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {
    private TorrentRegistry torrentRegistry;
    private Config config;

    public ChooseFilesStage(ProcessingStage<C> next,
                            TorrentRegistry torrentRegistry,
                            Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        Torrent torrent = context.getTorrent().get();
        TorrentDescriptor descriptor = torrentRegistry.getDescriptor(torrent.getTorrentId()).get();

        final Predicate<TorrentFileInfo> predicate = context.getFileSelector().map(selector -> {
            List<TorrentFileInfo> files = descriptor.getDataDescriptor().getTorrentFileInfos();
            List<SelectionResult> selectionResults = selector.selectFiles(files);
            if (selectionResults.size() != files.size()) {
                throw new IllegalStateException("Invalid number of selection results");
            }
            //noinspection UnnecessaryLocalVariable
            final Predicate<TorrentFileInfo> selectionResultPredicate =
                    torrentFileInfo -> !selectionResults.get(torrentFileInfo.getIndex()).shouldSkip();
            return selectionResultPredicate;
        }).orElse(torrentFileInfo -> true);

        final Collection<TorrentFileInfo> selectedFiles =
                descriptor.getDataDescriptor().getTorrentFileInfos().stream().filter(predicate).collect(toList());

        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        BitSet selectedPieces = getPieces(selectedFiles);
        BitSet skippedPieces = new BitSet();
        skippedPieces.or(selectedPieces);
        skippedPieces.flip(0, bitfield.getPiecesTotal());
        updateSkippedPieces(bitfield, skippedPieces);
    }

    private void updateSkippedPieces(Bitfield bitfield, BitSet skippedPieces) {
        IntStream.range(0, bitfield.getPiecesTotal()).filter(skippedPieces::get).forEach(bitfield::skip);
    }

    private BitSet getPieces(Collection<TorrentFileInfo> torrentFiles) {
        final BitSet pieces = new BitSet();
        torrentFiles.forEach(torrentFile -> IntStream
                .range(torrentFile.getFirstPieceIndex(), torrentFile.getLastPieceIndex())
                .forEach(pieces::set));
        return pieces;
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.FILES_CHOSEN;
    }
}
