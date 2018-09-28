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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * Log File Debug Class.
 *
 * <p>Output the debug information to a log file.
 *
 * @author gkspencer
 */
public class LogFileDebug extends DebugInterfaceBase {

    //	Output file stream
    private PrintStream m_out;

    /**
     * Default constructor
     */
    public LogFileDebug() {
    }

    /**
     * Create a log file debug object using the specified file name. Append to an existing file
     * if the append flag is true, else truncate the existing file.
     *
     * @param fname  String
     * @param append boolean
     * @exception IOException Error opening the log file
     */
    public LogFileDebug(String fname, boolean append)
            throws IOException {

        //  Open the file
        open(fname, append);
    }

    /**
     * Open the output file stream
     *
     * @param fname  String
     * @param append boolean
     * @exception IOException Error opening the log file
     */
    protected final void open(String fname, boolean append)
            throws IOException {

        //	Open the output file and also redirect the standard output stream to it
        FileOutputStream fout = new FileOutputStream(fname, append);
        m_out = new PrintStream(fout);
        System.setOut(m_out);
    }

    /**
     * Close the debug output.
     */
    public void close() {

        //  Close the debug file, if open
        if (m_out != null) {
            m_out.close();
            m_out = null;
        }
    }

    /**
     * Output a debug string with a specific logging level
     *
     * @param str   String
     * @param level int
     */
    public void debugPrint(String str, int level) {
        if (level <= getLogLevel())
            m_out.print(str);
    }

    /**
     * Output a debug string, and a newline, with a specific logging level
     *
     * @param str String
     * @param level int
     */
    public void debugPrintln(String str, int level) {
        if (level <= getLogLevel() && m_out != null) {
            m_out.println(str);
            m_out.flush();
        }
    }

    /**
     * Initialize the debug interface using the specified parameters.
     *
     * @param params ConfigElement
     * @param config ServerConfiguration
     */
    public void initialize(ConfigElement params, ServerConfiguration config)
            throws Exception {

        // Call the base class
        super.initialize(params, config);

        //	Get the output file name and append flag settings
        ConfigElement logFile = params.getChild("logFile");
        boolean append = params.getChild("append") != null ? true : false;

        //	Check if the log file has been specified
        if (logFile.getValue() == null || logFile.getValue().length() == 0)
            throw new Exception("logFile parameter not specified");

        //  Open the file
        open(logFile.getValue(), append);
    }
}
