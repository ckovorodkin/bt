package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;
import java.util.Random;

/**
 * @author Oleg Ermolaev Date: 09.02.2018 1:46
 */
public class RandomPieceOrder implements PieceOrder {
    private final Random random;

    public RandomPieceOrder() {
        this.random = new Random(System.currentTimeMillis());
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet ordinal = accumulator.getOrdinal(mask);
        final int cardinality = ordinal.cardinality();
        if (cardinality == 0) {
            return -1;
        }
        final int position = random.nextInt(cardinality);
        int current = -1;
        for (int i = 0; i < position + 1; i++) {
            current = ordinal.nextSetBit(current + 1);
        }
        assert current != -1;
        return current;
    }
}
