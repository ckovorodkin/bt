package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:38
 */
public interface PieceOrder {
    BitSet getMask();

    int next(BitSetAccumulator accumulator, BitSet mask);
}
