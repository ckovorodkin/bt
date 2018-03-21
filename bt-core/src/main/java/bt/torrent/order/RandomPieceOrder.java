package bt.torrent.order;

import bt.torrent.BitSetAccumulator;
import bt.torrent.order.helper.RandomSetBit;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 09.02.2018 1:46
 */
public class RandomPieceOrder  extends AbstractPieceOrder {
    private final RandomSetBit randomSetBit;

    public RandomPieceOrder(BitSet mask) {
        super(mask);
        this.randomSetBit = new RandomSetBit();
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet complexMask = getComplexMask(mask);
        final BitSet ordinal = accumulator.getOrdinal(complexMask);
        return randomSetBit.apply(ordinal);
    }
}
