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

import bt.data.TorrentFileInfo;
import bt.torrent.fileselector.SelectionResult;
import bt.torrent.fileselector.TorrentFileSelector;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static bt.torrent.fileselector.SelectionResult.DEFAULT_PRIORITY;
import static bt.torrent.fileselector.SelectionResult.SKIP_PRIORITY;

public class CliFileSelector extends TorrentFileSelector {
    private static final String PROMPT_MESSAGE_FORMAT = "Download '%s'? (hit <Enter> to confirm or <Esc> to skip)";
    private static final String ILLEGAL_KEYPRESS_WARNING = "*** Invalid key pressed. Please, use only <Enter> or <Esc> ***";

    private final boolean rarest;
    private final boolean random;
    private final long prefetchHeadLength;
    private final long prefetchTailLength;

    private final Optional<Runnable> beforeSelect;
    private final Optional<Runnable> afterSelect;

    private volatile boolean shutdown;

    public CliFileSelector(boolean rarest, boolean random, long prefetchHeadLength, long prefetchTailLength) {
        this(rarest, random, prefetchHeadLength, prefetchTailLength, null, null);
    }

    public CliFileSelector(boolean rarest,
                           boolean random,
                           long prefetchHeadLength,
                           long prefetchTailLength,
                           Runnable beforeSelect,
                           Runnable afterSelect) {
        this.rarest = rarest;
        this.random = random;
        this.prefetchHeadLength = prefetchHeadLength;
        this.prefetchTailLength = prefetchTailLength;
        this.beforeSelect = Optional.of(beforeSelect);
        this.afterSelect = Optional.of(afterSelect);
        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    @Override
    public List<SelectionResult> selectFiles(List<TorrentFileInfo> files) {
        try {
            beforeSelect.ifPresent(Runnable::run);
            return super.selectFiles(files);
        } finally {
            afterSelect.ifPresent(Runnable::run);
        }
    }

    @Override
    protected SelectionResult select(TorrentFileInfo torrentFileInfo) {
        while (!shutdown) {
            System.out.println(getPromptMessage(torrentFileInfo));

            try {
                switch (System.in.read()) {
                    case -1: {
                        throw new IllegalStateException("EOF");
                    }
                    case '\n': { // <Enter>
                        return new SelectionResult(
                                torrentFileInfo,
                                DEFAULT_PRIORITY,
                                rarest,
                                random,
                                prefetchHeadLength,
                                prefetchTailLength
                        );
                    }
                    case 0x1B: { // <Esc>
                        return new SelectionResult(
                                torrentFileInfo,
                                SKIP_PRIORITY,
                                rarest,
                                random,
                                prefetchHeadLength,
                                prefetchTailLength
                        );
                    }
                    default: {
                        System.out.println(ILLEGAL_KEYPRESS_WARNING);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        throw new IllegalStateException("Shutdown");
    }

    private static String getPromptMessage(TorrentFileInfo torrentFileInfo) {
        return String.format(PROMPT_MESSAGE_FORMAT, torrentFileInfo.getStorageUnit().getPath());
    }

    private void shutdown() {
        this.shutdown = true;
    }
}
