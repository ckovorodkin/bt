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

package bt.net;

import bt.CountingThreadFactory;
import bt.runtime.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static bt.logging.MDCWrapper.withMDCRemoteAddress;

public class IncomingConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingConnectionListener.class);

    private final Set<PeerConnectionAcceptor> connectionAcceptors;
    private final ExecutorService connectionExecutor;
    private final IPeerConnectionPool connectionPool;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final Config config;

    private final ExecutorService executor;
    private volatile boolean shutdown;

    public IncomingConnectionListener(Set<PeerConnectionAcceptor> connectionAcceptors,
                                      ExecutorService connectionExecutor,
                                      IPeerConnectionPool connectionPool,
                                      Config config) {
        this.connectionAcceptors = connectionAcceptors;
        this.connectionExecutor = connectionExecutor;
        this.connectionPool = connectionPool;
        this.config = config;

        this.executor = Executors.newFixedThreadPool(
                connectionAcceptors.size(),
                CountingThreadFactory.factory("bt.net.pool.incoming-acceptor"));
    }

    public void startup() {
        connectionAcceptors.forEach(acceptor ->
                executor.submit(() -> {
                    while (!shutdown) {
                        final ConnectionRoutine connectionRoutine;
                        try {
                            connectionRoutine = acceptor.accept();
                        } catch (Exception e) {
                            LOGGER.error("Unexpected error", e);
                            return;
                        }

                        withMDCRemoteAddress(connectionRoutine.getRemoteAddress()).run(() -> {
                            if (connectionPool.mightAddIncomingConnection(connectionRoutine.getRemoteAddress())) {
                                establishConnection(connectionRoutine);
                            } else {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug(
                                            "Rejecting incoming connection from {} due to exceeding of connections limit",
                                            connectionRoutine.getRemoteAddress()
                                    );
                                }
                                connectionRoutine.cancel();
                            }
                        });
                    }}));
    }

    private void establishConnection(ConnectionRoutine connectionRoutine) {
        connectionExecutor.submit(withMDCRemoteAddress(connectionRoutine.getRemoteAddress()).wrap(() -> {
            boolean added = false;
            if (!shutdown) {
                ConnectionResult connectionResult = connectionRoutine.establish();
                if (connectionResult.isSuccess()) {
                    if (!shutdown && connectionPool.mightAddIncomingConnection(connectionRoutine.getRemoteAddress())) {
                        connectionPool.addConnectionIfAbsent(connectionResult.getConnection());
                        added = true;
                    }
                }
            }
            if (!added) {
                connectionRoutine.cancel();
            }
        }));
    }

    public void shutdown() {
        shutdown = true;
        executor.shutdownNow();
    }
}
