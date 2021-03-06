/*
 * Copyright (c) 2013 Christopher Thunes
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.brewtab.irc.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;

import com.brewtab.irc.User;
import com.brewtab.irc.client.Channel;
import com.brewtab.irc.client.Client;
import com.brewtab.irc.client.ClientFactory;

public class Log4jAppender extends AppenderSkeleton {
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private BlockingQueue<LogLine> buffer = new PriorityBlockingQueue<LogLine>();

    private Client client;
    private Thread background;
    private volatile boolean running = true;

    private String url;
    private String nick = "log4j";
    private String localhost;
    private String quitMessage = "brewtab IRC log4j appender quiting";
    private String target;

    private static class LogLine implements Comparable<LogLine> {
        private Long timestamp;
        private String line;

        public LogLine(long timestamp, String line) {
            this.timestamp = timestamp;
            this.line = line;
        }

        public String getLine() {
            return line;
        }

        @Override
        public int compareTo(LogLine o) {
            return timestamp.compareTo(o.timestamp);
        }
    }

    private class ChatRunner implements Runnable {
        private String nick;

        public ChatRunner(String nick) {
            this.nick = nick;
        }

        @Override
        public void run() {
            initClient();

            User user = new User(nick);

            while (running) {
                final LogLine line;

                try {
                    line = buffer.take();
                } catch (InterruptedException e) {
                    continue;
                }

                client.sendMessage(user, line.getLine());
            }
        }
    }

    private class ChannelRunner implements Runnable {
        private String channelName;

        public ChannelRunner(String channelName) {
            this.channelName = channelName;
        }

        @Override
        public void run() {
            initClient();

            final Channel channel = client.join(channelName);

            while (running) {
                final LogLine line;

                try {
                    line = buffer.take();
                } catch (InterruptedException e) {
                    continue;
                }

                channel.write(line.getLine());
            }

            channel.part(quitMessage);
        }
    }

    @Override
    public void close() {
        running = false;
        background.interrupt();

        try {
            background.join();
        } catch (InterruptedException e) {
            // --
        }
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

    private void initClient() {
        if (localhost == null) {
            try {
                localhost = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                localhost = "localhost";
            }
        }

        ClientFactory clientFactory = ClientFactory.newInstance();
        clientFactory.setNick(nick);
        clientFactory.setUsername("log4j");
        clientFactory.setHostname(localhost);
        clientFactory.setRealName("Brewtab IRC log4j appender");

        client = clientFactory.connect(url);
    }

    private void init() {
        final Runnable runner;

        if (target.charAt(0) == '#') {
            runner = new ChannelRunner(target);
        } else {
            runner = new ChatRunner(target);
        }

        background = new Thread(runner);
        background.setName("log4j-irc-appender");
        background.setDaemon(true);
        background.start();
    }

    @Override
    protected void append(LoggingEvent event) {
        /*
         * We have to explicitly avoid logging anything at a DEBUG of TRACE
         * level that originates from within the IRC library itself or we risk
         * recursing infinitely.
         */
        if (event.getLevel().toInt() <= Level.DEBUG_INT) {
            String caller = event.getLocationInformation().getClassName();

            if (caller.startsWith("com.brewtab.irc.impl.")) {
                return;
            }
        }

        if (!initialized.getAndSet(true)) {
            init();
        }

        String line = getLayout().format(event);
        buffer.offer(new LogLine(event.getTimeStamp(), line));
    }

    public void setQuitMessage(String quitMessage) {
        this.quitMessage = quitMessage;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setTarget(String target) {
        this.target = target.trim();
    }

    public void setLocalhost(String hostname) {
        this.localhost = hostname;
    }
}
