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

package org.filesys.smb.dcerpc;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Defines the special DCE/RPC pipe names.
 *
 * @author gkspencer
 */
public enum DCEPipeType {
    PIPE_SRVSVC     (0),
    PIPE_SAMR       (1),
    PIPE_WINREG     (2),
    PIPE_WKSSVC     (3),
    PIPE_NETLOGON   (4),
    PIPE_LSARPC     (5),
    PIPE_SPOOLSS    (6),
    PIPE_NETDFS     (7),
    PIPE_SVCCTL     (8),
    PIPE_EVENTLOG   (9),
    PIPE_NETLOGON1  (10),
    PIPE_INITSHUT   (11),

    PIPE_INVALID    (-1);

    private final int pipeId;

    // Mapping command name to id
    private static Map<Integer, DCEPipeType> _typeMap = new HashMap<>();

    /**
     * Static initializer
     */
    static {
        for ( DCEPipeType pipeTyp : DCEPipeType.values())
            _typeMap.put( pipeTyp.intValue(), pipeTyp);
    }

    /**
     * Enum constructor
     *
     * @param typ int
     */
    DCEPipeType(int typ) { pipeId = typ; }

    /**
     * Return the pipe type as an int
     *
     * @return int
     */
    public final int intValue() { return pipeId; }

    /**
     * Create a pipe type from an int
     *
     * @param typ int
     * @return DCEPipeType
     */
    public static final DCEPipeType fromInt(int typ) {

        if ( _typeMap.containsKey( typ))
            return _typeMap.get( typ);

        return PIPE_INVALID;
    }

    //	IPC$ client pipe names
	private static final String[] _pipeNames = { "\\PIPE\\srvsvc",
      	  										 "\\PIPE\\samr",
      	  										 "\\PIPE\\winreg",
      	  										 "\\PIPE\\wkssvc",
      	  										 "\\PIPE\\NETLOGON",
      	  										 "\\PIPE\\lsarpc",
      	  										 "\\PIPE\\spoolss",
      	  										 "\\PIPE\\netdfs",
      	  										 "\\PIPE\\svcctl",
      	  										 "\\PIPE\\EVENTLOG",
      	  										 "\\PIPE\\NETLOGON",
												 "\\PIPE\\InitShutdown"
	};

	//	IPC$ server pipe names
	private static final String[] _srvNames = { "\\PIPE\\srvsvc",
	  											"\\PIPE\\lsass",
	  											"\\PIPE\\winreg",
	  											"\\PIPE\\ntsvcs",
	  											"\\PIPE\\lsass",
	  											"\\PIPE\\lsass",
	  											"\\PIPE\\spoolss",
	  											"\\PIPE\\netdfs",
	  											"\\PIPE\\svcctl",
	  											"\\PIPE\\EVENTLOG",
												"\\PIPE\\InitShutdown"
	};

	//	IPC$ pipe UUIDs
	private static UUID _uuidNetLogon = new UUID("8a885d04-1ceb-11c9-9fe8-08002b104860", 2);
	private static UUID _uuidWinReg   = new UUID("338cd001-2244-31f1-aaaa-900038001003", 1);
	private static UUID _uuidSvcCtl   = new UUID("367abb81-9844-35f1-ad32-98f038001003", 2);
	private static UUID _uuidLsaRpc   = new UUID("12345678-1234-abcd-ef00-0123456789ab", 0);
	private static UUID _uuidSrvSvc   = new UUID("4b324fc8-1670-01d3-1278-5a47bf6ee188", 3);
	private static UUID _uuidWksSvc   = new UUID("6bffd098-a112-3610-9833-46c3f87e345a", 1);
	private static UUID _uuidSamr     = new UUID("12345778-1234-abcd-ef00-0123456789ac", 1);
	private static UUID _uuidSpoolss  = new UUID("12345778-1234-abcd-ef00-0123456789ab", 1);
	private static UUID _uuidSvcctl		= new UUID("367abb81-9844-35f1-ad32-98f038001003", 2);
	private static UUID _uuidEventLog	= new UUID("82273FDC-E32A-18C3-3F78-827929DC23EA", 0);
	private static UUID _uuidNetLogon1= new UUID("12345678-1234-abcd-ef00-01234567cffb", 1);
	private static UUID _uuidInitShut = new UUID("894de0c0-0d55-11d3-a322-00c04fa321a1", 1);

//	private static UUID _uuidAtSvc    = new UUID("1ff70682-0a51-30e8-076d-740be8cee98b", 1);

	/**
	 * Convert a pipe name to a type
	 *
	 * @param name String
	 * @return DCEPipeType
	 */
	public final static DCEPipeType getNameAsType(String name) {
		for (int i = 0; i < _pipeNames.length; i++) {
			if (_pipeNames[i].equals(name))
				return DCEPipeType.fromInt(i);
		}
		return PIPE_INVALID;
	}

	/**
	 * Convert a pipe type to a name
	 *
	 * @param typ DCEPipeType
	 * @return String
	 */
	public final static String getTypeAsString(DCEPipeType typ) {
	    int iTyp = typ.intValue();

		if (iTyp >= 0 && iTyp < _pipeNames.length)
			return _pipeNames[iTyp];
		return null;
	}

	/**
	 * Convert a pipe type to a short name
	 *
	 * @param typ DECPipeType
	 * @return String
	 */
	public final static String getTypeAsStringShort(DCEPipeType typ) {
        int iTyp = typ.intValue();

        if (iTyp >= 0 && iTyp < _pipeNames.length)
            return _pipeNames[iTyp];
        return null;
	}

	/**
	 * Return the UUID for the pipe type
	 *
	 * @param typ DCEPipeType
	 * @return UUID
	 */
	public final static UUID getUUIDForType(DCEPipeType typ) {
		UUID ret = null;

		switch (typ) {
			case PIPE_NETLOGON:
				ret = _uuidNetLogon;
				break;
			case PIPE_NETLOGON1:
				ret = _uuidNetLogon1;
				break;
			case PIPE_WINREG:
				ret = _uuidWinReg;
				break;
			case PIPE_LSARPC:
				ret = _uuidLsaRpc;
				break;
			case PIPE_WKSSVC:
				ret = _uuidWksSvc;
				break;
			case PIPE_SAMR:
				ret = _uuidSamr;
				break;
			case PIPE_SRVSVC:
				ret = _uuidSrvSvc;
				break;
			case PIPE_SPOOLSS:
				ret = _uuidSpoolss;
				break;
			case PIPE_SVCCTL:
				ret = _uuidSvcCtl;
				break;
			case PIPE_EVENTLOG:
				ret = _uuidEventLog;
				break;
			case PIPE_INITSHUT:
				ret = _uuidInitShut;
				break;
		}
		return ret;
	}

	/**
	 * Get the server-side pipe name for the specified pipe
	 *
	 * @param typ DCEPipeType
	 * @return String
	 */
	public final static String getServerPipeName(DCEPipeType typ) {
        int iTyp = typ.intValue();

        if (iTyp >= 0 && iTyp < _srvNames.length)
			return _srvNames[iTyp];
		return null;
	}
}
