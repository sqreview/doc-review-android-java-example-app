package com.signalquest.example;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.signalquest.api.NtripParser;
import com.signalquest.api.NtripParser.AuthorizationFailure;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Queue;

/**
 * NTRIP Service connector.
 * <p>
 * Connects to an NTRIP service, parses the authorization header, and listens for RTCM messages. Will
 * also, optionally, send the current phone position as a GGA message, on a timer.
 * <p>
 * The {@link #connect(NtripService)} kicks off the normal flow (managed by a state machine) of:
 * {@link #startAiding()}, {@link #handleAuthorized(byte[])}, followed by multiple calls to
 * {@link NtripParser#parseRtcm(byte[])}, with data available using {@link NtripParser#next(int)}.
 */
class Ntrip {
    private final static int GGA_INTERVAL_MILLISECONDS = 5000;
    public enum State { IDLE, CONNECTING, AUTHORIZING, ACTIVE }
    public State getState() {
        return _state;
    }
    private State _state = State.IDLE;
    private final static String LOG_TAG = "NTRIP";
    private NtripService ntripService;
    private final NtripParser parser;
    private NtripGga gga = null;
    private Handler ggaHandler;
    private Server ntripServer;
    static final String NTRIP_DISCONNECT_ACTION = "com.signalquest.example.NTRIP_DISCONNECT_ACTION";

    private final ServerListener serverListener = new ServerListener() {
        @Override
        public void handleData(byte[] data) {
            StringBuilder sb = new StringBuilder();
            for (byte b : data) { sb.append(String.format("%02X ", b)); }
            Log.d(LOG_TAG, "RTCM timing to parse , " + sb);

            switch (_state) {
                case AUTHORIZING:
                    Log.d(LOG_TAG, "Parsing authorized");
                    handleAuthorized(data);
                    break;
                case ACTIVE:
                    Log.d(LOG_TAG, "Parsing incoming RTCM");
                    parser.parseRtcm(data);
                    App.onParsed();
                    break;
                default:
                    String str = new String(data);
                    Log.w(LOG_TAG, "Unhandled state " + _state.name() + ", data: " + str.length() + " bytes: (" + str + ")");
            }
        }

        @Override
        public void connected() {
            Ntrip.this.startAiding();
        }

        @Override
        public void handleException(Exception e) {
            if (_state != State.IDLE) {
                disconnect();
            }
            App.displayError(LOG_TAG, e.toString(), e);
        }
    };

    /**
     * Sets up the {@link NtripParser}.
     */
    public Ntrip() {
        this.parser = new NtripParser();
    }

    /**
     * Connects to the given NTRIP service.
     */
    public void connect(NtripService service) {
        if (ntripService != null) {
            Log.i(LOG_TAG, ("Disconnecting from " + ntripService));
            disconnect();
        }

        if (service.mountpoint == null || service.mountpoint.isEmpty()) {
            Log.w(LOG_TAG, "This example app does not handle mountpoint listings");
            App.displayError(LOG_TAG, "Mountpoint missing");
            return;
        }

        try {
            ntripService = service;
            ntripServer = new Server(service.server, service.port, serverListener);
            ntripServer.start();
            _state = State.CONNECTING;
        } catch (Exception e) {
            Log.w(LOG_TAG, "Unhandled: unable to connect to server");
            _state = State.IDLE;
        }
    }

    public byte[] next(int maxLength) {
        return parser.next(maxLength);
    }

    /**
     * Disconnect and broadcast for UI; this class will self-disconnect for errors.
     */
    public void disconnect() {
        _state = State.IDLE;
        stopGgaTimer();
        ntripServer.stop();
        ntripService = null;
        broadcastDisconnect();
    }

    private void sendGgaString() {
        if (ntripService != null && ntripService.sendPosition && _state == State.ACTIVE) {
            if (gga == null) {
                gga = new NtripGga();
            }

            String gga = this.gga.toString();
            if (gga.isEmpty()) {
                Log.w(LOG_TAG, "No position to send to NTRIP server");
                return;
            }
            String serverRequest = gga + "\r\n";
            byte[] data = serverRequest.getBytes();
            StringBuilder sb = new StringBuilder();
            for (byte b : data) { sb.append(String.format("%02X ", b)); }
            Log.d(LOG_TAG, "GGA for server , " + sb);
            try {
                ntripServer.write(data);
            } catch (Exception e) {
                App.displayError(LOG_TAG, "Error writing GGA to NTRIP server");
            }
        }
    }

    private final Runnable ggaPoster = new Runnable() {
        @Override
        public void run() {
            if (_state != State.ACTIVE) {
                return;
            }
            sendGgaString();
            ggaHandler.postDelayed(this, GGA_INTERVAL_MILLISECONDS);

        }
    };

    private void startGgaTimer() {
        if (ggaHandler == null) {
            HandlerThread ggaHandlerThread = new HandlerThread("gga");
            ggaHandlerThread.start();
            ggaHandler = new Handler(ggaHandlerThread.getLooper());
        }
        ggaHandler.postDelayed(ggaPoster, GGA_INTERVAL_MILLISECONDS);
    }

    private void stopGgaTimer() {
        if (ggaHandler != null) {
            ggaHandler.removeCallbacks(ggaPoster);
        }
    }

    private static String getSlashedMountpoint(NtripService service) {
        String mountpoint = service.mountpoint;
        return mountpoint.startsWith("/") ? mountpoint : "/" + mountpoint;
    }

    private void startAiding() {
        if (ntripService != null) {
            Log.i(LOG_TAG, ("authorize for " + ntripService));
            _state = State.AUTHORIZING;
            String serverRequest = "GET " + getSlashedMountpoint(ntripService) + " HTTP/1.1\r\nHost: " + ntripService.server + "\r\nAccept: */*\r\nUser-Agent: SignalQuest NTRIP Client/1.0\r\nAuthorization: Basic " + getBasicAuth() + "\r\nConnection: close\r\n\r\n";
            Log.d(LOG_TAG, "Authorization request: " + serverRequest);
            byte[] data = serverRequest.getBytes();
            try {
                ntripServer.write(data);
            } catch (Exception e) {
                App.displayError(LOG_TAG, "Error creating NTRIP aiding server request", e);
                disconnect();
            }
        } else {
            App.displayError(LOG_TAG, "Missing ntripService");
        }
    }

    private String getBasicAuth() {
        String username = ntripService.username;
        String password = ntripService.password;
        String authString = username + ":" + password;
        return Base64.getEncoder().encodeToString(authString.getBytes());
    }

    private void handleAuthorized(byte[] serverResponse) {
        try {
            parser.parseAuthorized(serverResponse);
            Log.d(LOG_TAG, "NTRIP auth success, active");
            _state = State.ACTIVE;
            if (ntripService.sendPosition) {
                // Start a repeating timer to send the latest GGA string (if any known)
                startGgaTimer();
            }
        } catch (AuthorizationFailure e) {
            App.displayError(LOG_TAG, "NTRIP auth failure: (" + e.summary + ": " + e.details);
            disconnect();
        }
    }

    private void broadcastDisconnect() {
        App.getAppContext().sendBroadcast(new Intent(NTRIP_DISCONNECT_ACTION));
    }

    /**
     * Simple server that connects to, reads from, and writes to a socket.
     */
    private static class Server implements Runnable {

        private final Handler handler;
        private final String serverAddress;
        private final int serverPort;
        private SocketChannel socketChannel;
        private Selector selector;
        private volatile boolean running = false;
        private final ServerListener listener;
        Queue<ByteBuffer> writeBuffer = new ArrayDeque<>();

        public Server(String serverAddress, int serverPort, ServerListener listener) {
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
            this.listener = listener;
            HandlerThread thread = new HandlerThread("ntrip server");
            thread.start();
            handler = new Handler(thread.getLooper());
        }

        public void run() {
            try {
                selector = Selector.open();
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                socketChannel.register(selector, SelectionKey.OP_CONNECT);
                InetSocketAddress address = new InetSocketAddress(serverAddress, serverPort);
                socketChannel.connect(address);
                while (running) {
                    selector.select();
                    if (!selector.isOpen()) {
                        break;
                    }
                    for (SelectionKey key : selector.selectedKeys()) {
                        if (key.isConnectable()) {
                            if (socketChannel.isConnectionPending()) {
                                socketChannel.finishConnect();
                                socketChannel.register(selector, SelectionKey.OP_READ);
                                listener.connected();
                            }
                        } else if (key.isReadable()) {
                            ByteBuffer buffer = ByteBuffer.allocate(1024);
                            int bytesRead = socketChannel.read(buffer);
                            if (bytesRead == -1) {
                                throw new RuntimeException("Server has closed the connection");
                            }
                            buffer.flip();
                            byte[] response = new byte[bytesRead];
                            buffer.get(response);
                            listener.handleData(response);
                            buffer.clear();
                        } else if (key.isWritable()) {
                            ByteBuffer buffer = writeBuffer.peek();
                            if (buffer != null) {
                                socketChannel.write(buffer);
                                if (buffer.remaining() == 0) {
                                    writeBuffer.poll();
                                }
                            }
                            if (writeBuffer.isEmpty()) {
                                key.interestOps(SelectionKey.OP_READ);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                listener.handleException(e);
                stop();
            }
        }

        public void start() {
            running = true;
            handler.post(this);
        }

        public void stop() {
            running = false;
            try {
                if (socketChannel != null && socketChannel.isOpen()) {
                    socketChannel.close();
                }
                if (selector != null && selector.isOpen()) {
                    selector.wakeup();
                    selector.close();
                }
            } catch (IOException e) {
                listener.handleException(e);
            }
        }

        public void write(byte[] data) {
            assert(running);
            if (socketChannel != null && socketChannel.isConnected()) {
                writeBuffer.add(ByteBuffer.wrap(data));
                socketChannel.keyFor(selector).interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                selector.wakeup();
            } else {
                listener.handleException(new IOException("Socket channel is not connected"));
            }
        }
    }

    /**
     * Used by the {@link Server} for reporting read data and exceptions, and reporting the connected event.
     */
    private interface ServerListener {
        void handleData(byte[] data);
        void connected();
        void handleException(Exception e);
    }

    public static class NtripService {
        public String server;
        public int port;
        public String mountpoint;
        public boolean sendPosition;
        public String username;
        public String password;

        public NtripService(String server, int port, String username, String password, String mountpoint, boolean sendPosition) {
            this.server = server;
            this.port = port;
            this.username = username;
            this.password = password;
            this.mountpoint = mountpoint;
            this.sendPosition = sendPosition;
        }

        @NonNull
        public String toString() {
            return "NtripService: " + server + ":" + port + getSlashedMountpoint(this);
        }
    }
}



