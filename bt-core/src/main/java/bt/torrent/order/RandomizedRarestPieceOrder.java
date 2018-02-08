package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;
import java.util.Random;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:41
 */
public class RandomizedRarestPieceOrder implements PieceOrder {
    private final Random random;

    public RandomizedRarestPieceOrder() {
        this.random = new Random(System.currentTimeMillis());
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet rarest = accumulator.getRarest(mask);
        final int cardinality = rarest.cardinality();
        if (cardinality == 0) {
            return -1;
        }
        final int position = random.nextInt(cardinality);
        int current = -1;
        for (int i = 0; i < position; i++) {
            current = rarest.nextSetBit(current + 1);
        }
        assert current != -1;
        return current;
    }
}
