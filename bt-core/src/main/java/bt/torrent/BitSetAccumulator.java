package bt.torrent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static java.lang.StrictMath.max;

/**
 * @author Oleg Ermolaev Date: 06.02.2018 23:10
 */
public class BitSetAccumulator {
    private final int length;
    private final List<BitSet> bitSets;
    private int firstIncompleteIndex;
    private int addBitMaxIterationCount = 0;
    private int addMaxIterationCount = 0;
    private int removeMaxIterationCount = 0;
    private int rarestMaxIterationCount = 0;

    public BitSetAccumulator(int length) {
        this.length = length;
        this.bitSets = new ArrayList<>();
        this.firstIncompleteIndex = 0;
    }

    public int getLength() {
        return length;
    }

    public boolean isEmpty() {
        return bitSets.isEmpty();
    }

    public void clear() {
        bitSets.clear();
        firstIncompleteIndex = 0;
    }

    public double getRatio(BitSet addition) {
        if (addition.length() > length) {
            throw new IllegalArgumentException();
        }
        final int cardinality = addition.cardinality();
        if (addition.cardinality() == 0) {
            return getRatio();
        }
        if (addition.cardinality() == length) {
            return getRatio() + 1;
        }
        if (bitSets.isEmpty()) {
            assert firstIncompleteIndex == 0;
            return cardinality / (double) length;
        }
        if (firstIncompleteIndex == bitSets.size()) {
            return firstIncompleteIndex + cardinality / (double) length;
        }
        add(addition);
        final double ratio = getRatio();
        remove(addition);
        return ratio;
    }
    public double getRatio() {
        if (bitSets.isEmpty()) {
            assert firstIncompleteIndex == 0;
            return 0.0;
        }
        if (firstIncompleteIndex == bitSets.size()) {
            return firstIncompleteIndex;
        }
        return firstIncompleteIndex + bitSets.get(firstIncompleteIndex).cardinality() / (double) length;
    }

    public void add(int bit) {
        if (bit >= length) {
            throw new IllegalArgumentException();
        }
        boolean added = false;
        int iterationCount = 0;
        for (int index = firstIncompleteIndex; index < bitSets.size(); index++) {
            iterationCount++;
            final BitSet current = bitSets.get(index);
            added = !current.get(bit);
            if (added) {
                current.set(bit);
                if (current.cardinality() == length) {
                    firstIncompleteIndex++;
                }
                break;
            }
        }
        if (addBitMaxIterationCount < iterationCount) {
            addBitMaxIterationCount = iterationCount;
        }
        if (!added) {
            final BitSet remainder = new BitSet(length);
            remainder.set(bit);
            bitSets.add(remainder);
        }
    }

    public void add(BitSet bitSet) {
        if (bitSet.length() > length) {
            throw new IllegalArgumentException();
        }
        int cardinality = bitSet.cardinality();
        if (cardinality == 0) {
            return;
        }
        final BitSet remainder = copyOf(bitSet);
        if (bitSets.isEmpty() || cardinality == length) {
            bitSets.add(firstIncompleteIndex, remainder);
            if (cardinality == length) {
                firstIncompleteIndex++;
            }
            return;
        }
        int iterationCount = 0;
        for (int index = firstIncompleteIndex; index < bitSets.size(); index++) {
            iterationCount++;
            final BitSet current = bitSets.get(index);
            final BitSet copy = copyOf(current);
            current.or(remainder);
            if (current.cardinality() == length) {
                firstIncompleteIndex++;
            }
            remainder.and(copy);
            cardinality = remainder.cardinality();
            if (cardinality == 0) {
                break;
            }
        }
        if (addMaxIterationCount < iterationCount) {
            addMaxIterationCount = iterationCount;
        }
        if (cardinality > 0) {
            bitSets.add(remainder);
        }
    }

    public BitSet remove(BitSet bitSet) {
        if (bitSet.length() > length) {
            throw new IllegalArgumentException();
        }
        int cardinality = bitSet.cardinality();
        final BitSet remainder = copyOf(bitSet);
        if (cardinality == 0 || bitSets.isEmpty()) {
            return remainder;
        }
        if (cardinality == length && bitSets.get(0).cardinality() == length) {
            bitSets.remove(0);
            firstIncompleteIndex--;
            assert firstIncompleteIndex >= 0;
            remainder.clear();
            return remainder;
        }
        int index = bitSets.size() - 1;
        int iterationCount = 0;
        while (index >= 0) {
            iterationCount++;
            final BitSet current = bitSets.get(index);
            final BitSet intersect = copyOf(current);
            intersect.and(remainder);
            if (intersect.cardinality() > 0) {
                current.xor(intersect);
                if (index < firstIncompleteIndex) {
                    firstIncompleteIndex = index;
                }
                if (current.cardinality() == 0) {
                    assert index == bitSets.size() - 1;
                    bitSets.remove(index);
                }
                remainder.xor(intersect);
                cardinality = remainder.cardinality();
                if (cardinality == 0) {
                    break;
                }
            }
            index--;
        }
        if (removeMaxIterationCount < iterationCount) {
            removeMaxIterationCount = iterationCount;
        }
        return remainder;
    }

    public BitSet getOrdinal() {
        if (bitSets.isEmpty()) {
            return new BitSet(length);
        }
        return getOrdinal(null, false);
    }

    public BitSet getOrdinal(BitSet mask) {
        if (mask.length() > length) {
            throw new IllegalArgumentException();
        }
        if (bitSets.isEmpty()) {
            return new BitSet(length);
        }
        final int maskCardinality = mask.cardinality();
        if (maskCardinality == 0) {
            return new BitSet(length);
        }
        final boolean useMask = maskCardinality < length;
        return getOrdinal(mask, useMask);
    }

    private BitSet getOrdinal(BitSet mask, boolean useMask) {
        final BitSet result = copyOf(bitSets.get(0));
        if (useMask) {
            result.and(mask);
        }
        return result;
    }

    public BitSet getRarest() {
        if (bitSets.isEmpty()) {
            return new BitSet(length);
        }
        return getRarest(null, false);
    }

    public BitSet getRarest(BitSet mask) {
        if (mask.length() > length) {
            throw new IllegalArgumentException();
        }
        if (bitSets.isEmpty()) {
            return new BitSet(length);
        }
        final int maskCardinality = mask.cardinality();
        if (maskCardinality == 0) {
            return new BitSet(length);
        }
        final boolean useMask = maskCardinality < length;
        return getRarest(mask, useMask);
    }

    private BitSet getRarest(BitSet mask, boolean useMask) {
        assert !bitSets.isEmpty();
        BitSet result = null;
        int iterationCount = 0;
        for (int index = max(0, firstIncompleteIndex - 1); index < bitSets.size(); index++) {
            iterationCount++;
            result = copyOf(bitSets.get(index));
            if (useMask) {
                result.and(mask);
                if (result.cardinality() == 0) {
                    break;
                }
            }
            final int nextIndex = index + 1;
            if (nextIndex < bitSets.size()) {
                result.andNot(bitSets.get(nextIndex));
            }
            if (result.cardinality() != 0) {
                break;
            }
        }
        assert result != null;
        if (rarestMaxIterationCount < iterationCount) {
            rarestMaxIterationCount = iterationCount;
        }
        return result;
    }

    int getDepth() {
        return bitSets.size();
    }

    BitSet get(int order) {
        return bitSets.get(order);
    }

    int getAddBitMaxIterationCount() {
        return addBitMaxIterationCount;
    }

    int getAddMaxIterationCount() {
        return addMaxIterationCount;
    }

    int getRemoveMaxIterationCount() {
        return removeMaxIterationCount;
    }

    int getRarestMaxIterationCount() {
        return rarestMaxIterationCount;
    }

    private BitSet copyOf(BitSet bitSet) {
        return (BitSet) bitSet.clone();
    }
}
