package bt.torrent.order;

import bt.data.Bitfield;
import bt.torrent.BitSetAccumulator;

import java.util.BitSet;

/**
 * @author Oleg Ermolaev Date: 09.02.2018 1:12
 */
public class MaskingPieceOrder implements PieceOrder {
    private final PieceOrder delegate;
    private final Bitfield mask;

    public MaskingPieceOrder(PieceOrder delegate, Bitfield mask) {
        this.delegate = delegate;
        this.mask = mask;
    }

    @Override
    public int next(BitSetAccumulator accumulator, BitSet mask) {
        final BitSet mixedMask = this.mask.getPieces();
        mixedMask.and(mask);
        return delegate.next(accumulator, mixedMask);
    }
}
