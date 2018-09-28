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

import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * Debug Output Interface
 *
 * @author gkspencer
 */
public interface DebugInterface {

    /**
     * Close the debug output.
     */
    public void close();

    /**
     * Output a debug string.
     *
     * @param str String
     */
    public void debugPrint(String str);

    /**
     * Output a debug string with a specific logging level
     *
     * @param str   String
     * @param level int
     */
    public void debugPrint(String str, int level);

    /**
     * Output a debug string, and a newline.
     *
     * @param str String
     */
    public void debugPrintln(String str);

    /**
     * Output a debug string, and a newline, with a specific logging level
     *
     * @param str String
     * @param level int
     */
    public void debugPrintln(String str, int level);

    /**
     * Output an exception
     *
     * @param ex    Exception
     * @param level int
     */
    public void debugPrintln(Exception ex, int level);

    /**
     * Initialize the debug interface using the specified named parameters.
     *
     * @param params ConfigElement
     * @param config ServerConfiguration
     * @exception Exception Error during initialization of the debug interface
     */
    public void initialize(ConfigElement params, ServerConfiguration config)
            throws Exception;

    /**
     * Return the debug interface logging level
     *
     * @return int
     */
    public int getLogLevel();
}
