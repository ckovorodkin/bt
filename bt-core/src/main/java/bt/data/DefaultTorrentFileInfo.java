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

package bt.data;

import bt.metainfo.TorrentFile;

/**
 * @author Oleg Ermolaev Date: 17.03.2018 21:37
 */
public class DefaultTorrentFileInfo implements TorrentFileInfo {
    private int index;
    private long offset;
    private int firstPieceIndex;
    private int lastPieceIndex;
    private TorrentFile torrentFile;
    private StorageUnit storageUnit;

    @Override
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    @Override
    public int getFirstPieceIndex() {
        return firstPieceIndex;
    }

    public void setFirstPieceIndex(int firstPieceIndex) {
        this.firstPieceIndex = firstPieceIndex;
    }

    @Override
    public int getLastPieceIndex() {
        return lastPieceIndex;
    }

    public void setLastPieceIndex(int lastPieceIndex) {
        this.lastPieceIndex = lastPieceIndex;
    }

    @Override
    public TorrentFile getTorrentFile() {
        return torrentFile;
    }

    public void setTorrentFile(TorrentFile torrentFile) {
        this.torrentFile = torrentFile;
    }

    @Override
    public StorageUnit getStorageUnit() {
        return storageUnit;
    }

    public void setStorageUnit(StorageUnit storageUnit) {
        this.storageUnit = storageUnit;
    }
}
