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

package org.filesys.server.auth;

import java.util.Random;

import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;

/**
 * NTLM1/LanMan SMB Authentication Context Class
 *
 * <p>Holds the challenge sent to the client during the negotiate phase that is used to verify the
 * hashed password in the session setup phase.
 *
 * @author gkspencer
 */
public class NTLanManAuthContext extends ChallengeAuthContext {

    // Random number generator used to generate challenge
    private static Random m_random = new Random(System.currentTimeMillis());

    /**
     * Class constructor
     */
    public NTLanManAuthContext() {

        // Generate a new challenge key, pack the key and return
        m_challenge = new byte[8];
        DataPacker.putIntelLong(m_random.nextLong(), m_challenge, 0);
    }

    /**
     * Class constructor
     *
     * @param challenge byte[]
     */
    public NTLanManAuthContext(byte[] challenge) {
        m_challenge = challenge;
    }

    /**
     * Return the SMB authentication context as a string
     *
     * @return String
     */
    public String toString() {

        StringBuffer str = new StringBuffer();

        str.append("[NTLM,Challenge=");
        str.append(HexDump.hexString(m_challenge));
        str.append("]");

        return str.toString();
    }
}
