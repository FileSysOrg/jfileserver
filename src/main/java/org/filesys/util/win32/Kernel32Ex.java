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

package org.filesys.util.win32;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Windows Kernel32 DLL Wrapper Class
 *
 * @author gkspencer
 */
public interface Kernel32Ex extends StdCallLibrary {

    Kernel32Ex INSTANCE = (Kernel32Ex) Native.loadLibrary("Kernel32", Kernel32Ex.class, W32APIOptions.UNICODE_OPTIONS);

    /**
     * Set process working set size
     *
     * @param handle    Handle of the process whose working set size is to be set
     * @param minSize   Minimum working set size for the process, in bytes
     * @param maxSize   Maximum working set size for the process, in bytes
     * @return boolean
     */
    boolean SetProcessWorkingSetSize(HANDLE handle, SIZE_T minSize, SIZE_T maxSize);
}
