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

package bt.torrent.messaging;

import bt.event.PeerSourceType;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.statistic.TransferAmount;

import java.util.Set;

/**
 * @author Oleg Ermolaev Date: 26.02.2018 19:03
 */
public interface PeerInfoView {
    Peer getPeer();

    TorrentId getTorrentId();

    int getPiecesTotal();

    Set<PeerSourceType> getPeerSourceTypes();

    PeerState getPeerState();

    Long getConnectAt();

    int getConnectAttempts();

    TransferAmount getTransferAmount();

    int getPieces();
}
