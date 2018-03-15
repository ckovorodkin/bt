/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.cli;

import bt.cli.display.Unit;
import bt.event.PeerSourceType;
import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.torrent.TorrentSessionState;
import bt.torrent.messaging.PeerInfoView;
import bt.torrent.messaging.PeerState;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static bt.cli.comparator.InetAddressComparator.compareInetAddress;
import static bt.cli.display.Unit.getDisplayString;

public class SessionStatePrinter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionStatePrinter.class);

    private static final Unit[] units = new Unit[]{new Unit(1L, "B"), new Unit(1L << 10, "KB"), //br
            new Unit(1L << 20, "MB"), new Unit(1L << 30, "GB"), new Unit(1L << 40, "TB")};

    private static final String AMOUNT_UNIT_FORMAT = "%6.1f %s";
    private static final String RATE_UNIT_FORMAT = "%6.1f %s/s";

    private static final String TORRENT_INFO = "Downloading %s (%,d B)";
    private static final String DURATION_INFO ="Elapsed time: %s\t\tRemaining time: %s";
    private static final String SESSION_INFO = "Peers: %2d/%d\t\tDown: %11s %9s\t\tUp: %11s %9s\t\t";

    private static final String WHITESPACES = "\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020";

    private static final String LOG_ENTRY =
            "Downloading.. Peers: %s; Down: %11s; Up: %11s; %.2f%% complete; Remaining time: %s";

    private static final String LOG_ENTRY_SEED = "Seeding.. Peers: %s; Up: %11s";

    public static SessionStatePrinter createKeyInputAwarePrinter(Collection<KeyStrokeBinding> bindings) {
        return new SessionStatePrinter() {
            private Thread t;

            {
                t = new Thread(() -> {
                    while (!isShutdown()) {
                        try {
                            // don't intercept input when paused
                            if (super.supressOutput) {
                                Thread.sleep(1000);
                                continue;
                            }

                            KeyStroke keyStroke = pollKeyInput();
                            if (keyStroke == null) {
                                Thread.sleep(100);
                            } else if (keyStroke.isCtrlDown() && keyStroke.getKeyType() == KeyType.Character
                                    && keyStroke.getCharacter().equals('c')) {
                                shutdown();
                                System.exit(0);
                            } else {
                                bindings.forEach(binding -> {
                                    if (keyStroke.equals(binding.getKeyStroke())) {
                                        binding.getBinding().run();
                                    }
                                });
                            }
                        } catch (Throwable e) {
                            LOGGER.error("Unexpected error when reading user input", e);
                        }
                    }
                });
                t.setDaemon(true);
                t.start();

                Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            }

            @Override
            public void shutdown() {
                if (!isShutdown()) {
                    super.shutdown();
                    t.interrupt();
                }
            }
        };
    }

    private Screen screen;
    private TextGraphics graphics;

    private volatile boolean supressOutput;
    private volatile boolean shutdown;

    private Optional<Torrent> torrent;
    private volatile long started;
    private volatile long downloaded;
    private volatile long uploaded;
    private volatile boolean showDownload;
    private volatile Long lastShowDownloadUpdateAt;
    private final ConcurrentHashMap<Peer, AtomicLong> peerDownloadMap;
    private final ConcurrentHashMap<Peer, AtomicLong> peerUploadMap;

    public SessionStatePrinter() {
        try {
            Terminal terminal = new DefaultTerminalFactory(System.out, System.in,
                     Charset.forName("UTF-8")).createTerminal();
            terminal.setCursorVisible(false);

            screen = new TerminalScreen(terminal);
            graphics = screen.newTextGraphics();
            screen.startScreen();
            clearScreen();

            started = System.currentTimeMillis();
            showDownload = true;
            lastShowDownloadUpdateAt = null;

            peerDownloadMap = new ConcurrentHashMap<>();
            peerUploadMap = new ConcurrentHashMap<>();
            this.torrent = Optional.empty();
            printTorrentInfo();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create terminal", e);
        }
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = Optional.of(torrent);
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }

        try {
            clearScreen();
            screen.stopScreen();
        } catch (Throwable e) {
            // ignore
        } finally {
            shutdown = true;
        }
    }

    /**
     * blocking
     */
    public KeyStroke readKeyInput() throws IOException {
        return screen.readInput();
    }

    /**
     * non-blocking
     */
    public KeyStroke pollKeyInput() throws IOException {
        return screen.pollInput();
    }

    private void printTorrentInfo() {
        printTorrentNameAndSize(torrent);
        fillLine(1, '-');
    }

    private void printTorrentNameAndSize(Optional<Torrent> torrent) {
        String name = torrent.isPresent() ? torrent.get().getName() : "";
        long size = torrent.isPresent() ? torrent.get().getSize() : 0;

        graphics.putString(0, 0, String.format(TORRENT_INFO, name, size));
    }

    /**
     * call me once per second
     */
    public synchronized void print(TorrentSessionState sessionState) {
        if (supressOutput || shutdown) {
            return;
        }

        try {
            final long currentTimeMillis = System.currentTimeMillis();

            printTorrentInfo();

            long downloaded = sessionState.getDownloaded();
            long uploaded = sessionState.getUploaded();

            final long uploadRate = uploaded - this.uploaded;
            final long downloadRate = downloaded - this.downloaded;

            final long interval = currentTimeMillis - (lastShowDownloadUpdateAt == null ? 0 : lastShowDownloadUpdateAt);
            final boolean updateShowDownload = lastShowDownloadUpdateAt == null //br
                    || showDownload && interval >= 10_000 || !showDownload && interval >= 5_000;
            if (updateShowDownload) {
                if (downloadRate == uploadRate) {
                    if (downloadRate + uploadRate != 0) {
                        showDownload = false;
                    }
                    lastShowDownloadUpdateAt = null;
                } else {
                    final boolean newShowDownload = downloadRate > uploadRate;
                    if (lastShowDownloadUpdateAt == null || showDownload != newShowDownload) {
                        showDownload = newShowDownload;
                        lastShowDownloadUpdateAt = currentTimeMillis;
                    }
                }
            }

            printTorrentNameAndSize(torrent);

            int row = 1;

            final long torrentSize = torrent.isPresent() ? torrent.get().getSize() : 0;
            final long selectDownload = sessionState.getSelectDownload();
            final long leftDownload = sessionState.getLeftDownload();
            final long leftVerify = sessionState.getLeftVerify();
            graphics.putString(0, ++row, String.format("Total: %9s, selected: %9s, left: %9s, verify: %9s",
                    getDisplayString(AMOUNT_UNIT_FORMAT, torrentSize, units, false),
                    getDisplayString(AMOUNT_UNIT_FORMAT, selectDownload, units, false),
                    getDisplayString(AMOUNT_UNIT_FORMAT, leftDownload, units, false),
                    getDisplayString(AMOUNT_UNIT_FORMAT, leftVerify, units, false)
            ));

            String elapsedTime = getElapsedTime(currentTimeMillis);
            String remainingTime = getRemainingTime(
                    downloadRate,
                    sessionState.getPiecesRemaining(), sessionState.getPiecesNotSkipped());
            graphics.putString(0, ++row, String.format(DURATION_INFO, elapsedTime, remainingTime));

            int peerCount = sessionState.getConnectedPeers().size();
            int activePeerCount = sessionState.getActivePeers().size();
            String sessionInfo = String.format(
                    SESSION_INFO,
                    activePeerCount,
                    peerCount,
                    getDisplayString(RATE_UNIT_FORMAT, downloadRate, units, false),
                    getDisplayString(AMOUNT_UNIT_FORMAT, downloaded, units),
                    getDisplayString(RATE_UNIT_FORMAT, uploadRate, units, false),
                    getDisplayString(AMOUNT_UNIT_FORMAT, uploaded, units)
            );
            graphics.putString(0, ++row, sessionInfo);

            int completed = sessionState.getPiecesComplete();
            double completePercents = getCompletePercentage(sessionState.getPiecesTotal(), completed);
            double requiredPercents = getTargetPercentage(sessionState.getPiecesTotal(), completed, sessionState.getPiecesRemaining());
            graphics.putString(0, ++row, getProgressBar(completePercents, requiredPercents));

            final double ratio = sessionState.getRatio();
            graphics.putString(0, ++row, getPiecesBar(sessionState.getPieces(), sessionState.getPiecesTotal(), ratio));

            boolean complete = sessionState.isComplete();
            if (complete) {
                graphics.putString(0, ++row, "Download is complete. Press Ctrl-C to stop seeding and exit.");
            } else {
                fillLine(++row, ' ');
            }

            final Collection<PeerInfoView> peerInfos = sessionState.getPeerInfos();

            final Collection<PeerInfoView> onlinePeerInfos = sessionState.getOnlinePeerInfos();
            final int onlinePeerCount = onlinePeerInfos.size();
            final int onlineSeedCount = getSeeds(onlinePeerInfos);
            final int onlineLeachCount = onlinePeerCount - onlineSeedCount;

            final Collection<PeerInfoView> connectedPeerInfos = sessionState.getConnectedPeerInfos();
            final int connectedPeerCount = connectedPeerInfos.size();
            final int connectedSeedCount = getSeeds(connectedPeerInfos);
            final int connectedLeachCount = connectedPeerCount - connectedSeedCount;

            fillLine(++row, ' ');
            graphics.putString(0, row, String.format(
                    "Discovered: %d, online: %d, connected: %d, seeds: %d (%d), leeches: %d (%d)",
                    peerInfos.size(),
                    onlinePeerCount,
                    connectedPeerCount,
                    connectedSeedCount,
                    onlineSeedCount,
                    connectedLeachCount,
                    onlineLeachCount
            ));

            final boolean dataWorkerOverload = sessionState.isDataWorkerOverload();

            graphics.putString(0, ++row,
                    "---------- Peer   Source Attempt  State   Have Piece R C W" + (showDownload
                            ? " ------------ Download"
                            : " -------------- Upload")
            );
            final List<PeerRecord> peerRecords = getPeerRecords(sessionState);
            for (int i = 0; i < peerRecords.size() && i < 15 && row < graphics.getSize().getRows() + 1; i++) {
                final PeerRecord peerRecord = peerRecords.get(i);
                graphics.putString(0, ++row,
                        getPeerRecordString(currentTimeMillis, peerRecord, dataWorkerOverload, showDownload)
                );
            }

            while (row < graphics.getSize().getRows() - 1) {
                fillLine(++row, ' ');
            }

            // might use RefreshType.DELTA, but it does not tolerate resizing of the window
            screen.refresh(Screen.RefreshType.COMPLETE);

            if (LOGGER.isDebugEnabled()) {
                if (complete) {
                    LOGGER.debug(String.format(LOG_ENTRY_SEED,
                            peerCount, getDisplayString(RATE_UNIT_FORMAT, uploadRate, units, false)
                    ));
                } else {
                    LOGGER.debug(String.format(LOG_ENTRY,
                            peerCount,
                            getDisplayString(RATE_UNIT_FORMAT, downloadRate, units, false),
                            getDisplayString(RATE_UNIT_FORMAT, uploadRate, units, false),
                            completePercents,
                            remainingTime
                    ));
                }
            }

            this.downloaded = downloaded;
            this.uploaded = uploaded;

        } catch (Throwable e) {
            LOGGER.error("Unexpected error when printing session state", e);
            shutdown();
        }
    }

    private void fillLine(int row, char character) {
        graphics.drawLine(0, row, graphics.getSize().getColumns() - 1, row, character);
    }

    private int getSeeds(Collection<PeerInfoView> peerInfos) {
        int result = 0;
        for (PeerInfoView peerInfo : peerInfos) {
            if (peerInfo.getPieces() == peerInfo.getPiecesTotal()) {
                result++;
            }
        }
        return result;
    }

    private String getElapsedTime(long currentTimeMillis) {
        Duration elapsed = Duration.ofMillis(currentTimeMillis - started);
        return formatDuration(elapsed);
    }

    private String getRemainingTime(long downloaded, int piecesRemaining, int piecesTotal) {
        String remainingStr;
        if (piecesRemaining == 0) {
            remainingStr = "-" + WHITESPACES;
        } else if (downloaded == 0 || !torrent.isPresent()) {
            remainingStr = "\u221E" + WHITESPACES; // infinity
        } else {
            long size = torrent.get().getSize();
            double remaining = piecesRemaining / ((double) piecesTotal);
            long remainingBytes = (long) (size * remaining);
            Duration remainingTime = Duration.ofSeconds(remainingBytes / downloaded);
            // overwrite trailing chars with whitespaces if there are any
            remainingStr = formatDuration(remainingTime) + WHITESPACES;
        }
        return remainingStr;
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format("%d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

    private String getProgressBar(double completePercents, double requiredPercents) throws IOException {
        int completeInt = (int) completePercents;
        int requiredInt = (int) requiredPercents;

        int width = graphics.getSize().getColumns() - 25;
        if (width < 0) {
            return "Progress: " + completeInt + "% (req.: " + requiredInt + "%)";
        }

        String s = "Progress: [%-" + width + "s] %d%%";
        char[] bar = new char[width];
        double shrinkFactor = width / 100d;
        int bound = (int) (completeInt * shrinkFactor);
        Arrays.fill(bar, 0, bound, '#');
        Arrays.fill(bar, bound, bar.length, ' ');
        if (completeInt != requiredInt && requiredInt != 100) {
            final int i = (int) (requiredInt * shrinkFactor);
            bar[i] = '|';
        }
        return String.format(s, String.valueOf(bar), completeInt);
    }

    private static final char[] CHARS = {' ', 'o', '8', '%', '#'};

    private String getPiecesBar(BitSet pieces, int length, double ratio) {
        final int width = graphics.getSize().getColumns() - 25;
        if (width < 0) {
            return String.format("Pieces: %d / %d", pieces.cardinality(), length);
        }
        final char[] chars = new char[width];
        for (int i = 0; i < width; ++i) {
            final int head = i * length / width;
            final int tail = (i + 1) * length / width;
            final boolean includeTail = head == tail || (i + 1) * length % width > 0;
            int emptyCount = 0;
            int fullCount = 0;
            for (int bit = head; bit < tail; ++bit) {
                if (pieces.get(bit)) {
                    fullCount++;
                } else {
                    emptyCount++;
                }
            }
            if (includeTail) {
                if (pieces.get(tail)) {
                    fullCount++;
                } else {
                    emptyCount++;
                }
            }
            final int totalCount = emptyCount + fullCount;
            assert totalCount > 0;
            int c = fullCount * (CHARS.length - 1) / totalCount;
            if (c == 0 && fullCount > 0) {
                assert CHARS.length >= 2;
                c = 1;
            }
            chars[i] = CHARS[c];
        }
        return String.format("Pieces:   [%s] %.2f", String.valueOf(chars), StrictMath.floor(ratio * 100.0) / 100.0);
    }

    private double getCompletePercentage(int total, int completed) {
        return completed / ((double) total) * 100;
    }

    private double getTargetPercentage(int total, int completed, int remaining) {
        return (completed + remaining) / ((double) total) * 100;
    }

    private void clearScreen() {
        try {
            this.screen.clear();
            this.screen.refresh();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void pause() {
        if (supressOutput) {
            return;
        }
        this.supressOutput = true;
        clearScreen();
    }

    public synchronized void resume() {
        if (!supressOutput) {
            return;
        }
        this.supressOutput = false;
        clearScreen();
    }

    private List<PeerRecord> getPeerRecords(TorrentSessionState sessionState) {
        final Set<Peer> timeoutedPeers = sessionState.getTimeoutedPeers();
        final Collection<PeerInfoView> onlinePeerInfos = sessionState.getOnlinePeerInfos();
        return onlinePeerInfos.stream().map(peerInfo -> new PeerRecord(peerInfo,
                timeoutedPeers.contains(peerInfo.getPeer()),
                sessionState.isEncryptedConnection(peerInfo.getPeer()),
                sessionState.getConnectionState(peerInfo.getPeer())
        )).sorted((o1, o2) -> {
            int cmp = -Long.compare(o1.getDownload() + o1.getUpload(), o2.getDownload() + o2.getUpload());
            if (cmp != 0) {
                return cmp;
            }
            cmp = -Integer.compare(o1.getPieces(), o2.getPieces());
            if (cmp != 0) {
                return cmp;
            }
            cmp = compareInetAddress(o1.getInetAddress(), o2.getInetAddress());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(o1.getPort(), o2.getPort());
            return cmp;
        }).collect(Collectors.toList());
    }

    private String getPeerRecordString(@SuppressWarnings("UnusedParameters") long currentTimeMillis,
                                       PeerRecord peerRecord,
                                       boolean dataWorkerOverload,
                                       boolean showDownload) {
        final AtomicLong previousDownload =
                peerDownloadMap.computeIfAbsent(peerRecord.getPeer(), it -> new AtomicLong());
        final long download = peerRecord.getDownload();
        final long downloadDelta = download - previousDownload.get();
        previousDownload.set(download);

        final AtomicLong previousUpload = peerUploadMap.computeIfAbsent(peerRecord.getPeer(), it -> new AtomicLong());
        final long upload = peerRecord.getUpload();
        final long uploadDelta = upload - previousUpload.get();
        previousUpload.set(upload);
        return String.format("%15s %s %4s %2d %s %5.1f%% %5s %1s %1s %1s %9s %11s",
                peerRecord.getInetAddress().getHostAddress(),
                //peerRecord.getPeerInfo().getPeer().getPort(),//" %5s"
                getSourceFlags(peerRecord),
                peerRecord.getConnectAt() == null ? "" : (peerRecord.getConnectAt() - currentTimeMillis + 500L) / 1000L,
                peerRecord.getConnectAttempts(),
                getConnectionFlags(peerRecord),
                peerRecord.getPieces() * 100.0 / peerRecord.getPiecesTotal(),
                getDisplayValue(peerRecord.getCurrentPiece(), peerRecord.getPiecesTotal(), false, ""),
                getDisplayValue(peerRecord.getPendingRequests(), 9, true, dataWorkerOverload ? "X" : ""),
                getDisplayValue(peerRecord.getCancelledPeerRequests(), 9, true, ""),
                getDisplayValue(peerRecord.getPendingWrites(), 9, true, ""),
                showDownload
                        ? getDisplayString(AMOUNT_UNIT_FORMAT, download, units)
                        : getDisplayString(AMOUNT_UNIT_FORMAT, upload, units),
                showDownload
                        ? getDisplayString(RATE_UNIT_FORMAT, downloadDelta, units)
                        : getDisplayString(RATE_UNIT_FORMAT, uploadDelta, units)
        );
    }

    private String getDisplayValue(Integer value, int maxValue, boolean hideZero, String zerro) {
        return value == null ? "" : value == 0 && hideZero ? zerro : value > maxValue ? "^" : String.valueOf(value);
    }

    private String getSourceFlags(PeerRecord peerRecord) {
        final StringBuilder sb = new StringBuilder();
        final Set<PeerSourceType> peerSourceTypes = peerRecord.getPeerSourceTypes();
        sb.append(peerSourceTypes.contains(PeerSourceType.MANUAL) ? 'X' : '.');
        sb.append(peerSourceTypes.contains(PeerSourceType.INCOMING) ? 'I' : '.');
        sb.append(peerSourceTypes.contains(PeerSourceType.MAGNET) ? 'M' : '.');
        sb.append(peerSourceTypes.contains(PeerSourceType.TRACKER) ? 'T' : '.');
        sb.append(peerSourceTypes.contains(PeerSourceType.PEX) ? 'E' : '.');
        sb.append(peerSourceTypes.contains(PeerSourceType.DHT) ? 'H' : '.');
        sb.append(peerSourceTypes.contains(PeerSourceType.LSD) ? 'L' : '.');
        sb.append(peerSourceTypes.contains(PeerSourceType.UNKNOWN) ? 'U' : '.');
        return sb.toString();
    }

    private String getConnectionFlags(PeerRecord peerRecord) {
        final StringBuilder sb = new StringBuilder();
        final PeerState peerState = peerRecord.getPeerState();
        switch (peerState) {
            case DISCONNECTED:
                sb.append('D');
                break;
            case CONNECTING:
                sb.append('C');
                break;
            case ACTIVE:
                sb.append(Boolean.TRUE.equals(peerRecord.isEncryptedConnection()) ? 'E' : 'A');
                break;
            default:
                throw new RuntimeException(String.format("Unsupported peerState: '%s'", peerState));
        }
        sb.append(peerRecord.isTimeouted() ? 'T' : '.');
        //sb.append(' ');
        sb.append(Boolean.TRUE.equals(peerRecord.isChoking()) ? 'C' : '.');
        sb.append(Boolean.TRUE.equals(peerRecord.isInterested()) ? 'I' : '.');
        sb.append(Boolean.TRUE.equals(peerRecord.isPeerInterested()) ? 'i' : '.');
        sb.append(Boolean.TRUE.equals(peerRecord.isPeerChoking()) ? 'c' : '.');
        return sb.toString();
    }
}
