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

package org.filesys.smb.dcerpc.info;

/**
 * NT Service Constants Class
 *
 * @author gkspencer
 */
public class NTService {

	//	Service states to enumerate
	public static final int EnumActive					= 0x01;
	public static final int EnumInactive				= 0x02;
	public static final int EnumAll						= 0x03;
	
	//	Service states
	public static final int StateStopped				= 1;
	public static final int StateStartPending			= 2;
	public static final int StateStopPending			= 3;
	public static final int StateRunning				= 4;
	public static final int StateContinuePending		= 5;
	public static final int StatePausePending			= 6;
	public static final int StatePaused					= 7;

	//	Controls accepted by a service
	public static final int CtrlStop					= 0x0001;
	public static final int CtrlPauseContinue			= 0x0002;
	public static final int CtrlShutdown				= 0x0004;
	public static final int CtrlParamChange				= 0x0008;
	public static final int CtrlNetBindChange			=	0x0010;

	//	Service types
	public static final int TypeKernelDriver		= 0x0001;
	public static final int TypeFileSystem			= 0x0002;
	public static final int TypeAdapter				= 0x0004;
	public static final int TypeRecognizer			= 0x0008;
	public static final int TypeDriver				= TypeKernelDriver + TypeFileSystem + TypeAdapter + TypeRecognizer;
	
	public static final int TypeOwnProcess			= 0x0010;
	public static final int TypeSharedProcess		= 0x0020;
	public static final int TypeWin32				= TypeOwnProcess + TypeSharedProcess;
	
	public static final int TypeInteractive			= 0x0100;
	
	public static final int TypeAll					= TypeDriver + TypeWin32;

	//  For setServiceConfiguration()
	public static final int TypeNoChange        	= 0xFFFFFFFF;
	
	//	Service start type
	public static final int StartBoot				= 0;
	public static final int StartSystem				= 1;
	public static final int StartAuto				= 2;
	public static final int StartDemand				= 3;
	public static final int StartDisabled			= 4;	

	//	Service error control
	public static final int ErrorIgnore				= 0;
	public static final int ErrorNormal				= 1;
	public static final int ErrorSevere				= 2;
	public static final int ErrorCritical			= 3;
	
	//  Control service sub-codes
	public static final int ServiceCtrlStop     	= 1;
	public static final int ServiceCtrlPause    	= 2;
	public static final int ServiceCtrlResume   	= 3;

	//	Service state strings
	private static final String[] _srvState = { "Stopped",
	  											"StartPending",
	  											"StopPending",
	  											"Running",
	  											"ContinuePending",
	  											"PausePending",
	  											"Paused"
	};
	
	//	Service start type strings
	private static final String[] _srvStart = { "Boot",
	  											"System",
	  											"Auto",
	  											"Demand",
	  											"Disabled"
	};
	
	//	Service error control strings
	private static final String[] _srvError = { "Ignore",
	  											"Normal",
	  											"Severe",
	  											"Critical"
	};
	
	//	Service controls accepted strings
	private static final String[] _srvCtrls = { "Stop",
	  											"PauseContinue",
	  											"Shutdown",
	  											"ParamChange",
	  											"NetBindChange"
	};
	
	//	Service type strings
	private static final String[] _srvType = { "KernelDriver",
	  										   "FileSystem",
	  										   "Adapter",
	  										   "Recognizer",
	  										   "OwnProcess",
	  										   "ShareProcess"
	};

	/**
	 * Return the service state as a string
	 *
	 * @param state int
	 * @return String
	 */
	public static final String getStateAsString(int state) {
		int idx = state - 1;
		if (idx < 0 || idx >= _srvState.length)
			return null;
		return _srvState[idx];
	}

	/**
	 * Return the service start type as a string
	 *
	 * @param start int
	 * @return String
	 */
	public static final String getStartTypeAsString(int start) {
		if (start < 0 || start >= _srvStart.length)
			return null;
		return _srvStart[start];
	}

	/**
	 * Return the service error control as a string
	 *
	 * @param err int
	 * @return String
	 */
	public static final String getErrorControlAsString(int err) {
		if (err < 0 || err >= _srvError.length)
			return null;
		return _srvError[err];
	}

	/**
	 * Return the service controls accepted as a string
	 *
	 * @param ctrls int
	 * @return String
	 */
	public static final String getControlsAcceptedAsString(int ctrls) {
		StringBuffer str = new StringBuffer();

		for (int i = 0; i < _srvCtrls.length; i++) {
			if ((ctrls & (1 << i)) != 0) {
				if (str.length() > 0)
					str.append(",");
				str.append(_srvCtrls[i]);
			}
		}

		return str.toString();
	}

	/**
	 * Return the service type as a string
	 *
	 * @param typ int
	 * @return String
	 */
	public static final String getTypeAsString(int typ) {
		StringBuffer str = new StringBuffer();

		for (int i = 0; i < _srvType.length; i++) {
			if ((typ & (1 << i)) != 0) {
				if (str.length() > 0)
					str.append(",");
				str.append(_srvType[i]);
			}
		}

		return str.toString();
	}
}
