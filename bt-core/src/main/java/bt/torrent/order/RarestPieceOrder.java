package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:40
 */
public class RarestPieceOrder extends AbstractPieceOrder {
    public RarestPieceOrder(BitSet mask) {
        super(mask);
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet complexMask = getComplexMask(mask);
        final BitSet rarest = accumulator.getRarest(complexMask);
        return rarest.nextSetBit(0);
    }
}
