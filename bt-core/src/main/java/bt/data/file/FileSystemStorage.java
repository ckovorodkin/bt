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

package bt.data.file;

import bt.data.StorageUnit;
import bt.data.TorrentFileInfo;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Provides file-system based storage for torrent files.
 *
 * @see bt.data.storage.impl.file.FileSystemStorage
 * @see bt.data.storage.impl.file.PathNormalizer
 * @see bt.data.storage.impl.file.FilePathResolver#resolve(Torrent)
 *
 * @since 1.0
 */
@Deprecated
public class FileSystemStorage extends bt.data.storage.impl.file.FileSystemStorage {
    /**
     * Create a file-system based storage inside a given directory.
     *
     * @param rootDirectory Root directory for this storage. All torrent files will be stored inside this directory.
     * @since 1.0
     * @deprecated since 1.3 in favor of more generic {@link #FileSystemStorage(Path)}
     */
    @Deprecated
    public FileSystemStorage(File rootDirectory) {
        this(rootDirectory.toPath());
    }

    public FileSystemStorage(Path rootDirectory) {
        super(rootDirectory);
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {
        final List<TorrentFileInfo> torrentFileInfos = register(torrent);
        for (TorrentFileInfo torrentFileInfo : torrentFileInfos) {
            if (torrentFileInfo.getTorrentFile().equals(torrentFile)) {
                return torrentFileInfo.getStorageUnit();
            }
        }
        throw new IllegalStateException();
    }
}
