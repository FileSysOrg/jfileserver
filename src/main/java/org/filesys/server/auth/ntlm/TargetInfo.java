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

package org.filesys.server.auth.ntlm;

import org.filesys.util.DataBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Target Information Class
 *
 * <p>Contains the target information from an NTLM message.
 *
 * @author gkspencer
 */
public abstract class TargetInfo {

    // Target information types
    public enum Type {
        END_OF_LIST     ( 0x0000),
        SERVER          ( 0x0001),
        DOMAIN          ( 0x0002),
        FULL_DNS        ( 0x0003),
        DNS_DOMAIN      ( 0x0004),
        DNS_TREE        ( 0x0005),
        FLAGS           ( 0x0006),
        TIMESTAMP       ( 0x0007),
        SINGLE_HOST     ( 0x0008),
        SPN             ( 0x0009),
        CHANNEL_BINDING ( 0x000A);

        private final int infoType;

        /**
         * Enum constructor
         *
         * @param typ int
         */
        Type( int typ) {
            infoType = typ;
        }

        /**
         * Return the enum value as an int
         *
         * @return int
         */
        public final int intValue() { return infoType; }

        /**
         * Convert an int to an enum value
         *
         * @param iVal int
         * @return Type
         */
        public static Type fromInt( int iVal) {
            if ( iVal < 0 || iVal > 10)
                return END_OF_LIST;
            return Type.values()[ iVal];
        }
    }

    // Target information type
    private Type m_type;

    /**
     * Class constructor
     *
     * @param type TargetInfo.Type
     */
    public TargetInfo(TargetInfo.Type type) {
        m_type = type;
    }

    /**
     * Return the target type
     *
     * @return TargetInfo.Type
     */
    public final TargetInfo.Type isType() {
        return m_type;
    }

    /**
     * Return the target information value as string
     *
     * @return String
     */
    public abstract String valueAsString();

    /**
     * Return the target information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(isType().name());
        str.append(":");
        str.append(valueAsString());
        str.append("]");

        return str.toString();
    }

    /**
     * Pack a target information list into the specified buffer
     *
     * @param tList List&lt;TargetInfo&gt;
     * @param buf DataBuffer
     */
    public static void packInfoList(List<TargetInfo> tList, DataBuffer buf) {

        // Loop through the target information list
        for ( TargetInfo tInfo : tList) {

            // Pack the target information record
            switch ( tInfo.isType()) {

                // String target information
                case SERVER:
                case DOMAIN:
                case FULL_DNS:
                case DNS_DOMAIN:
                case DNS_TREE:
                case SPN:
                    buf.putShort( tInfo.isType().intValue());
                    buf.putShort( tInfo.valueAsString().length() * 2);
                    buf.putString( tInfo.valueAsString(), true, false);
                    break;

                // Timestamp type target information
                case TIMESTAMP:
                    TimestampTargetInfo timeInfo = (TimestampTargetInfo) tInfo;
                    buf.putShort( tInfo.isType().intValue());
                    buf.putShort( 8);
                    buf.putLong( timeInfo.getValue());
                    break;

                // Integer type target information
                case FLAGS:
                    FlagsTargetInfo flagInfo = (FlagsTargetInfo) tInfo;
                    buf.putShort( tInfo.isType().intValue());
                    buf.putShort( 4);
                    buf.putInt( flagInfo.getValue());
                    break;
            }
        }

        // Add the end of list marker
        buf.putShort( Type.END_OF_LIST.intValue());
        buf.putShort( 0);
    }

    /**
     * Unpack a target information list from the specified buffer
     *
     * @param tBuf DataBuffer
     * @param offset int
     * @return List&lt;TargetInfo&gt;
     */
    public static List<TargetInfo> unpackInfoList( DataBuffer tBuf, int offset) {

        // Unpack the target information list from the specified buffer
        List<TargetInfo> tList = null;
        boolean endOfList = false;

        while ( !endOfList) {

            // Get the current target information type and data length
            TargetInfo.Type tTyp = TargetInfo.Type.fromInt( tBuf.getShort());
            int tLen = tBuf.getShort();

            if ( tTyp == TargetInfo.Type.END_OF_LIST) {
                endOfList = true;
                continue;
            }

            // Get the target information value
            TargetInfo tInfo = null;

            switch ( tTyp) {

                // String type target information
                case SERVER:
                case DOMAIN:
                case FULL_DNS:
                case DNS_DOMAIN:
                case DNS_TREE:
                case SPN:
                    String sVal = tBuf.getFixedString( tLen/2, true);
                    tInfo = new StringTargetInfo( tTyp, sVal);
                    break;

                // Timestamp type target information
                case TIMESTAMP:
                    long lVal = tBuf.getLong();
                    tInfo = new TimestampTargetInfo( lVal);
                    break;

                // Integer type target information
                case FLAGS:
                    int iVal = tBuf.getInt();
                    tInfo = new FlagsTargetInfo( iVal);
                    break;

                // Unsupported types
                case CHANNEL_BINDING:
                case SINGLE_HOST:
                    tBuf.skipBytes( tLen);
                    break;
            }

            // Add the target information to the list if valid
            if ( tInfo != null) {
                if ( tList == null)
                    tList = new ArrayList<>();
                tList.add( tInfo);
            }
        }

        // Return the target information list
        return tList;
    }

    /**
     * Find the specified target information type in a list
     *
     * @param tList List&lt;TargetInfo&gt;
     * @param typ TargetInfo.Type
     * @return TargetInfo
     */
    public static TargetInfo findTypeInList( List<TargetInfo> tList, TargetInfo.Type typ) {
        if ( tList == null)
            return null;

        for ( TargetInfo tInfo : tList) {
            if ( tInfo.isType() == typ)
                return tInfo;
        }

        return null;
    }
}
