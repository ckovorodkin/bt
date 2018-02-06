package bt.torrent.bitfield;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * @author Oleg Ermolaev Date: 06.02.2018 23:58
 */
public class PieceStatisticControllerTest {
    @Test
    public void testAdd() throws Exception {
        final Random random = new SecureRandom(new byte[]{1});
        final PieceStatisticController controller = new PieceStatisticController(8);
        final Queue<BitSet> queue = new LinkedList<>();
        boolean add = true;
        do {
            if (queue.size() == 64) {
                add = false;
            }
            if (add) {
                final BitSet bitSet = BitSet.valueOf(new long[]{random.nextLong()});
                queue.add(bitSet);
                controller.add(bitSet);
            } else {
                controller.remove(queue.remove());
            }

            for (int i = 0; i < controller.getDepth(); ++i) {
                final BitSet current = controller.get(i);
                for (int j = 0; j < 64; ++j) {
                    System.out.print(current.get(j) ? "+" : ".");
                }
                System.out.println();
            }
            System.out.println();
        } while (!queue.isEmpty());
    }
}
