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

package org.filesys.debug;

/**
 * Console Debug Output Class.
 *
 * <p>Output debug messages to the console stream, System.out.
 *
 * @author gkspencer
 */
public class ConsoleDebug extends DebugInterfaceBase {

    /**
     * Output a debug string with a specific logging level
     *
     * @param str   String
     * @param level int
     */
    public void debugPrint(String str, int level) {
        if (level <= getLogLevel())
            System.out.print(str);
    }

    /**
     * Output a debug string, and a newline, with a specific logging level
     *
     * @param str String
     */
    public void debugPrintln(String str, int level) {
        if (level <= getLogLevel())
            System.out.println(str);
    }
}
