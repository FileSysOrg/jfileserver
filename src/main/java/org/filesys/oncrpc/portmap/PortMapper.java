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

import org.filesys.oncrpc.nfs.v3.NFS3;

import java.util.HashMap;
import java.util.Map;

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
    public enum ProcedureId {
        Null(0),
        Set(1),
        UnSet(2),
        GetPort(3),
        Dump(4),

        Invalid(0xFFFF);

        private final int procId;

        // Mapping procedure name to id
        private static Map<Integer, PortMapper.ProcedureId> _idMap = new HashMap<>();

        /**
         * Static initializer
         */
        static {
            for ( PortMapper.ProcedureId id : PortMapper.ProcedureId.values())
                _idMap.put( id.intValue(), id);
        }

        /**
         * Enum constructor
         *
         * @param id int
         */
        ProcedureId(int id) { procId = id; }

        /**
         * Return the procedure id as an int
         *
         * @return int
         */
        public final int intValue() { return procId; }

        /**
         * Create a procedure id type from an int
         *
         * @param typ int
         * @return ProcedureId
         */
        public static final PortMapper.ProcedureId fromInt(int typ) {

            if ( _idMap.containsKey( typ))
                return _idMap.get( typ);

            return Invalid;
        }
    }
}
