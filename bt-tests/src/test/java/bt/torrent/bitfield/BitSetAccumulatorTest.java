package bt.torrent.bitfield;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author Oleg Ermolaev Date: 06.02.2018 23:58
 */
public class BitSetAccumulatorTest {
    @Test
    public void testAdd() throws Exception {
        final byte[] bytes = new byte[20];
        final Random random = new SecureRandom(new byte[]{1});
        final BitSetAccumulator controller = new BitSetAccumulator(bytes.length * Byte.SIZE);
        final Queue<BitSet> queue = new LinkedList<>();
        boolean add = true;
        do {
            if (queue.size() == 100) {
                add = false;
                //System.out.printf("%d;%.3f\n",queue.size(), controller.getRatio());
            }
            if (add) {
                random.nextBytes(bytes);
                final BitSet bitSet = BitSet.valueOf(bytes);
                queue.add(bitSet);
                controller.add(bitSet);
                //System.out.printf("%d;%d\n",queue.size(), controller.getAddMaxIterationCount());
            } else {
                controller.remove(queue.remove());
            }
            dumpRarest(controller);
        } while (!queue.isEmpty());
        dump(controller);
        System.out.println(controller.getAddMaxIterationCount());
        System.out.println(controller.getRemoveMaxIterationCount());
        System.out.println(controller.getRarestMaxIterationCount());
        assertEquals(0, controller.getDepth());
    }

    private void dumpRarest(BitSetAccumulator controller) {
        final BitSet mask = new BitSet(controller.getLength());
        mask.flip(0, controller.getLength());
        final BitSet rarest = controller.getRarest(mask);
        System.out.printf("%4d ", rarest.cardinality());
        dump(rarest, controller.getLength());
    }

    private void dump(BitSetAccumulator controller) {
        for (int i = 0; i < controller.getDepth(); ++i) {
            dump(controller.get(i), controller.getLength());
        }
        System.out.println();
    }

    private void dump(BitSet bitSet, int length) {
        for (int j = 0; j < length; ++j) {
            System.out.print(bitSet.get(j) ? "+" : ".");
        }
        System.out.println();
    }
}
