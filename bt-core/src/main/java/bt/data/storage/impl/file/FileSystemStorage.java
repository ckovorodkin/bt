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

package bt.data.storage.impl.file;

import bt.data.DefaultTorrentFileInfo;
import bt.data.Storage;
import bt.data.StorageUnit;
import bt.data.TorrentFileInfo;
import bt.data.storage.impl.IdentityTargetResolver;
import bt.data.storage.impl.PathResolver;
import bt.data.storage.impl.TargetResolver;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Provides file-system based storage for torrent files.
 *
 * @see bt.data.storage.impl.file.PathNormalizer
 * @see bt.data.storage.impl.file.FilePathResolver#resolve(Torrent)
 * @since 1.0
 */
public class FileSystemStorage implements Storage {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStorage.class);

    private final PathResolver pathResolver;
    private final TargetResolver targetResolver;

    private final Object modificationLock;
    private final Map<Path, StorageUnit> storageUnitsMap;
    private final Map<TorrentId, Set<StorageUnit>> torrentIdStorageUnitsMap;
    private final Map<StorageUnit, Set<AccessorKey>> storageUnitAccessorKeysMap;

    public FileSystemStorage(Path rootDirectory) {
        this(rootDirectory, new IdentityTargetResolver(rootDirectory));
    }

    public FileSystemStorage(Path rootDirectory, TargetResolver targetResolver) {
        this.pathResolver = new FilePathResolver(rootDirectory.getFileSystem());
        this.targetResolver = targetResolver;

        this.modificationLock = new Object();
        this.storageUnitsMap = new ConcurrentHashMap<>();
        this.storageUnitAccessorKeysMap = new ConcurrentHashMap<>();
        this.torrentIdStorageUnitsMap = new ConcurrentHashMap<>();
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TorrentFileInfo> register(Torrent torrent) {
        final Map<Integer, Path> resolvedPathMap = pathResolver.resolve(torrent);
        final Map<Integer, Path> targetPathMap = targetResolver.resolve(torrent, resolvedPathMap);

        final TorrentId torrentId = torrent.getTorrentId();
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
            final Path file = requireNonNull(targetPathMap.get(index));
            final StorageUnit storageUnit = registerStorageUnit(file, torrentId, index, torrentFile);
            torrentFileInfo.setStorageUnit(storageUnit);
            torrentFileInfos.add(torrentFileInfo);
        }
        return torrentFileInfos;
    }

    private StorageUnit registerStorageUnit(Path file, TorrentId torrentId, int fileIndex, TorrentFile torrentFile) {
        synchronized (modificationLock) {
            final long capacity = torrentFile.getSize();
            final StorageUnit storageUnit =
                    storageUnitsMap.computeIfAbsent(file, file0 -> new FileSystemStorageUnit(file0, capacity));

            //noinspection StatementWithEmptyBody
            if (storageUnit.capacity() != capacity) {
                //todo oe: change path
                throw new IllegalStateException();
            } else {
                //todo oe: check file digest
            }

            torrentIdStorageUnitsMap
                    .computeIfAbsent(torrentId, torrentId0 -> ConcurrentHashMap.newKeySet())
                    .add(storageUnit);

            storageUnitAccessorKeysMap
                    .computeIfAbsent(storageUnit, storageUnit0 -> ConcurrentHashMap.newKeySet())
                    .add(new AccessorKey(torrentId, fileIndex));

            return storageUnit;
        }
    }

    @Override
    public void unregister(Torrent torrent) {
        final int[] count = {0};
        synchronized (modificationLock) {
            torrentIdStorageUnitsMap.computeIfPresent(torrent.getTorrentId(), (torrentId, storageUnits) -> {
                for (Iterator<StorageUnit> storageUnitIterator = storageUnits.iterator();
                     storageUnitIterator.hasNext(); ) {
                    final StorageUnit currentStorageUnit = storageUnitIterator.next();
                    storageUnitAccessorKeysMap.computeIfPresent(currentStorageUnit, (storageUnit, accessorKeys) -> {
                        for (Iterator<AccessorKey> accessorKeyIterator = accessorKeys.iterator();
                             accessorKeyIterator.hasNext(); ) {
                            final AccessorKey accessorKey = accessorKeyIterator.next();
                            if (accessorKey.getTorrentId().equals(torrentId)) {
                                accessorKeyIterator.remove();
                            }
                        }
                        if (accessorKeys.isEmpty()) {
                            count[0]++;
                            storageUnitsMap.remove(storageUnit.getPath());
                            try {
                                storageUnit.close();
                            } catch (Exception | AssertionError e) {
                                LOGGER.error("Failed to close storage unit: " + storageUnit, e);
                            }
                        }
                        return accessorKeys.isEmpty() ? null : accessorKeys;
                    });
                    storageUnitIterator.remove();
                }
                assert storageUnits.isEmpty();
                return null;
            });
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Closed {} storage unit{}", count[0], count[0] > 1 ? "s" : "");
        }
    }

    private static final class AccessorKey {
        private final TorrentId torrentId;
        private final int fileIndex;

        public AccessorKey(TorrentId torrentId, int fileIndex) {
            this.torrentId = requireNonNull(torrentId);
            this.fileIndex = fileIndex;
        }

        public TorrentId getTorrentId() {
            return torrentId;
        }

        public int getFileIndex() {
            return fileIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AccessorKey that = (AccessorKey) o;
            return fileIndex == that.fileIndex && Objects.equals(torrentId, that.torrentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(torrentId, fileIndex);
        }

        @Override
        public String toString() {
            return "AccessorKey{" + "torrentId=" + torrentId + ", fileIndex=" + fileIndex + '}';
        }
    }
}
