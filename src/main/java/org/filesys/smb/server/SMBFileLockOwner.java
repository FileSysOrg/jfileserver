/*
 * Copyright (C) 2020 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.smb.server;

import org.filesys.locking.FileLock;
import org.filesys.locking.FileLockOwner;
import org.filesys.server.SrvSession;

/**
 * SMB File Lock Owner Class
 *
 * <p>Represents a file lock owned by an SMB client</p>
 *
 * @author gkspencer
 */
public class SMBFileLockOwner extends FileLockOwner {

    // Process id that owns this lock
    private int m_pid;

    /**
     * Class constructor
     *
     * @param sess SMBSrvSession
     * @param version int
     */
    public SMBFileLockOwner( SMBSrvSession sess, int version) {
        super( Protocol.SMB, version, sess);

        m_pid    = sess.getProcessId();
    }

    /**
     * Return the owner process id
     *
     * @return int
     */
    public final int getProcessId() { return m_pid; }

    @Override
    public boolean isLockOwner(FileLock fLock) {

        // Check if the lock owner types match
        if ( fLock.hasOwner() && fLock.getOwner().isProtocol() == isProtocol()) {

            // Get the current lock owner details from the file lock
            SMBFileLockOwner smbOwner = (SMBFileLockOwner) fLock.getOwner();

            if ( smbOwner.getSessionId() == getSessionId() && smbOwner.getProcessId() == getProcessId())
                return true;
        }

        // Not the lock owner
        return false;
    }

    @Override
    protected void buildDetailsString(StringBuilder str) {
        super.buildDetailsString(str);

        str.append( ", processId=");
        str.append( getProcessId());
    }
}
