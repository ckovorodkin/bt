package bt.torrent.messaging;

import bt.protocol.Have;
import bt.protocol.Message;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * @author Oleg Ermolaev Date: 11.02.2018 16:58
 */
class PieceAnnouncingPeerWorker implements PeerWorker {
    private final PeerWorker delegate;
    private final Supplier<Iterable<? extends PeerWorker>> activePeerWorkersSupplier;
    private final Queue<Have> pieceAnnouncements;

    PieceAnnouncingPeerWorker(
            PeerWorker delegate, Supplier<Iterable<? extends PeerWorker>> activePeerWorkersSupplier) {
        this.delegate = delegate;
        this.activePeerWorkersSupplier = activePeerWorkersSupplier;
        this.pieceAnnouncements = new ConcurrentLinkedQueue<>();
    }

    @Override
    public ConnectionState getConnectionState() {
        return delegate.getConnectionState();
    }

    @Override
    public void accept(Message message) {
        delegate.accept(message);
    }

    @Override
    public Message get() {
        Message message = pieceAnnouncements.poll();
        if (message != null) {
            //todo oe: low priority
            return message;
        }

        message = delegate.get();
        if (message != null && Have.class.equals(message.getClass())) {
            Have have = (Have) message;
            activePeerWorkersSupplier.get().forEach(worker -> {
                if (this != worker && PieceAnnouncingPeerWorker.class.isAssignableFrom(worker.getClass())) {
                    PieceAnnouncingPeerWorker.class.cast(worker).getPieceAnnouncements().add(have);
                }
            });
        }
        return message;
    }

    private Queue<Have> getPieceAnnouncements() {
        return pieceAnnouncements;
    }
}
