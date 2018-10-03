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

package org.filesys.server.auth.spnego;

import java.io.IOException;

import org.filesys.server.auth.SecurityBlob;
import org.filesys.server.auth.asn.DER;


/**
 * SPNEGO Class
 *
 * <p>
 * Contains SPNEGO constants
 *
 * @author gkspencer
 */
public class SPNEGO {

    // Message types
    public static final int NegTokenInit = 0;
    public static final int NegTokenTarg = 1;

    // NegTokenInit context flags
    public static final int ContextDelete   = 0;
    public static final int ContextMutual   = 1;
    public static final int ContextReplay   = 2;
    public static final int ContextSequence = 3;
    public static final int ContextAnon     = 4;
    public static final int ContextConf     = 5;
    public static final int ContextInteg    = 6;

    // NegTokenTarg result codes
    public enum Result {
        AcceptCompleted     (0),
        AcceptIncomplete    (1),
        Reject              (2),

        Invalid             (-1);

        private final int result;

        /**
         * Enum constructor
         *
         * @param ival int
         */
        Result(int ival) { result = ival; }

        /**
         * Return the result as an int
         *
         * @return int
         */
        public final int intValue() { return result; }

        /**
         * Create a Result from an int
         *
         * @param ival int
         * @return Result
         */
        public static final Result fromInt(int ival) {
            Result res = Invalid;

            switch ( ival) {
                case 0:
                    res = AcceptCompleted;
                    break;
                case 1:
                    res = AcceptIncomplete;
                    break;
                case 2:
                    res = Reject;
                    break;
            }

            return res;
        }
    }

    /**
     * Determine the SPNEGO token type
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     * @return int
     * @exception IOException Error decoding the token
     */
    public static int checkTokenType(byte[] buf, int off, int len)
            throws IOException {

        // Check the initial byte of the buffer
        if (DER.isApplicationSpecific(buf[off]))
            return NegTokenInit;
        else if (DER.isTagged(buf[off]))
            return NegTokenTarg;
        else
            return -1;
    }

    /**
     * Determine the SPNEGO token type
     *
     * @param secBlob SecurityBlob
     * @return int
     * @exception IOException Error decoding the token
     */
    public static int checkTokenType(SecurityBlob secBlob)
            throws IOException {

        // Check the initial byte of the buffer
        int typ = (int) secBlob.getSecurityBlob()[ secBlob.getSecurityOffset()];

        if (DER.isApplicationSpecific( typ))
            return NegTokenInit;
        else if (DER.isTagged( typ))
            return NegTokenTarg;
        else
            return -1;
    }
}
