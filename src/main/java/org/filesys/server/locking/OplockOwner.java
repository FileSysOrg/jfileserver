/*
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.server.locking;

import org.filesys.smb.OpLockType;

/**
 * Oplock Owner Interface
 *
 * @author gkspencer
 */
public interface OplockOwner {

    /**
     * Check if the oplock owner matches this oplock owner for the type of oplock
     *
     * @param opType    OplockType
     * @param opOwner   OplockOwner
     * @return boolean
     */
    public boolean isOwner(OpLockType opType, OplockOwner opOwner);
}
