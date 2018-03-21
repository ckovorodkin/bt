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

/**
 * @since 1.7
 */
public class SelectionResult {
    public static final int SKIP_PRIORITY = 0;
    public static final int DEFAULT_PRIORITY = 2;

    private TorrentFileInfo torrentFileInfo;
    private int priority;
    private boolean rarest;
    private boolean random;

    // later we may add more options:
    // - nofify-on-completed
    // etc.

    public SelectionResult(TorrentFileInfo torrentFileInfo, int priority, boolean rarest, boolean random) {
        this.torrentFileInfo = torrentFileInfo;
        this.priority = priority;
        this.random = random;
        this.rarest = rarest;
    }

    /**
     * @since 0.0
     */
    public TorrentFileInfo getTorrentFileInfo() {
        return torrentFileInfo;
    }

    /**
     * @since 0.0
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @since 0.0
     */
    public boolean isRarest() {
        return rarest;
    }

    /**
     * @since 0.0
     */
    public boolean isRandom() {
        return random;
    }
}
