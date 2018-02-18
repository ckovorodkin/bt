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

package bt.torrent.messaging;

import bt.BtException;
import bt.data.Bitfield;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.net.Peer;
import bt.protocol.Cancel;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.Request;
import bt.torrent.annotation.Produces;
import bt.torrent.data.DataWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import static bt.torrent.messaging.BlockKey.buildBlockKey;

/**
 * Produces block requests to the remote peer.
 *
 * @since 1.0
 */
public class RequestProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestProducer.class);

    private static final int MAX_PENDING_REQUESTS = 5;

    private Bitfield bitfield;
    private List<ChunkDescriptor> chunks;
    private final DataWorker dataWorker;

    public RequestProducer(DataDescriptor dataDescriptor, DataWorker dataWorker) {
        this.dataWorker = dataWorker;
        this.bitfield = dataDescriptor.getBitfield();
        this.chunks = dataDescriptor.getChunkDescriptors();
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {

        Peer peer = context.getPeer();
        ConnectionState connectionState = context.getConnectionState();

        if (!connectionState.getCurrentAssignment().isPresent()) {
            resetConnection(connectionState, messageConsumer);
            return;
        }

        Assignment assignment = connectionState.getCurrentAssignment().get();
        int currentPiece = assignment.getPiece();
        if (bitfield.isComplete(currentPiece)) {
            assignment.finish();
            resetConnection(connectionState, messageConsumer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Finished downloading piece #{}", currentPiece);
            }
            return;
        } else if (!connectionState.initializedRequestQueue()) {
            connectionState.getPendingWrites().clear();
            initializeRequestQueue(connectionState, currentPiece);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Begin downloading piece #{} from peer: {}. Request queue length: {}",
                        currentPiece, peer, connectionState.getRequestQueue().size());
            }
        }

        Queue<Request> requestQueue = connectionState.getRequestQueue();
        while (!requestQueue.isEmpty() && connectionState.getPendingRequests().size() <= MAX_PENDING_REQUESTS) {
            if (dataWorker.isOverload()) {
                assignment.check();
                //todo: one-time-message per each overload
                //LOGGER.trace("Can't produce write block request -- dataWorker is overloaded");
                break;
            }

            Request request = requestQueue.poll();

            ChunkDescriptor chunk = chunks.get(request.getPieceIndex());
            assert request.getOffset() % chunk.blockSize() == 0;
            final int blockIndex = (int) (request.getOffset() / chunk.blockSize());
            if (chunk.isPresent(blockIndex)) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Rejecting request to remote peer because the chunk block is already present: " +
                            "piece index {" + request.getPieceIndex() + "}, offset {" + request.getOffset()
                            + "}, length {" + request.getLength() + "}");
                }
                continue;
            }

            BlockKey key = buildBlockKey(request.getPieceIndex(), request.getOffset(), request.getLength());
            messageConsumer.accept(request);
            connectionState.getPendingRequests().add(key);
        }

        if (requestQueue.isEmpty() //br
                && connectionState.getPendingRequests().isEmpty() //br
                && connectionState.getPendingWrites().isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Aborted downloading piece #{}. All queries are empty.", currentPiece);
            }
            assignment.abort();
        }
    }

    private void resetConnection(ConnectionState connectionState, Consumer<Message> messageConsumer) {
        connectionState.setInitializedRequestQueue(false);
        connectionState.getRequestQueue().clear();
        connectionState.getPendingRequests().forEach(key -> //br
                messageConsumer.accept(new Cancel(key.getPieceIndex(), key.getOffset(), key.getLength())));
        connectionState.getPendingRequests().clear();
    }

    private void initializeRequestQueue(ConnectionState connectionState, int pieceIndex) {
        assert connectionState.getRequestQueue().isEmpty();
        assert connectionState.getPendingRequests().isEmpty();
        assert connectionState.getPendingWrites().isEmpty();
        List<Request> requests = buildRequests(pieceIndex);
        Collections.shuffle(requests);
        connectionState.getRequestQueue().addAll(requests);
        connectionState.setInitializedRequestQueue(true);
    }

    private List<Request> buildRequests(int pieceIndex) {
        List<Request> requests = new ArrayList<>();
        ChunkDescriptor chunk = chunks.get(pieceIndex);
        long chunkSize = chunk.getData().length();
        long blockSize = chunk.blockSize();

        for (int blockIndex = 0; blockIndex < chunk.blockCount(); blockIndex++) {
            if (!chunk.isPresent(blockIndex)) {
                int offset = (int) (blockIndex * blockSize);
                int length = (int) Math.min(blockSize, chunkSize - offset);
                try {
                    requests.add(new Request(pieceIndex, offset, length));
                } catch (InvalidMessageException e) {
                    // shouldn't happen
                    throw new BtException("Unexpected error", e);
                }
            }
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Built {} requests for piece #{} (size: {}, block size: {}, number of blocks: {})",
                    requests.size(), pieceIndex, chunkSize, blockSize, chunk.blockCount());
        }
        return requests;
    }
}
