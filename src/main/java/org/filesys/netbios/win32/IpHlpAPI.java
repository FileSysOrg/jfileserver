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

package org.filesys.netbios.win32;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinBase.OVERLAPPED;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Windows IpHlpAPI DLL Wrapper Class
 *
 * @author gkspencer
 */
public interface IpHlpAPI extends Library {

    IpHlpAPI INSTANCE = (IpHlpAPI) Native.loadLibrary("IpHlpAPI", IpHlpAPI.class, W32APIOptions.UNICODE_OPTIONS);

    /**
     * Wait for a network address change
     *
     * @param handle    Handle to be returned
     * @param overLap   OVERLAPPED structure
     * @return int
     */
    int NotifyAddrChange(WinNT.HANDLEByReference handle, OVERLAPPED overLap);

    /**
     * Cancel wait for address change
     *
     * @param overLap   OVERLAPPED structure used in NotifyAddrChange
     * @return boolean
     */
    boolean CancelIPChangeNotify( OVERLAPPED overLap);
}
