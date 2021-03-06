 /*
 * Copyright 2009, Mahmood Ali.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Mahmood Ali. nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package indi.isnow.japns.service;

import indi.isnow.japns.exceptions.DeliveryError;
import indi.isnow.japns.exceptions.NetworkIOException;
import indi.isnow.japns.notification.ApnsNotification;
import indi.isnow.japns.notification.ApnsNotificationFrame;
import indi.isnow.japns.notification.EnhancedApnsNotification;
import indi.isnow.japns.util.Utilities;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApnsConnectionImpl implements ApnsConnection {

    private static final Logger logger = LoggerFactory.getLogger(ApnsConnectionImpl.class);

    private final SocketFactory factory;
    private final String host;
    private final int port;
    private final int readTimeout;
    private final int connectTimeout;
    private final Proxy proxy;
    private final String proxyUsername;
    private final String proxyPassword;
    private final ReconnectPolicy reconnectPolicy;
    private int cacheLength;
    private final boolean errorDetection;
    private final ThreadFactory threadFactory;
    private final boolean autoAdjustCacheLength;
    private final ConcurrentLinkedQueue<ApnsNotification> cachedNotifications, notificationsBuffer;
    private Socket socket;
    private final AtomicInteger threadId = new AtomicInteger(0);

    public ApnsConnectionImpl(SocketFactory factory, String host, int port) {
        this(factory, host, port, new ReconnectPolicies.Never());
    }

    private ApnsConnectionImpl(SocketFactory factory, String host, int port, ReconnectPolicy reconnectPolicy) {
        this(factory, host, port, null, null, null, reconnectPolicy);
    }

    private ApnsConnectionImpl(SocketFactory factory, String host, int port, Proxy proxy, String proxyUsername, String proxyPassword,
                               ReconnectPolicy reconnectPolicy) {
        this(factory, host, port, proxy, proxyUsername, proxyPassword, reconnectPolicy, false, null,
                ApnsConnection.DEFAULT_CACHE_LENGTH, true, 0, 0);
    }

    public ApnsConnectionImpl(SocketFactory factory, String host, int port, Proxy proxy, String proxyUsername, String proxyPassword,
                              ReconnectPolicy reconnectPolicy, boolean errorDetection, ThreadFactory tf, int cacheLength,
                              boolean autoAdjustCacheLength, int readTimeout, int connectTimeout) {
        this.factory = factory;
        this.host = host;
        this.port = port;
        this.reconnectPolicy = reconnectPolicy;
        this.proxy = proxy;
        this.errorDetection = errorDetection;
        this.threadFactory = tf == null ? defaultThreadFactory() : tf;
        this.cacheLength = cacheLength;
        this.autoAdjustCacheLength = autoAdjustCacheLength;
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
        cachedNotifications = new ConcurrentLinkedQueue<ApnsNotification>();
        notificationsBuffer = new ConcurrentLinkedQueue<ApnsNotification>();
    }

    private ThreadFactory defaultThreadFactory() {
        return new ThreadFactory() {
            ThreadFactory wrapped = Executors.defaultThreadFactory();
            public Thread newThread( Runnable r )
            {
                Thread result = wrapped.newThread(r);
                result.setName("MonitoringThread-"+threadId.incrementAndGet());
                result.setDaemon(true);
                return result;
            }
        };
    }

    public synchronized void close() {
        Utilities.close(socket);
    }

    private void monitorSocket(final Socket socket) {
        logger.debug("Launching Monitoring Thread for socket {}", socket);

        Thread t = threadFactory.newThread(new Runnable() {
            final static int EXPECTED_SIZE = 6;

            public void run() {
                logger.debug("Started monitoring thread");
                try {
                    InputStream in;
                    try {
                        in = socket.getInputStream();
                    } catch (IOException ioe) {
                        in = null;
                    }

                    byte[] bytes = new byte[EXPECTED_SIZE];
                    while (in != null && readPacket(in, bytes)) {
                        logger.debug("Error-response packet {}", Utilities.encodeHex(bytes));
                        // Quickly close socket, so we won't ever try to send push notifications
                        // using the defective socket.
                        Utilities.close(socket);

                        int command = bytes[0] & 0xFF;
                        if (command != 8) {
                            throw new IOException("Unexpected command byte " + command);
                        }
                        int statusCode = bytes[1] & 0xFF;
                        DeliveryError e = DeliveryError.ofCode(statusCode);

                        int id = Utilities.parseBytes(bytes[2], bytes[3], bytes[4], bytes[5]);

                        logger.debug("Closed connection cause={}; id={}", e, id);

                        Queue<ApnsNotification> tempCache = new LinkedList<ApnsNotification>();
                        ApnsNotification notification = null;
                        boolean foundNotification = false;

                        while (!cachedNotifications.isEmpty()) {
                            notification = cachedNotifications.poll();
                            logger.debug("Candidate for removal, message id {}", notification.getIdentifier());

                            if (notification.getIdentifier() == id) {
                                logger.debug("Bad message found {}", notification.getIdentifier());
                                foundNotification = true;
                                break;
                            }
                            tempCache.add(notification);
                        }

                        if (foundNotification) {
                            logger.debug("delegate.messageSendFailed, message id {}", notification.getIdentifier());
                        } else {
                            cachedNotifications.addAll(tempCache);
                            int resendSize = tempCache.size();
                            logger.warn("Received error for message that wasn't in the cache...");
                            if (autoAdjustCacheLength) {
                                cacheLength = cacheLength + (resendSize / 2);
                            }
                        }

                        int resendSize = 0;

                        while (!cachedNotifications.isEmpty()) {

                            resendSize++;
                            final ApnsNotification resendNotification = cachedNotifications.poll();
                            logger.debug("Queuing for resend {}", resendNotification.getIdentifier());
                            notificationsBuffer.add(resendNotification);
                        }
                        logger.debug("resending {} notifications", resendSize);

                        drainBuffer();
                    }
                    logger.debug("Monitoring input stream closed by EOF");

                } catch (IOException e) {
                    // An exception when reading the error code is non-critical, it will cause another retry
                    // sending the message. Other than providing a more stable network connection to the APNS
                    // server we can't do much about it - so let's not spam the application's error log.
                    logger.info("Exception while waiting for error code", e);
                } finally {
                    close();
                }
            }

            /**
             * Read a packet like in.readFully(bytes) does - but do not throw an exception and return false if nothing
             * could be read at all.
             * @param in the input stream
             * @param bytes the array to be filled with data
             * @return true if a packet as been read, false if the stream was at EOF right at the beginning.
             * @throws IOException When a problem occurs, especially EOFException when there's an EOF in the middle of the packet.
             */
            private boolean readPacket(final InputStream in, final byte[] bytes) throws IOException {
                final int len = bytes.length;
                int n = 0;
                while (n < len) {
                    try {
                        int count = in.read(bytes, n, len - n);
                        if (count < 0) {
                            throw new EOFException("EOF after reading "+n+" bytes of new packet.");
                        }
                        n += count;
                    } catch (IOException ioe) {
                        if (n == 0)
                            return false;
                        throw new IOException("Error after reading "+n+" bytes of packet", ioe);
                    }
                }
                return true;
            }
        });
        t.start();
    }

    private synchronized Socket getOrCreateSocket() throws NetworkIOException {
        if (reconnectPolicy.shouldReconnect()) {
            logger.debug("Reconnecting due to reconnectPolicy dictating it");
            Utilities.close(socket);
            socket = null;
        }

        if (socket == null || socket.isClosed()) {
            try {
                if (proxy == null) {
                    socket = factory.createSocket(host, port);
                    logger.debug("Connected new socket {}", socket);
                } else if (proxy.type() == Proxy.Type.HTTP) {
                    TlsTunnelBuilder tunnelBuilder = new TlsTunnelBuilder();
                    socket = tunnelBuilder.build((SSLSocketFactory) factory, proxy, proxyUsername, proxyPassword, host, port);
                    logger.debug("Connected new socket through http tunnel {}", socket);
                } else {
                    boolean success = false;
                    Socket proxySocket = null;
                    try {
                        proxySocket = new Socket(proxy);
                        proxySocket.connect(new InetSocketAddress(host, port), connectTimeout);
                        socket = ((SSLSocketFactory) factory).createSocket(proxySocket, host, port, false);
                        success = true;
                    } finally {
                        if (!success) {
                            Utilities.close(proxySocket);
                        }
                    }
                    logger.debug("Connected new socket through socks tunnel {}", socket);
                }

                socket.setSoTimeout(readTimeout);
                socket.setKeepAlive(true);

                if (errorDetection) {
                    monitorSocket(socket);
                }

                reconnectPolicy.reconnected();
                logger.debug("Made a new connection to APNS");
            } catch (IOException e) {
                logger.error("Couldn't connect to APNS server", e);
                throw new NetworkIOException(e);
            }
        }
        return socket;
    }

    int DELAY_IN_MS = 1000;
    private static final int RETRIES = 3;

    public synchronized void sendMessage(ApnsNotification m) throws NetworkIOException {
        sendMessage(m, false);
        drainBuffer();
    }

    private synchronized void sendMessage(ApnsNotification m, boolean fromBuffer) throws NetworkIOException {
        logger.debug("sendMessage {} fromBuffer: {}", m, fromBuffer);

        int attempts = 0;
        while (true) {
            try {
                attempts++;
                Socket socket = getOrCreateSocket();
                socket.getOutputStream().write(m.marshall());
                socket.getOutputStream().flush();
                cacheNotification(m);

                logger.debug("Message \"{}\" sent", m);
                attempts = 0;
                break;
            } catch (IOException e) {
                Utilities.close(socket);
                if (attempts >= RETRIES) {
                    logger.error("Couldn't send message after " + RETRIES + " retries." + m, e);
                    Utilities.wrapAndThrowAsRuntimeException(e);
                }
                // The first failure might be due to closed connection (which in turn might be caused by
                // a message containing a bad token), so don't delay for the first retry.
                //
                // Additionally we don't want to spam the log file in this case, only after the second retry
                // which uses the delay.

                if (attempts != 1) {
                    logger.info("Failed to send message " + m + "... trying again after delay", e);
                    Utilities.sleep(DELAY_IN_MS);
                }
            }
        }
    }
    
    private synchronized void sendMessages(List<ApnsNotification> ms, boolean fromBuffer) throws NetworkIOException {
        logger.debug("sendMessages {} fromBuffer: {}", ms, fromBuffer);
        
        ApnsNotificationFrame frame = new ApnsNotificationFrame(ms);
        int attempts = 0;
        while (true) {
            try {
                attempts++;
                Socket socket = getOrCreateSocket();
                socket.getOutputStream().write(frame.marshall());
                socket.getOutputStream().flush();
                if (attempts == 0)
                	cacheNotification(ms);

                logger.debug("Messages \"{}\" sent", ms);
                attempts = 0;
                break;
            } catch (IOException e) {
                Utilities.close(socket);
                if (attempts >= RETRIES) {
                    logger.error("Couldn't send message after " + RETRIES + " retries." + ms, e);
                    Utilities.wrapAndThrowAsRuntimeException(e);
                }
                // The first failure might be due to closed connection (which in turn might be caused by
                // a message containing a bad token), so don't delay for the first retry.
                //
                // Additionally we don't want to spam the log file in this case, only after the second retry
                // which uses the delay.

                if (attempts != 1) {
                    logger.info("Failed to send message " + ms + "... trying again after delay", e);
                    Utilities.sleep(DELAY_IN_MS);
                }
            }
        }
    }


    private synchronized void drainBuffer() {
        logger.debug("draining buffer");
        while (!notificationsBuffer.isEmpty()) {
            sendMessage(notificationsBuffer.poll(), true);
        }
    }

    private void cacheNotification(ApnsNotification notification) {
        cachedNotifications.add(notification);
        while (cachedNotifications.size() > cacheLength) {
            cachedNotifications.poll();
            logger.debug("Removing notification from cache " + notification);
        }
    }
    
    private void cacheNotification(List<ApnsNotification> notifications) {
        cachedNotifications.addAll(notifications);
        while (cachedNotifications.size() > cacheLength) {
            logger.debug("Removing notification from cache " + cachedNotifications.poll());
        }
    }

    public ApnsConnectionImpl copy() {
        return new ApnsConnectionImpl(factory, host, port, proxy, proxyUsername, proxyPassword, reconnectPolicy.copy(),
                errorDetection, threadFactory, cacheLength, autoAdjustCacheLength, readTimeout, connectTimeout);
    }

    public void testConnection() throws NetworkIOException {
        ApnsConnectionImpl testConnection = null;
        try {
            testConnection =
                    new ApnsConnectionImpl(factory, host, port, proxy, proxyUsername, proxyPassword, reconnectPolicy.copy());
            final ApnsNotification notification = new EnhancedApnsNotification(0, 0, new byte[]{0}, new byte[]{0});
            testConnection.sendMessage(notification);
        } finally {
            if (testConnection != null) {
                testConnection.close();
            }
        }
    }

    public void setCacheLength(int cacheLength) {
        this.cacheLength = cacheLength;
    }

    public int getCacheLength() {
        return cacheLength;
    }
}
