package bt.torrent.order;

import bt.torrent.BitSetAccumulator;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 08.02.2018 23:40
 */
public class RarestPieceOrder implements PieceOrder {
    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet rarest = accumulator.getRarest(mask);
        return rarest.nextSetBit(0);
    }
}
