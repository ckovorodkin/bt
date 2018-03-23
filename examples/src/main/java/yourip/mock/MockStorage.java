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

package yourip.mock;

import bt.data.DefaultTorrentFileInfo;
import bt.data.Storage;
import bt.data.StorageUnit;
import bt.data.TorrentFileInfo;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

import java.util.ArrayList;
import java.util.List;

public class MockStorage implements Storage {

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TorrentFileInfo> register(Torrent torrent) {
        final long pieceLength = torrent.getChunkSize();
        final List<TorrentFile> files = torrent.getFiles();
        final List<TorrentFileInfo> torrentFileInfos = new ArrayList<>(files.size() + 1);
        long offset = 0;
        for (int index = 0; index < files.size(); index++) {
            final TorrentFile torrentFile = files.get(index);
            final DefaultTorrentFileInfo torrentFileInfo = new DefaultTorrentFileInfo();
            torrentFileInfo.setIndex(index);
            torrentFileInfo.setPieceLength(pieceLength);
            torrentFileInfo.setOffset(offset);
            torrentFileInfo.setFirstPieceIndex((int) (offset / pieceLength));
            offset += torrentFile.getSize();
            torrentFileInfo.setLastPieceIndex((int) (offset / pieceLength));
            torrentFileInfo.setTorrentFile(torrentFile);
            torrentFileInfo.setStorageUnit(new MockStorageUnit());
            torrentFileInfos.add(torrentFileInfo);
        }
        return torrentFileInfos;
    }

    @Override
    public void unregister(Torrent torrent) {
        // do nothing
    }
}
