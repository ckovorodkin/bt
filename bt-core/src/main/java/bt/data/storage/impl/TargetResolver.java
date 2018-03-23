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

import bt.metainfo.TorrentId;

import java.nio.file.Path;
import java.util.Map;

/**
 * @author Oleg Ermolaev Date: 23.03.2018 22:32
 */
public interface TargetResolver {
    Map<Integer, Path> resolve(TorrentId torrentId, Map<Integer, Path> pathMap);

    Path resolve(TorrentId torrentId, int fileIndex, Path path);
}
