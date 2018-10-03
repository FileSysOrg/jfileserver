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

package org.filesys.ftp;

import java.util.HashMap;
import java.util.Map;

/**
 * FTP Command Types Class
 *
 * @author gkspencer
 */
public enum FTPCommand {
    USER    (0),
    PASS    (1),
    ACCT    (2),
    CWD     (3),
    CDUP    (4),
    SMNT    (5),
    REIN    (6),
    QUIT    (7),
    PORT    (8),
    PASV    (9),
    TYPE    (10),
    STRU    (11),
    MODE    (12),
    RETR    (13),
    STOR    (14),
    STOU    (15),
    APPE    (16),
    ALLO    (17),
    REST    (18),
    RNFR    (19),
    RNTO    (20),
    ABOR    (21),
    DELE    (22),
    RMD     (23),
    MKD     (24),
    PWD     (25),
    LIST    (26),
    NLST    (27),
    SITE    (28),
    SYST    (29),
    STAT    (30),
    HELP    (31),
    NOOP    (32),
    MDTM    (33),
    SIZE    (34),
    OPTS    (35),
    FEAT    (36),
    XPWD    (37),
    XMKD    (38),
    XRMD    (39),
    XCUP    (40),
    XCWD    (41),
    MLST    (42),
    MLSD    (43),
    EPRT    (44),
    EPSV    (45),
    AUTH    (46),
    PBSZ    (47),
    PROT    (48),
    CCC     (49),
    MFMT    (50),

    INVALID_CMD (-1);

    // Mapping command name to id
    private static Map<String, FTPCommand> _idMap = new HashMap<>();

    // FTP command id
    private final int ftpCmd;

    /**
     * Static initializer
     */
    static {
        for ( FTPCommand cmd : FTPCommand.values())
            _idMap.put( cmd.name(), cmd);
    }

    /**
     * Enum constructor
     *
     * @param cmd int
     */
    FTPCommand( int cmd) { ftpCmd = cmd; }

    /**
     * Convert an FTP command to an id
     *
     * @param cmd String
     * @return int
     */
    public final static FTPCommand getCommandId(String cmd) {

        FTPCommand ftpCmd = _idMap.get( cmd.toUpperCase());
        if ( ftpCmd == null)
            return INVALID_CMD;

        return ftpCmd;
    }
}
