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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringTokenizer;

import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * Debug Interface Base Class
 *
 * @author gkspencer
 */
public abstract class DebugInterfaceBase implements DebugInterface {

    //	Line seperator used for exception stack traces
    private static final String LineSeperator = System.getProperty("line.separator");

    // Log output level
    private int m_logLevel = Debug.Debug;

    /**
     * Class constructor
     */
    public DebugInterfaceBase() {
    }

    /**
     * Close the debug output.
     */
    public void close() {
    }

    /**
     * Output a debug string.
     *
     * @param str String
     */
    public final void debugPrint(String str) {
        debugPrint(str, Debug.Debug);
    }

    /**
     * Output a debug string with a specific logging level
     *
     * @param str   String
     * @param level int
     */
    public abstract void debugPrint(String str, int level);

    /**
     * Output a debug string, and a newline.
     *
     * @param str String
     */
    public final void debugPrintln(String str) {
        debugPrintln(str, Debug.Debug);
    }

    /**
     * Output a debug string, and a newline, with a specific logging level
     *
     * @param str String
     */
    public abstract void debugPrintln(String str, int level);

    /**
     * Output an exception
     *
     * @param ex    Exception
     * @param level int
     */
    public void debugPrintln(Exception ex, int level) {

        //	Write the exception stack trace records to an in-memory stream
        StringWriter strWrt = new StringWriter();
        ex.printStackTrace(new PrintWriter(strWrt, true));

        //	Split the resulting string into seperate records and output to the debug device
        StringTokenizer strTok = new StringTokenizer(strWrt.toString(), LineSeperator);

        while (strTok.hasMoreTokens())
            debugPrintln(strTok.nextToken(), level);
    }

    /**
     * Return the debug interface logging level
     *
     * @return int
     */
    public final int getLogLevel() {
        return m_logLevel;
    }

    /**
     * Set the logging level
     *
     * @param logLevel int
     */
    public final void setLogLevel(int logLevel) {
        m_logLevel = logLevel;
    }

    /**
     * Initialize the debug interface using the specified parameters.
     *
     * @param params ConfigElement
     * @param config ServerConfiguration
     */
    public void initialize(ConfigElement params, ServerConfiguration config)
            throws Exception {

        // Check for a logging level
        ConfigElement logLevelElem = params.getChild("logLevel");

        if (logLevelElem != null) {

            // Validate the logging level
            String logLevelStr = logLevelElem.getValue();

            if (logLevelStr != null) {
                if (logLevelStr.equalsIgnoreCase("Debug"))
                    m_logLevel = Debug.Debug;
                else if (logLevelStr.equalsIgnoreCase("Info"))
                    m_logLevel = Debug.Info;
                else if (logLevelStr.equalsIgnoreCase("Warn"))
                    m_logLevel = Debug.Warn;
                else if (logLevelStr.equalsIgnoreCase("Error"))
                    m_logLevel = Debug.Error;
                else if (logLevelStr.equalsIgnoreCase("Fatal"))
                    m_logLevel = Debug.Fatal;
                else
                    throw new Exception("Invalid debug logging level, " + logLevelStr);
            }
        }
    }
}
