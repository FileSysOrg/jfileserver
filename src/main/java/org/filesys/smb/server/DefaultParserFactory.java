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

import org.filesys.smb.Dialect;
import org.filesys.smb.DialectSelector;

/**
 * Default Parser Factpry Class
 *
 * <p>Default implementation creates SMB v1 parsers</p>
 *
 * @author gkspencer
 */
public class DefaultParserFactory implements ParserFactory {

    // Set of supported SMB dialects for this parser
    static private DialectSelector _supportedDialects;

    /**
     * Create a parser for the specified version of SMB
     *
     * @param smbVer SMBSrvPacket.Version
     * @param buf byte[]
     * @param len int
     * @return SMBParser
     * @exception UnsupportedSMBVersionException SMB version is not supported
     */
    public SMBParser createParser( SMBSrvPacket.Version smbVer, byte[] buf, int len)
        throws UnsupportedSMBVersionException {

        // Create an SMB v1 parser, or throw an exception
        if ( smbVer == SMBSrvPacket.Version.V1)
            return new SMBV1Parser(buf, len);

        throw new UnsupportedSMBVersionException();
    }

    /**
     * Return the set of supported SMB dialects for this parser
     *
     * @return DialectSelector
     */
    public DialectSelector getSupportedDialects() {
        return _supportedDialects;
    }

    /**
     * Static initializer
     */
    static {

        // Setup the supported set of SMB dialects
        _supportedDialects = new DialectSelector();
        _supportedDialects.enableUpTo(Dialect.UpToSMBv1);
    }
}
