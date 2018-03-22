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

package bt.torrent.fileselector;

import bt.data.TorrentFileInfo;

import static bt.torrent.fileselector.SelectionResult.DEFAULT_PRIORITY;

/**
 * @author Oleg Ermolaev Date: 18.03.2018 2:27
 */
public class AllTorrentFileSelector extends TorrentFileSelector {
    private final boolean rarest;
    private final boolean random;
    private final long prefetchHeadLength;
    private final long prefetchTailLength;

    public AllTorrentFileSelector() {
        this(true, true, 0, 0);
    }

    public AllTorrentFileSelector(boolean rarest, boolean random, long prefetchHeadLength, long prefetchTailLength) {
        this.rarest = rarest;
        this.random = random;
        this.prefetchHeadLength = prefetchHeadLength;
        this.prefetchTailLength = prefetchTailLength;
    }

    @Override
    protected SelectionResult select(TorrentFileInfo torrentFileInfo) {
        return new SelectionResult(torrentFileInfo,
                DEFAULT_PRIORITY,
                rarest,
                random,
                prefetchHeadLength,
                prefetchTailLength
        );
    }
}
