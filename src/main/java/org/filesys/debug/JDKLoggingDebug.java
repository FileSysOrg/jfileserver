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

import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * JDK Logging Debug Class
 *
 * <p>Output debug messages using the JDK logging APIs.
 *
 * @author gkspencer
 */
public class JDKLoggingDebug extends DebugInterfaceBase {

    // Buffer for debugPrint() strings
    private StringBuilder m_printBuf;

    /**
     * Class constructor
     */
    public JDKLoggingDebug() {
        super();
    }

    /**
     * Output a debug string.
     *
     * @param str String
     * @param level int
     */
    public final void debugPrint(String str, int level) {

        // Check if the logging level is enabled
        if (level <= getLogLevel()) {

            // Allocate a holding buffer
            if (m_printBuf == null) {
                synchronized (this) {
                    if (m_printBuf == null)
                        m_printBuf = new StringBuilder();
                }
            }

            // Append the string to the holding buffer
            synchronized (m_printBuf) {
                m_printBuf.append(str);
            }
        }
    }

    /**
     * Output a debug string, and a newline.
     *
     * @param str String
     * @param level int
     */
    public final void debugPrintln(String str, int level) {

        // Check if the logging level is enabled
        if (level <= getLogLevel()) {

            // Check if there is a holding buffer
            if (m_printBuf != null) {

                // Append the new string
                m_printBuf.append(str);
                logOutput(m_printBuf.toString(), level);
                m_printBuf = null;
            } else
                logOutput(str, level);
        }
    }

    /**
     * Output an exception
     *
     * @param ex    Exception
     * @param level int
     */
    public void debugPrintln(Exception ex, int level) {

        // Check if the logging level is enabled
        if (level <= getLogLevel()) {

            // Convert the logging level to a JDK logging level
            Level logLevel = Level.OFF;

            switch (level) {
                case Debug.Debug:
                    logLevel = Level.FINEST;
                    break;
                case Debug.Info:
                    logLevel = Level.INFO;
                    break;
                case Debug.Warn:
                    logLevel = Level.WARNING;
                    break;
                case Debug.Fatal:
                    logLevel = Level.SEVERE;
                    break;
                case Debug.Error:
                    logLevel = Level.FINEST;
                    break;
            }

            // Output the exception
            Logger.getGlobal().log(logLevel, "", ex);
        }
    }

    /**
     * Output to the logger at the appropriate log level
     *
     * @param str   String
     * @param level int
     */
    protected void logOutput(String str, int level) {
        Level logLevel = Level.OFF;

        switch (level) {
            case Debug.Debug:
                logLevel = Level.FINEST;
                break;
            case Debug.Info:
                logLevel = Level.INFO;
                break;
            case Debug.Warn:
                logLevel = Level.WARNING;
                break;
            case Debug.Fatal:
                logLevel = Level.SEVERE;
                break;
            case Debug.Error:
                logLevel = Level.FINEST;
                break;
        }

        Logger.getGlobal().log(logLevel, str);
    }

    /**
     * Initialize the debug interface using the specified parameters.
     *
     * @param params ConfigElement
     * @param config ServerConfiguration
     */
    public void initialize(ConfigElement params, ServerConfiguration config) {

        //  Get the logging properties file name
        ConfigElement logProps = params.getChild("Properties");

        //  Check if the log file has been specified
        if (logProps.getValue() != null) {

            // Open the logging properties file
            FileInputStream logPropsFile = null;

            try {

                // Open the logging properties file
                logPropsFile = new FileInputStream(logProps.getValue());

                // Load the logging properties
                LogManager.getLogManager().readConfiguration(logPropsFile);
            }
            catch (Exception ex) {

            }
            finally {

                // Close the properties file
                if (logPropsFile != null) {
                    try {
                        logPropsFile.close();
                    }
                    catch (Exception ex) {
                    }
                }
            }
        }
    }
}
