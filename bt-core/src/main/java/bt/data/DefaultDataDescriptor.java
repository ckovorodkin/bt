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

package bt.data;

import bt.BtException;
import bt.data.range.BlockRange;
import bt.data.range.Ranges;
import bt.data.storage.Storage;
import bt.metainfo.Torrent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class DefaultDataDescriptor implements DataDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataDescriptor.class);

    private Storage storage;

    private Torrent torrent;
    private List<ChunkDescriptor> chunkDescriptors;
    private Bitfield bitfield;

    private List<TorrentFileInfo> torrentFileInfos;

    private ChunkVerifier verifier;

    public DefaultDataDescriptor(Storage storage,
                                 Torrent torrent,
                                 ChunkVerifier verifier,
                                 int transferBlockSize) {
        this.storage = storage;
        this.torrent = torrent;
        this.verifier = verifier;

        init(transferBlockSize);
    }

    private void init(long transferBlockSize) {
        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        if (transferBlockSize > chunkSize) {
            transferBlockSize = chunkSize;
        }

        int chunksTotal = (int) Math.ceil(totalSize / chunkSize);
        List<ChunkDescriptor> chunks = new ArrayList<>(chunksTotal + 1);

        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();

        final List<TorrentFileInfo> torrentFileInfos = storage.register(torrent);

        // filter out empty files (and create them at once)
        List<StorageUnit> nonEmptyStorageUnits = new ArrayList<>();
        torrentFileInfos.stream().map(TorrentFileInfo::getStorageUnit).forEach(unit -> {
            if (unit.capacity() > 0) {
                nonEmptyStorageUnits.add(unit);
            } else {
                try {
                    // TODO: think about adding some explicit "initialization/creation" method
                    unit.writeBlock(new byte[0], 0);
                } catch (Exception e) {
                    LOGGER.warn("Failed to create empty storage unit: " + unit, e);
                }
            }
        });

        if (nonEmptyStorageUnits.size() > 0) {
            long limitInLastUnit = nonEmptyStorageUnits.get(nonEmptyStorageUnits.size() - 1).capacity();
            DataRange data = new ReadWriteDataRange(nonEmptyStorageUnits, 0, limitInLastUnit);

            long off, lim;
            long remaining = totalSize;
            while (remaining > 0) {
                off = chunks.size() * chunkSize;
                lim = Math.min(chunkSize, remaining);

                DataRange subrange = data.getSubrange(off, lim);

                if (!chunkHashes.hasNext()) {
                    throw new BtException("Wrong number of chunk hashes in the torrent: too few");
                }

                chunks.add(buildChunkDescriptor(subrange, transferBlockSize, chunkHashes.next()));

                remaining -= chunkSize;
            }
        }

        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }

        this.bitfield = buildBitfield(chunks);
        this.chunkDescriptors = chunks;
        this.torrentFileInfos = torrentFileInfos;
    }

    private ChunkDescriptor buildChunkDescriptor(DataRange data, long blockSize, byte[] checksum) {
        BlockRange<DataRange> blockData = Ranges.blockRange(data, blockSize);
        DataRange synchronizedData = Ranges.synchronizedDataRange(blockData);
        BlockSet synchronizedBlockSet = Ranges.synchronizedBlockSet(blockData.getBlockSet());

        return new DefaultChunkDescriptor(synchronizedData, synchronizedBlockSet, checksum);
    }

    private Bitfield buildBitfield(List<ChunkDescriptor> chunks) {
        Bitfield bitfield = new Bitfield(chunks.size());
        verifier.verify(chunks, bitfield);
        /*todo*/
        //new Thread(() -> verifier.verify(chunks, bitfield)).start();
        return bitfield;
    }

    @Override
    public List<ChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }

    @Override
    public Bitfield getBitfield() {
        return bitfield;
    }

    @Override
    public List<TorrentFileInfo> getTorrentFileInfos() {
        return torrentFileInfos;
    }

    @Override
    public void close() {
        storage.unregister(torrent);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " <" + torrent.getName() + ">";
    }
}
