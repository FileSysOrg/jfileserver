/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.locking;

import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * Deferred Request Class
 *
 * <p>Holds the session and request details for a deferred request that is waiting on an oplock break
 * notification from the client owning the oplock.
 *
 * @author gkspencer
 */
public class DeferredRequest {

    // Session and request packet
    private SMBSrvSession m_deferredSess;
    private SMBSrvPacket m_deferredPkt;

    /**
     * Class constructor
     *
     * @param sess   SMBSrvSession
     * @param reqPkt SMBSrvPacket
     */
    public DeferredRequest(SMBSrvSession sess, SMBSrvPacket reqPkt) {
        m_deferredSess = sess;
        m_deferredPkt = reqPkt;
    }

    /**
     * Return the deferred session
     *
     * @return SMBSrvSession
     */
    public final SMBSrvSession getDeferredSession() {
        return m_deferredSess;
    }

    /**
     * Return the deferred request packet
     *
     * @return SMBSrvPacket
     */
    public final SMBSrvPacket getDeferredPacket() {
        return m_deferredPkt;
    }

    /**
     * Return the deferred request details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Deferred sess=");

        if (getDeferredSession() != null)
            str.append(getDeferredSession().getUniqueId());
        else
            str.append("null");

        str.append(", pkt=");

        if (getDeferredPacket() != null)
            str.append(getDeferredPacket());
        else
            str.append("null");
        str.append("]");

        return str.toString();
    }
}
