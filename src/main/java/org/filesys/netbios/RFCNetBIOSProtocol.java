/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.netbios;

/**
 * RFC NetBIOS constants.
 *
 * @author gkspencer
 */
public final class RFCNetBIOSProtocol {

    //  RFC NetBIOS default port/socket
    public static final int SESSION = 139;

    // 	RFC NetBIOS datagram port
    public static final int DATAGRAM= 138;

    //  RFC NetBIOS default name lookup datagram port
    public static final int NAMING  = 137;

    //  RFC NetBIOS default socket timeout
    public static final int TMO = 30000;    // 30 seconds, in milliseconds

    //  RFC NetBIOS message types
    public enum MsgType {
        MESSAGE     (0x00),
        REQUEST     (0x81),
        ACK         (0x82),
        REJECT      (0x83),
        RETARGET    (0x84),
        KEEPALIVE   (0x85),

        INVALID     (-1);

        private final int msgType;

        /**
         * Enum constructor
         *
         * @param typ int
         */
        MsgType(int typ) { msgType = typ; }

        /**
         * Return the message type as an int
         *
         * @return int
         */
        public final int intValue() { return msgType; }

        /**
         * Convert from an int to a MsgType
         *
         * @param typ int
         * @return MsgType
         */
        public final static MsgType fromInt(int typ) {
            MsgType msgType = INVALID;

            switch(typ) {
                case 0x00:
                    msgType = MESSAGE;
                    break;
                case 0x81:
                    msgType = REQUEST;
                    break;
                case 0x82:
                    msgType = ACK;
                    break;
                case 0x83:
                    msgType = REJECT;
                    break;
                case 0x84:
                    msgType = RETARGET;
                    break;
                case 0x085:
                    msgType = KEEPALIVE;
                    break;
            }

            return msgType;
        }
    }

    //  RFC NetBIOS packet header length, and various message lengths.
    public static final int HEADER_LEN          = 4;
    public static final int SESSREQ_LEN         = 72;
    public static final int SESSRESP_LEN        = 9;

    //	Maximum packet size that RFC NetBIOS can handle (17bit value)
    public static final int MaxPacketSize = 0x01FFFF + HEADER_LEN;
}
