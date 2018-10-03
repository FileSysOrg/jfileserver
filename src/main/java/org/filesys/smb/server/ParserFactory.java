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

package org.filesys.smb.server;

import org.filesys.smb.DialectSelector;

/**
 * SMB Parser Factory Interface
 *
 * @author gkspencer
 */
public interface ParserFactory {

    /**
     * Create a parser for the specified version of SMB
     *
     * @param smbVer SMBSrvPacket.Version
     * @param buf byte[]
     * @param len int
     * @return SMBParser
     * @exception UnsupportedSMBVersionException SMB version not supported
     */
    public SMBParser createParser( SMBSrvPacket.Version smbVer, byte[] buf, int len)
        throws UnsupportedSMBVersionException;

    /**
     * Return the set of SMB dialects supported by the parser
     *
     * @return DialectSelector
     */
    public DialectSelector getSupportedDialects();
}
