/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
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

package org.filesys.util;

import java.net.InetAddress;
import java.util.StringTokenizer;

/**
 * TCP/IP Address Utility Class
 *
 * @author gkspencer
 */
public class IPAddress {

    /**
     * Check if the specified address is a valid numeric TCP/IP address
     *
     * @param ipaddr String
     * @return boolean
     */
    public final static boolean isNumericAddress(String ipaddr) {

        //	Check if the string is valid
        if (ipaddr == null || ipaddr.length() < 7 || ipaddr.length() > 15)
            return false;

        //	Check the address string, should be n.n.n.n format
        StringTokenizer token = new StringTokenizer(ipaddr, ".");
        if (token.countTokens() != 4)
            return false;

        while (token.hasMoreTokens()) {

            //	Get the current token and convert to an integer value
            String ipNum = token.nextToken();

            try {
                int ipVal = Integer.valueOf(ipNum).intValue();
                if (ipVal < 0 || ipVal > 255)
                    return false;
            }
            catch (NumberFormatException ex) {
                return false;
            }
        }

        //	Looks like a valid IP address
        return true;
    }

    /**
     * Check if the specified address is a valid numeric TCP/IP address and return as an integer value
     *
     * @param ipaddr String
     * @return int
     */
    public final static int parseNumericAddress(String ipaddr) {

        //	Check if the string is valid
        if (ipaddr == null || ipaddr.length() < 7 || ipaddr.length() > 15)
            return 0;

        //	Check the address string, should be n.n.n.n format
        StringTokenizer token = new StringTokenizer(ipaddr, ".");
        if (token.countTokens() != 4)
            return 0;

        int ipInt = 0;

        while (token.hasMoreTokens()) {

            //	Get the current token and convert to an integer value
            String ipNum = token.nextToken();

            try {

                //	Validate the current address part
                int ipVal = Integer.valueOf(ipNum).intValue();
                if (ipVal < 0 || ipVal > 255)
                    return 0;

                //	Add to the integer address
                ipInt = (ipInt << 8) + ipVal;
            }
            catch (NumberFormatException ex) {
                return 0;
            }
        }

        //	Return the integer address
        return ipInt;
    }

    /**
     * Convert an IP address into an integer value
     *
     * @param ipaddr InetAddress
     * @return int
     */
    public final static int asInteger(InetAddress ipaddr) {

        //	Get the address as an array of bytes
        byte[] addrBytes = ipaddr.getAddress();

        //	Build an integer value from the bytes
        return DataPacker.getInt(addrBytes, 0);
    }

    /**
     * Check if the specified address is within the required subnet
     *
     * @param ipaddr String
     * @param subnet String
     * @param mask   String
     * @return boolean
     */
    public final static boolean isInSubnet(String ipaddr, String subnet, String mask) {

        //	Convert the addresses to integer values
        int ipaddrInt = parseNumericAddress(ipaddr);
        if (ipaddrInt == 0)
            return false;

        int subnetInt = parseNumericAddress(subnet);
        if (subnetInt == 0)
            return false;

        int maskInt = parseNumericAddress(mask);
        if (maskInt == 0)
            return false;

        //	Check if the address is part of the subnet
        if ((ipaddrInt & maskInt) == subnetInt)
            return true;
        return false;
    }

    /**
     * Convert a raw IP address array as a String
     *
     * @param ipaddr byte[]
     * @return String
     */
    public final static String asString(byte[] ipaddr) {

        //	Check if the address is valid
        if (ipaddr == null || ipaddr.length != 4)
            return null;

        //	Convert the raw IP address to a string
        StringBuffer str = new StringBuffer();

        str.append((int) (ipaddr[0] & 0xFF));
        str.append(".");
        str.append((int) (ipaddr[1] & 0xFF));
        str.append(".");
        str.append((int) (ipaddr[2] & 0xFF));
        str.append(".");
        str.append((int) (ipaddr[3] & 0xFF));

        //	Return the address string
        return str.toString();
    }

    /**
     * Convert a raw IP address array as a String
     *
     * @param ipaddr int
     * @return String
     */
    public final static String asString(int ipaddr) {

        byte[] ipbyts = new byte[4];
        ipbyts[0] = (byte) ((ipaddr >> 24) & 0xFF);
        ipbyts[1] = (byte) ((ipaddr >> 16) & 0xFF);
        ipbyts[2] = (byte) ((ipaddr >> 8) & 0xFF);
        ipbyts[3] = (byte) (ipaddr & 0xFF);

        return asString(ipbyts);
    }

    /**
     * Convert a TCP/IP address string into a byte array
     *
     * @param addr String
     * @return byte[]
     */
    public final static byte[] asBytes(String addr) {

        // Convert the TCP/IP address string to an integer value
        int ipInt = parseNumericAddress(addr);
        if (ipInt == 0)
            return null;

        // Convert to bytes
        byte[] ipByts = new byte[4];

        ipByts[3] = (byte) (ipInt & 0xFF);
        ipByts[2] = (byte) ((ipInt >> 8) & 0xFF);
        ipByts[1] = (byte) ((ipInt >> 16) & 0xFF);
        ipByts[0] = (byte) ((ipInt >> 24) & 0xFF);

        // Return the TCP/IP bytes
        return ipByts;
    }
}
