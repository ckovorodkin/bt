package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:51
 */
public class SequentialPieceOrder implements PieceOrder {
    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet ordinal = accumulator.getOrdinal(mask);
        return ordinal.nextSetBit(0);
    }
}
