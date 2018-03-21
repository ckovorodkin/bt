package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:51
 */
public class SequentialPieceOrder extends AbstractPieceOrder {
    public SequentialPieceOrder(BitSet mask) {
        super(mask);
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet complexMask = getComplexMask(mask);
        final BitSet ordinal = accumulator.getOrdinal(complexMask);
        return ordinal.nextSetBit(0);
    }
}
