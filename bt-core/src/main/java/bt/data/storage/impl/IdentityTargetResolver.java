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

package bt.data.storage.impl;

import bt.metainfo.Torrent;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Oleg Ermolaev Date: 23.03.2018 22:35
 */
public class IdentityTargetResolver implements TargetResolver {
    private final Path rootDirectory;

    public IdentityTargetResolver(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public Map<Integer, Path> resolve(Torrent torrent, Map<Integer, Path> pathMap) {
        final Map<Integer, Path> map = new HashMap<>((int) (pathMap.size() / 0.75d));
        pathMap.forEach((fileIndex, path) -> map.put(fileIndex, resolve(torrent, fileIndex, path)));
        return map;
    }

    @Override
    public Path resolve(Torrent torrent, int fileIndex, Path path) {
        return rootDirectory.resolve(path);
    }
}
