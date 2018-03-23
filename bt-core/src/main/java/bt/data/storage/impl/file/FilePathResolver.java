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

package bt.data.storage.impl.file;

import bt.data.storage.impl.PathResolver;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.file.Files.isDirectory;

/**
 * @author Oleg Ermolaev Date: 23.03.2018 6:37
 */
public class FilePathResolver implements PathResolver {
    private final Path rootDirectory;
    private final PathNormalizer pathNormalizer;

    public FilePathResolver(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.pathNormalizer = new PathNormalizer(rootDirectory.getFileSystem());
    }

    @Override
    public Map<Integer, Path> resolve(Torrent torrent) {
        final TorrentId torrentId = torrent.getTorrentId();
        final String name = torrent.getFiles().size() == 1 ? null : torrent.getName();
        final List<TorrentFile> files = torrent.getFiles();
        final Map<Integer, Path> map = new HashMap<>();
        final Set<Path> set = new HashSet<>();
        for (int index = 0; index < files.size(); index++) {
            final TorrentFile torrentFile = files.get(index);
            final List<String> pathElements = torrentFile.getPathElements();
            final Path resolved = resolve(torrentId, name, index, pathElements);
            Path file = resolved;
            int attempt = 1;
            while (set.contains(file) || isDirectory(file)) {   //todo oe: check capacity mismatch
                file = changeName(resolved, ++attempt);
            }
            set.add(file);
            map.put(index, file);
        }
        return map;
    }

    private Path changeName(Path resolved, int attempt) {
        final Path parent = resolved.getParent();
        final String fileName = resolved.getFileName().toString();
        final int index = fileName.lastIndexOf('.');
        final String newFileName = String.format(
                "%s(%d)%s",
                index > 0 ? fileName.substring(0, index) : "",
                attempt,
                fileName.substring(Math.max(0, index))
        );
        return parent.resolve(newFileName);
    }

    @Override
    public Path resolve(TorrentId torrentId, String name, int fileIndex, List<String> path) {
        final Path torrentDirectory;
        if (name == null) {
            torrentDirectory = rootDirectory;
        } else {
            final String normalizedName = pathNormalizer.normalize(name);
            torrentDirectory = rootDirectory.resolve(normalizedName);
        }
        final String normalizedPath = pathNormalizer.normalize(path);
        return torrentDirectory.resolve(normalizedPath);
    }
}
