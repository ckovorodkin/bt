package bt.torrent.order;

import bt.torrent.BitSetAccumulator;
import bt.torrent.order.helper.RandomSetBit;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:41
 */
public class RandomizedRarestPieceOrder implements PieceOrder {
    private final RandomSetBit randomSetBit;

    public RandomizedRarestPieceOrder() {
        this.randomSetBit = new RandomSetBit();
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet rarest = accumulator.getRarest(mask);
        return randomSetBit.apply(rarest);
    }
}
