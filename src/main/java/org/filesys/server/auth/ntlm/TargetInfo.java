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
}
