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

package org.filesys.server.auth.ntlm;

/**
 * NTLM Constants Class
 *
 * @author gkspencer
 */
public class NTLM {

    // Signature
    public static final byte[] Signature = "NTLMSSP\u0000".getBytes();

    // NTLM flags
    public static final int FlagNegotiateUnicode        = 0x00000001;
    public static final int FlagNegotiateOEM            = 0x00000002;
    public static final int FlagRequestTarget           = 0x00000004;
    public static final int FlagNegotiateSign           = 0x00000010;
    public static final int FlagNegotiateSeal           = 0x00000020;
    public static final int FlagDatagramStyle           = 0x00000040;
    public static final int FlagLanManKey               = 0x00000080;
    public static final int FlagNegotiateNetware        = 0x00000100;
    public static final int FlagNegotiateNTLM           = 0x00000200;
    public static final int FlagDomainSupplied          = 0x00001000;
    public static final int FlagWorkstationSupplied     = 0x00002000;
    public static final int FlagLocalCall               = 0x00004000;
    public static final int FlagAlwaysSign              = 0x00008000;
    public static final int FlagChallengeInit           = 0x00010000;
    public static final int FlagChallengeAccept         = 0x00020000;
    public static final int FlagChallengeNonNT          = 0x00040000;
    public static final int FlagNegotiateExtSecurity    = 0x00080000;
    public static final int FlagTargetInfo              = 0x00800000;
    public static final int FlagRequestVersion          = 0x02000000;
    public static final int Flag128Bit                  = 0x20000000;
    public static final int FlagKeyExchange             = 0x40000000;
    public static final int Flag56Bit                   = 0x80000000;

    // Signing key constants
    public static final byte[] SERVER_SIGNING_KEY_CONST = "session key to server-to-client signing key magic constant\u0000".getBytes();
    public static final byte[] CLIENT_SIGNING_KEY_CONST = "session key to client-to-server signing key magic constant\u0000".getBytes();

    // Signing key constants
    public static final byte[] SERVER_SEALING_KEY_CONST = "session key to server-to-client sealing key magic constant\u0000".getBytes();
    public static final byte[] CLIENT_SEALING_KEY_CONST = "session key to client-to-server sealing key magic constant\u0000".getBytes();

    /**
     * Check if the specified flag is set
     *
     * @param flags int
     * @param flag int
     * @return boolean
     */
    public static final boolean hasFlag(int flags, int flag) {
        return ( flags & flag) != 0 ? true : false;
    }
}
