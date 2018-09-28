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

/**
 * Debug Output Class
 *
 * @author gkspencer
 */
public final class Debug {

    //	Global constants used to control compiling of debug statements
    public static final boolean EnableInfo = true;
    public static final boolean EnableWarn = true;
    public static final boolean EnableError = true;
    public static final boolean EnableDbg = true;

    //	Line seperator used for exception stack traces
    private static final String LineSeperator = System.getProperty("line.separator");

    //  Debug output levels
    //
    //  Note: These should map to the standard Log4j levels
    public static final int Fatal = 0;
    public static final int Error = 1;
    public static final int Warn = 2;
    public static final int Info = 3;
    public static final int Debug = 4;

    //	Global debug interface
    private static DebugInterface m_debug = new ConsoleDebug();

    /**
     * Default constructor
     */
    private Debug() {
    }

    /**
     * Get the debug interface
     *
     * @return dbg
     */
    public static final DebugInterface getDebugInterface() {
        return m_debug;
    }

    /**
     * Set the debug interface
     *
     * @param dbg DebugInterface
     */
    public static final void setDebugInterface(DebugInterface dbg) {
        m_debug = dbg;
    }

    /**
     * Output a debug string.
     *
     * @param str String
     */
    public static final void print(String str) {
        m_debug.debugPrint(str);
    }

    /**
     * Output a debug string.
     *
     * @param str   String
     * @param level int
     */
    public static final void print(String str, int level) {
        m_debug.debugPrint(str, level);
    }

    /**
     * Output a debug string, and a newline.
     *
     * @param str String
     */
    public static final void println(String str) {
        m_debug.debugPrintln(str);
    }

    /**
     * Output a debug string, and a newline.
     *
     * @param str   String
     * @param level int
     */
    public static final void println(String str, int level) {
        m_debug.debugPrintln(str, level);
    }

    /**
     * Output an exception trace to the debug device
     *
     * @param ex Exception
     */
    public static final void println(Exception ex) {
        println(ex, Error);
    }

    /**
     * Output an exception trace to the debug device
     *
     * @param ex    Exception
     * @param level int
     */
    public static final void println(Exception ex, int level) {
        m_debug.debugPrintln(ex, level);
    }

    /**
     * Output an exception trace to the debug device
     *
     * @param ex Throwable
     */
    public static final void println(Throwable ex) {
        println(ex, Error);
    }

    /**
     * Output an exception trace to the debug device
     *
     * @param ex    Throwable
     * @param level int
     */
    public static final void println(Throwable ex, int level) {

        //	Write the exception stack trace records to an in-memory stream
        StringWriter strWrt = new StringWriter();
        ex.printStackTrace(new PrintWriter(strWrt, true));

        //	Split the resulting string into seperate records and output to the debug device
        StringTokenizer strTok = new StringTokenizer(strWrt.toString(), LineSeperator);

        while (strTok.hasMoreTokens())
            m_debug.debugPrintln(strTok.nextToken(), level);
    }
}
