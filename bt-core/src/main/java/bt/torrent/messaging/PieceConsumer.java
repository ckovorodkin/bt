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

import bt.data.Bitfield;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.net.Peer;
import bt.protocol.Piece;
import bt.torrent.annotation.Consumes;
import bt.torrent.data.BlockWrite;
import bt.torrent.data.DataWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static bt.torrent.messaging.BlockKey.buildBlockKey;

/**
 * Consumes blocks, received from the remote peer.
 *
 * @since 1.0
 */
public class PieceConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PieceConsumer.class);

    private Bitfield bitfield;
    private final List<ChunkDescriptor> chunks;
    private DataWorker dataWorker;

    public PieceConsumer(DataDescriptor dataDescriptor, DataWorker dataWorker) {
        this.bitfield = dataDescriptor.getBitfield();
        this.chunks = dataDescriptor.getChunkDescriptors();
        this.dataWorker = dataWorker;
    }

    @Consumes
    public void consume(Piece piece, MessageContext context) {
        Peer peer = context.getPeer();
        ConnectionState connectionState = context.getConnectionState();

        final Long requestedAt = connectionState.getPendingRequests().remove(buildBlockKey(piece));
        // check that this block was requested in the first place
        if (requestedAt == null) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "Discarding unexpected block from peer {}: piece index {{}}, offset {{}}, length {{}}",
                        peer,
                        piece.getPieceIndex(),
                        piece.getOffset(),
                        piece.getBlock().length
                );
            }
            return;
        } else {
            final long elapsed = System.currentTimeMillis() - requestedAt;
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received block after {} ms: piece index {{}}, offset {{}}, length {{}}",
                        elapsed,
                        piece.getPieceIndex(),
                        piece.getOffset(),
                        piece.getBlock().length
                );
            }
            //todo: use 'elapsed' for control peer's queue (re-request abnormally delayed blocks).
        }

        if (connectionState.getCurrentAssignment().isPresent()) {
            Assignment assignment = connectionState.getCurrentAssignment().get();
            if (piece.getPieceIndex() == assignment.getPiece()) {
                assignment.check();
            }
        }

        // discard blocks for pieces that have already been verified
        if (bitfield.isCompleteVerified(piece.getPieceIndex())) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "Discarding received block because the chunk is already complete and verified: " +
                        "piece index {" + piece.getPieceIndex() + "}, " +
                        "offset {" + piece.getOffset() + "}, " +
                        "length {" + piece.getBlock().length + "}");
            }
            return;
        }

        final ChunkDescriptor chunk = chunks.get(piece.getPieceIndex());
        final long blockSize = chunk.blockSize();
        assert piece.getOffset() % blockSize == 0;
        final int blockIndex = (int) (piece.getOffset() / blockSize);
        if (chunk.isPresent(blockIndex)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Discarding received block because the chunk is already contains that block: " +
                        "piece index {" + piece.getPieceIndex() + "}, " +
                        "offset {" + piece.getOffset() + "}, " +
                        "length {" + piece.getBlock().length + "}");
            }
            return;
        }

        connectionState.incrementDownloaded(piece.getBlock().length);

        addBlock(peer, connectionState, piece).whenComplete((block, error) -> {
            boolean verificationFuturePresent = false;
            try {
                if (error != null) {
                    throw new RuntimeException("Failed to perform request to write block", error);
                }
                if (block.getError().isPresent()) {
                    throw new RuntimeException("Failed to perform request to write block", block.getError().get());
                }
                if (block.isRejected()) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Request to write block could not be completed: " + piece);
                    }
                    return;
                }
                Optional<CompletableFuture<Boolean>> verificationFuture = block.getVerificationFuture();
                verificationFuturePresent = verificationFuture.isPresent();
                if (verificationFuturePresent) {
                    verificationFuture.get().whenComplete((verified, error1) -> {
                        connectionState.getPendingWrites().remove(buildBlockKey(piece));
                        if (error1 != null) {
                            throw new RuntimeException("Failed to verify block", error1);
                        }
                    });
                }
            } finally {
                if (!verificationFuturePresent) {
                    connectionState.getPendingWrites().remove(buildBlockKey(piece));
                }
            }
        });
    }

    private CompletableFuture<BlockWrite> addBlock(Peer peer, ConnectionState connectionState, Piece piece) {
        int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset();

        byte[] block = piece.getBlock();

        CompletableFuture<BlockWrite> future = dataWorker.addBlock(peer, pieceIndex, offset, block);
        connectionState.getPendingWrites().put(buildBlockKey(piece), future);
        return future;
    }
}
