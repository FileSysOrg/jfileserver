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

package org.filesys.oncrpc.mount;

import org.filesys.oncrpc.nfs.v3.NFS3;
import org.filesys.oncrpc.portmap.PortMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Mount Server Constants Class
 *
 * @author gkspencer
 */
public final class Mount {

    //	Program and version id
    public static final int ProgramId       = 100005;
    public static final int VersionId1      = 1;
    public static final int VersionId3      = 3;

    //	RPC procedure ids (version 1)
    public enum ProcedureId1 {
        Null(0),
        Mnt(1),
        Dump(2),
        UMnt(3),
        UMntAll(4),
        Export(5),
        ExportAll(6),

        Invalid(0xFFFF);

        private final int procId;

        // Mapping procedure name to id
        private static Map<Integer, Mount.ProcedureId1> _idMap = new HashMap<>();

        /**
         * Static initializer
         */
        static {
            for ( Mount.ProcedureId1 id : Mount.ProcedureId1.values())
                _idMap.put( id.intValue(), id);
        }

        /**
         * Enum constructor
         *
         * @param id int
         */
        ProcedureId1(int id) { procId = id; }

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
         * @return ProcedureId1
         */
        public static final Mount.ProcedureId1 fromInt(int typ) {

            if ( _idMap.containsKey( typ))
                return _idMap.get( typ);

            return Invalid;
        }
    }

    //	RPC procedure ids (version 3)
    public enum ProcedureId3 {
        Null(0),
        Mnt(1),
        Dump(2),
        UMnt(3),
        UMntAll(4),
        Export(5),

        Invalid(0xFFFF);

        private final int procId;

        // Mapping procedure name to id
        private static Map<Integer, Mount.ProcedureId3> _idMap = new HashMap<>();

        /**
         * Static initializer
         */
        static {
            for ( Mount.ProcedureId3 id : Mount.ProcedureId3.values())
                _idMap.put( id.intValue(), id);
        }

        /**
         * Enum constructor
         *
         * @param id int
         */
        ProcedureId3(int id) { procId = id; }

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
         * @return ProcedureId3
         */
        public static final Mount.ProcedureId3 fromInt(int typ) {

            if ( _idMap.containsKey( typ))
                return _idMap.get( typ);

            return Invalid;
        }
    }

    //	Mount server status codes
    public enum StatusCode {
        Success(0),
        Perm(1),
        NoEnt(2),
        IO(5),
        Access(13),
        NotDir(20),
        InVal(22),
        NameTooLong(63),
        NotSupp(10004),
        ServerFault(10006),

        Invalid(0xFFFF);

        private final int stsCode;

        // Mapping status code to id
        private static Map<Integer, Mount.StatusCode> _stsMap = new HashMap<>();

        /**
         * Static initializer
         */
        static {
            for ( Mount.StatusCode sts : Mount.StatusCode.values())
                _stsMap.put( sts.intValue(), sts);
        }

        /**
         * Enum constructor
         *
         * @param id int
         */
        StatusCode(int id) { stsCode = id; }

        /**
         * Return the status code as an int
         *
         * @return int
         */
        public final int intValue() { return stsCode; }

        /**
         * Create a status code type from an int
         *
         * @param sts int
         * @return StatusCode
         */
        public static final Mount.StatusCode fromInt(int sts) {

            if ( _stsMap.containsKey( sts))
                return _stsMap.get( sts);

            return Invalid;
        }
    }

    //	Data structure limits
    public static final int FileHandleSize1 = 32;
    public static final int FileHandleSize3 = 32;        //	can be 64 for v3
}
