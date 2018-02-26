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

import bt.data.Bitfield;
import bt.event.EventSink;
import bt.event.EventSource;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.IConnectionSource;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.statistic.TransferAmountStatistic;
import bt.torrent.DefaultTorrentSessionState;
import bt.torrent.PiecesStatistics;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.DataWorker;
import bt.torrent.data.IDataWorkerFactory;
import bt.torrent.messaging.Assignments;
import bt.torrent.messaging.BitfieldConsumer;
import bt.torrent.messaging.GenericConsumer;
import bt.torrent.messaging.IPeerWorkerFactory;
import bt.torrent.messaging.MetadataProducer;
import bt.torrent.messaging.PeerManager;
import bt.torrent.messaging.PeerRequestConsumer;
import bt.torrent.messaging.PeerWorkerFactory;
import bt.torrent.messaging.PieceConsumer;
import bt.torrent.messaging.RequestProducer;
import bt.torrent.messaging.TorrentWorker;
import bt.torrent.order.PieceOrder;

public class InitializeTorrentProcessingStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private TransferAmountStatistic transferAmountStatistic;
    private IConnectionSource connectionSource;
    private IPeerConnectionPool peerConnectionPool;
    private IMessageDispatcher messageDispatcher;
    private TorrentRegistry torrentRegistry;
    private IDataWorkerFactory dataWorkerFactory;
    private EventSource eventSource;
    private EventSink eventSink;
    private IRuntimeLifecycleBinder lifecycleBinder;
    private Config config;

    public InitializeTorrentProcessingStage(ProcessingStage<C> next,
                                            TransferAmountStatistic transferAmountStatistic,
                                            IConnectionSource connectionSource,
                                            IPeerConnectionPool peerConnectionPool,
                                            IMessageDispatcher messageDispatcher,
                                            TorrentRegistry torrentRegistry,
                                            IDataWorkerFactory dataWorkerFactory,
                                            EventSource eventSource,
                                            EventSink eventSink,
                                            IRuntimeLifecycleBinder lifecycleBinder,
                                            Config config) {
        super(next);
        this.transferAmountStatistic = transferAmountStatistic;
        this.connectionSource = connectionSource;
        this.peerConnectionPool = peerConnectionPool;
        this.messageDispatcher = messageDispatcher;
        this.torrentRegistry = torrentRegistry;
        this.dataWorkerFactory = dataWorkerFactory;
        this.eventSource = eventSource;
        this.eventSink = eventSink;
        this.lifecycleBinder = lifecycleBinder;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        final TorrentId torrentId = context.getTorrentId().get();
        Torrent torrent = context.getTorrent().get();
        TorrentDescriptor descriptor = torrentRegistry.register(torrent, context.getStorage());

        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        PiecesStatistics pieceStatistics = new PiecesStatistics(bitfield.getPiecesTotal());
        final PieceOrder pieceOrder = context.getPieceOrder();

        DataWorker dataWorker = createDataWorker(descriptor);
        Assignments assignments = new Assignments(bitfield, pieceOrder, pieceStatistics, config);

        IPeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(context.getRouter(), transferAmountStatistic);

        TorrentWorker torrentWorker =
                new TorrentWorker(torrentId, messageDispatcher, peerWorkerFactory, bitfield, assignments, config);

        final PeerManager peerManager = new PeerManager(torrentId,
                connectionSource,
                peerConnectionPool,
                transferAmountStatistic,
                pieceStatistics,
                torrentWorker,
                eventSource,
                eventSink,
                lifecycleBinder,
                config
        );

        context.setState(new DefaultTorrentSessionState(torrentId,
                descriptor,
                torrentWorker,
                peerManager,
                transferAmountStatistic
        ));


        context.getRouter().registerMessagingAgent(GenericConsumer.consumer());
        context.getRouter().registerMessagingAgent(new BitfieldConsumer(peerManager));
        context.getRouter().registerMessagingAgent(new PieceConsumer(descriptor.getDataDescriptor(), dataWorker));
        context.getRouter().registerMessagingAgent(new PeerRequestConsumer(dataWorker));
        context.getRouter().registerMessagingAgent(new RequestProducer(descriptor.getDataDescriptor(), dataWorker));
        context.getRouter().registerMessagingAgent(new MetadataProducer(() -> context.getTorrent().orElse(null), config));

        context.setBitfield(bitfield);
        context.setAssignments(assignments);
        context.setPieceStatistics(pieceStatistics);
    }

    private DataWorker createDataWorker(TorrentDescriptor descriptor) {
        return dataWorkerFactory.createWorker(descriptor.getDataDescriptor());
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
