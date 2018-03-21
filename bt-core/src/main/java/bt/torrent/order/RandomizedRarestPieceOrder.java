package bt.torrent.order;

import bt.torrent.BitSetAccumulator;
import bt.torrent.order.helper.RandomSetBit;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:41
 */
public class RandomizedRarestPieceOrder extends AbstractPieceOrder {
    private final RandomSetBit randomSetBit;

    public RandomizedRarestPieceOrder(BitSet mask) {
        super(mask);
        this.randomSetBit = new RandomSetBit();
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet complexMask = getComplexMask(mask);
        final BitSet rarest = accumulator.getRarest(complexMask);
        return randomSetBit.apply(rarest);
    }
}
