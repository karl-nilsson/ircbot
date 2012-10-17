package com.brewtab.irc.protocol;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import com.brewtab.irc.messages.IRCMessage;

/**
 * Encode an IRCMessage or IRCMessage[] into a String
 * 
 * @author Christopher Thunes <cthunes@brewtab.com>
 */
public class IRCMessageEncoder extends OneToOneEncoder {
    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) {
        if (msg instanceof IRCMessage) {
            IRCMessage message = (IRCMessage) msg;
            return message.toString();
        } else if (msg instanceof IRCMessage[]) {
            IRCMessage[] messages = (IRCMessage[]) msg;
            StringBuffer buffer = new StringBuffer();

            for (IRCMessage message : messages) {
                buffer.append(message.toString());
            }

            return buffer.toString();
        } else {
            throw new IllegalArgumentException("msg must be one of IRCMessage or IRCMessage[]");
        }
    }
}
