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

import bt.metainfo.TorrentId;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.RuntimeLifecycleBinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;

import static java.util.Collections.singleton;
import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class ConnectionSourceTest {
    private static final Config CONFIG = new Config() {
    };

    private ConnectionSource connectionSource;
    private RuntimeLifecycleBinder lifecycleBinder;

    @Before
    public void setUp() throws Exception {
        lifecycleBinder = new RuntimeLifecycleBinder();
        connectionSource = new ConnectionSource(singleton((PeerConnectionAcceptor) () -> {
            throw new UnsupportedOperationException();
        }), new MockPeerConnectionFactory(), new MockPeerConnectionPool(), lifecycleBinder, CONFIG);
    }

    @After
    public void tearDown() throws Exception {
        lifecycleBinder.visitBindings(
                IRuntimeLifecycleBinder.LifecycleEvent.SHUTDOWN,
                lifecycleBinding -> lifecycleBinding.getRunnable().run()
        );
    }

    @Test
    public void test_getConnection() throws Exception {
        final InetPeer peer = new InetPeer(InetAddress.getLoopbackAddress(), 0);
        final TorrentId torrentId = TorrentId.fromBytes(new byte[TorrentId.TORRENT_ID_LENGTH]);
        final ConnectionResult connectionResult = connectionSource.getConnection(peer, torrentId);
        assertTrue(connectionResult.isSuccess());
    }

    @Test
    public void test_concurrent_getConnection() throws Exception {
        final InetPeer peer = new InetPeer(InetAddress.getLoopbackAddress(), 1);
        final TorrentId torrentId = TorrentId.fromBytes(new byte[TorrentId.TORRENT_ID_LENGTH]);

        final int threadCount = 100;
        final Collection<ConnectionResult> connectionResults = new ConcurrentLinkedQueue<>();
        final CyclicBarrier barrier = new CyclicBarrier(1 + threadCount);
        final Semaphore semaphore = new Semaphore(1 - threadCount);
        for (int i = 0; i < threadCount; ++i) {
            new Thread(() -> {
                try {
                    barrier.await();
                    final ConnectionResult threadConnectionResult = connectionSource.getConnection(peer, torrentId);
                    connectionResults.add(threadConnectionResult);
                } catch (InterruptedException | BrokenBarrierException ignore) {
                } finally {
                    semaphore.release();
                }
            }).start();
            //Thread.sleep(3000 / threadCount);
        }

        assertTrue(connectionResults.isEmpty());
        barrier.await();
        semaphore.acquire();
        assertEquals(threadCount, connectionResults.size());

        final Field connectionExecutorField = connectionSource.getClass().getDeclaredField("connectionExecutor");
        try {
            connectionExecutorField.setAccessible(true);
            final ThreadPoolExecutor executor =
                    ThreadPoolExecutor.class.cast(connectionExecutorField.get(connectionSource));
            //assertEquals(1, executor.getCompletedTaskCount());    // don't work in jdk1.8.0_162 but work in jdk1.8.0_66
        } finally {
            connectionExecutorField.setAccessible(false);
        }

        ConnectionResult commonConnectionResult = null;
        for (ConnectionResult threadConnectionResult : connectionResults) {
            assertEquals(peer, threadConnectionResult.getConnection().getRemotePeer());
            assertEquals(torrentId, threadConnectionResult.getConnection().getTorrentId());
            if (commonConnectionResult == null) {
                commonConnectionResult = threadConnectionResult;
            } else {
                assertEquals(commonConnectionResult.getConnection(), threadConnectionResult.getConnection());
            }
        }

        final Field pendingConnectionsField = connectionSource.getClass().getDeclaredField("pendingConnections");
        try {
            pendingConnectionsField.setAccessible(true);
            final Map expected = Map.class.cast(pendingConnectionsField.get(connectionSource));
            assertEquals(0, expected.size());
        } finally {
            pendingConnectionsField.setAccessible(false);
        }
    }
}
