package bt.torrent.bitfield;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * @author Oleg Ermolaev Date: 06.02.2018 23:10
 */
public class PieceStatisticController {
    private final int length;
    private final List<BitSet> bitSets;

    public PieceStatisticController(int length) {
        this.length = length;
        this.bitSets = new ArrayList<>();
    }

    public int getLength() {
        return length;
    }

    public int getDepth() {
        return bitSets.size();
    }

    public BitSet get(int order) {
        return bitSets.get(order);
    }

    public void add(BitSet bitSet0) {
        if (bitSet0.cardinality() == 0) {
            return;
        }
        final BitSet remainder = copyOf(bitSet0);
        if (bitSets.isEmpty()) {
            bitSets.add(remainder);
            return;
        }
        for (BitSet bitSet : bitSets) {
            final BitSet current = copyOf(bitSet);
            bitSet.or(remainder);
            remainder.and(current);
            if (remainder.cardinality() == 0) {
                break;
            }
        }
        if (remainder.cardinality() > 0) {
            bitSets.add(remainder);
        }
    }

    public void remove(BitSet bitSet0) {
        if (bitSet0.cardinality() == 0) {
            return;
        }
        final BitSet remainder = copyOf(bitSet0);
        int index = bitSets.size() - 1;
        while (index >= 0) {
            final BitSet bitSet = bitSets.get(index);
            final BitSet intersect = copyOf(bitSet);
            intersect.and(remainder);
            if (intersect.cardinality() > 0) {
                bitSet.xor(intersect);
                remainder.xor(intersect);
                if (remainder.cardinality() == 0) {
                    break;
                }
            }
            index--;
        }
        if (index == -1) {
            throw new IllegalStateException();
        }
    }

    private BitSet copyOf(BitSet bitSet) {
        return (BitSet) bitSet.clone();
    }
}
