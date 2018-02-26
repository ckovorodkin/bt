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

package bt.processor.torrent;

import bt.metainfo.TorrentId;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.DefaultMessageRouter;
import bt.torrent.messaging.MessageRouter;

import java.util.Set;

public class CreateSessionStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private TorrentRegistry torrentRegistry;
    private Set<Object> messagingAgents;

    public CreateSessionStage(ProcessingStage<C> next, TorrentRegistry torrentRegistry, Set<Object> messagingAgents) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.messagingAgents = messagingAgents;
    }

    @Override
    protected void doExecute(C context) {
        TorrentId torrentId = context.getTorrentId().get();
        TorrentDescriptor descriptor = torrentRegistry.register(torrentId);

        MessageRouter router = new DefaultMessageRouter(messagingAgents);
        context.setRouter(router);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
