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

package bt.data;

import bt.data.storage.impl.file.FileSystemStorage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.walk;

// TODO: replace this with in-memory storage
class TestFileSystemStorage extends ExternalResource implements Storage {

    private static final String ROOT_PATH = "target/rt";

    private final Path rootDirectory;
    private volatile Storage delegate;
    private final Object lock;

    private final Set<Torrent> torrents;

    public TestFileSystemStorage() {
        rootDirectory = new File(ROOT_PATH).getAbsoluteFile().toPath();
        try {
            createDirectories(rootDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directories: " + rootDirectory, e);
        }
        torrents = new HashSet<>();
        lock = new Object();
    }

    private Storage getDelegate() {
        if (delegate == null) {
            synchronized (lock) {
                if (delegate == null) {
                    delegate = new FileSystemStorage(rootDirectory);
                }
            }
        }
        return delegate;
    }

    public File getRoot() {
        return rootDirectory.toFile();
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TorrentFileInfo> register(Torrent torrent) {
        torrents.add(torrent);
        return getDelegate().register(torrent);
    }

    @Override
    public void unregister(Torrent torrent) {
        delegate.unregister(torrent);
        torrents.remove(torrent);
    }

    @Override
    protected void after() {
        try {
            final Storage delegate = getDelegate();
            torrents.forEach(delegate::unregister);
            deleteRecursive(rootDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteRecursive(Path root) throws IOException {
        walk(root).sorted((o1, o2) -> o2.getNameCount() - o1.getNameCount()).forEach(path -> {
            try {
                delete(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
