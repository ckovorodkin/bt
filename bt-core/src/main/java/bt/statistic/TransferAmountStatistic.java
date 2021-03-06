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

package bt.statistic;

import bt.metainfo.TorrentId;
import bt.net.Peer;

/**
 * @author Oleg Ermolaev Date: 17.02.2018 8:43
 */
public interface TransferAmountStatistic {
    TransferAmount getTransferAmount();

    TransferAmount getTransferAmount(TorrentId torrentId);

    TransferAmount getTransferAmount(TorrentId torrentId, Peer peer);

    TransferAmountHandler getTransferAmountHandler(TorrentId torrentId, Peer peer);
}
