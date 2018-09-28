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

package org.filesys.oncrpc.portmap;

/**
 * PortMapper RPC Service Constants Class
 *
 * @author gkspencer
 */
public class PortMapper {

    //	Default port mapper port
    public static final int DefaultPort = 111;

    //	Program and version id
    public static final int ProgramId = 100000;
    public static final int VersionId = 2;

    //	RPC procedure ids
    public static final int ProcNull    = 0;
    public static final int ProcSet     = 1;
    public static final int ProcUnSet   = 2;
    public static final int ProcGetPort = 3;
    public static final int ProcDump    = 4;

    public static final int ProcMax     = 4;

    //	RPC procedure names
    private static final String[] _procNames = {"Null", "Set", "UnSet", "GetPort", "Dump"};

    /**
     * Return a procedure id as a name
     *
     * @param id int
     * @return String
     */
    public final static String getProcedureName(int id) {
        if (id < 0 || id > ProcMax)
            return null;
        return _procNames[id];
    }
}
