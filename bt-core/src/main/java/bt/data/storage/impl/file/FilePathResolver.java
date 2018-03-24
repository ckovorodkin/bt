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

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Oleg Ermolaev Date: 23.03.2018 6:37
 */
public class FilePathResolver implements PathResolver {
    private final FileSystem fileSystem;
    private final PathNormalizer pathNormalizer;

    public FilePathResolver(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.pathNormalizer = new PathNormalizer(fileSystem);
    }

    @Override
    public Map<Integer, Path> resolve(Torrent torrent) {
        final LinkedHashMap<Integer, Path> resolvedMap = resolvePaths(torrent);
        return resolveDuplicates(resolvedMap);
    }

    private LinkedHashMap<Integer, Path> resolvePaths(Torrent torrent) {
        final TorrentId torrentId = torrent.getTorrentId();
        final String name = torrent.getFiles().size() == 1 ? null : torrent.getName();
        final List<TorrentFile> files = torrent.getFiles();
        // Using of LinkedHashMap ensures that resolveDuplicates() produces a time-constant result
        final LinkedHashMap<Integer, Path> map = new LinkedHashMap<>((int) (files.size() / 0.75d));
        for (int index = 0; index < files.size(); index++) {
            final TorrentFile torrentFile = files.get(index);
            final List<String> pathElements = torrentFile.getPathElements();
            final Path resolved = resolve(torrentId, name, index, pathElements);
            map.put(index, resolved);
        }
        return map;
    }

    private Map<Integer, Path> resolveDuplicates(LinkedHashMap<Integer, Path> resolvedMap) {
        final Map<Integer, Path> map = new HashMap<>((int) (resolvedMap.size() / 0.75d));
        final Set<Path> usedPaths = new HashSet<>((int) (resolvedMap.size() / 0.75d));
        resolvedMap.values().stream().forEach(resolved -> {
            Path parent = resolved.getParent();
            while (parent != null) {
                usedPaths.add(parent);
                parent = parent.getParent();
            }
        });
        resolvedMap.forEach((index, resolved) -> {
            Path file = resolved;
            int attempt = 1;
            while (usedPaths.contains(file)) {
                file = changeName(resolved, ++attempt);
            }
            usedPaths.add(file);
            map.put(index, file);
        });
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
        final String first = name == null ? "" : pathNormalizer.normalize(name);
        final String normalizedPath = pathNormalizer.normalize(path);
        return fileSystem.getPath(first, normalizedPath);
    }
}
