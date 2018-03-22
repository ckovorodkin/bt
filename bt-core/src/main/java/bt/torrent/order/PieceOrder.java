package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;
import java.util.Optional;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:38
 */
public interface PieceOrder {
    BitSet getMask();

    int next(BitSetAccumulator accumulator, BitSet mask);

    Optional<BitSet> getCurrentMask(int pieceIndex);
}
