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

package org.filesys.util;

import com.sun.jna.Platform;

/**
 * PlatformType Class
 *
 * <p>Determine the platform type that we are runnng on.
 *
 * @author gkspencer
 */
public class PlatformType {

    // PlatformType types
    public enum Type {
        Unchecked, Unknown, WINDOWS, LINUX, SOLARIS, MACOSX, AIX
    };

    // PlatformType type we are running on
    private static Type _platformType = Type.Unchecked;

    /**
     * Determine the platform type
     *
     * @return Type
     */
    public static final Type isPlatformType() {

        // Check if the type has been set
        if (_platformType == Type.Unchecked) {

            // Get the operating system type
            if (Platform.isWindows())
                _platformType = Type.WINDOWS;
            else if (Platform.isLinux())
                _platformType = Type.LINUX;
            else if (Platform.isMac())
                _platformType = Type.MACOSX;
            else if (Platform.isSolaris())
                _platformType = Type.SOLARIS;
            else if (Platform.isAIX())
                _platformType = Type.AIX;
        }

        // Return the current platform type
        return _platformType;
    }

    /**
     * Determine if we are running under Windows NT onwards
     *
     * @return boolean
     */
    public static final boolean isWindowsNTOnwards() {

        // Get the operating system name property
        String osName = System.getProperty("os.name");

        if ( osName.startsWith("Windows")) {
            if ( osName.endsWith("95") || osName.endsWith("98") || osName.endsWith("ME")) {

                // Windows 95-ME
                return false;
            }

            // Looks like Windows NT onwards
            return true;
        }

        // Not Windows
        return false;
    }
}
