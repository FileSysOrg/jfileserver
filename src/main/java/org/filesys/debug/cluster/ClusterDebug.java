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

package org.filesys.debug.cluster;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.filesys.debug.DebugInterface;
import org.filesys.debug.DebugInterfaceBase;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.filesys.cache.hazelcast.ClusterConfigSection;
import org.springframework.extensions.config.ConfigElement;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Member;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;

/**
 * Cluster Debug Interface Class
 *
 * <p>Send debug output to a cluster logger.
 *
 * @author gkspencer
 */
public class ClusterDebug extends DebugInterfaceBase implements MessageListener<DebugClusterMessage> {

    // Default debug message topic name
    public static final String DefaultTopicName = "JFileSrvDebug";

    // Cluster configuration section
    private ClusterConfigSection m_clusterConfig;

    // Cluster name
    private String m_debugTopicName = DefaultTopicName;

    // Cluster topic for debug messages
    private ITopic<DebugClusterMessage> m_debugTopic;

    // Local node address, and name
    private Member m_localNode;
    private String m_localName;

    // Flag to control whether this is a receive only node or sends debug output to the cluster
    private boolean m_receiveOnly;

    // Local debug interface to pipe all debug messages to
    private DebugInterface m_localInterface;

    // Buffer for debugPrint() strings
    private StringBuilder m_printBuf;

    // Prefix for local debug output
    private String m_prefix;

    /**
     * Class constructor
     */
    public ClusterDebug() {
    }

    /**
     * Initialize the debug interface using the specified named parameters.
     *
     * @param params ConfigElement
     * @param config ServerConfiguration
     * @exception Exception Error during initialization
     */
    public void initialize(ConfigElement params, ServerConfiguration config)
            throws Exception {

        // Call the base class initialization
        super.initialize(params, config);

        // Make sure the cluster configuration is valid
        m_clusterConfig = (ClusterConfigSection) config.getConfigSection(ClusterConfigSection.SectionName);

        if (m_clusterConfig == null)
            throw new InvalidConfigurationException("Cluster configuration not available");

        // Allocate the print buffer
        m_printBuf = new StringBuilder(256);

        // Check if the debug topic name has been specfied
        ConfigElement elem = params.getChild("debugTopic");
        if (elem != null && elem.getValue() != null) {

            // Set the debug topic name
            m_debugTopicName = elem.getValue();

            // Validate the topic name
            if (m_debugTopicName == null || m_debugTopicName.length() == 0)
                throw new InvalidConfigurationException("Empty cluster name");
        }

        // Check if this node is receive only
        if (params.getChild("receiveOnly") != null)
            m_receiveOnly = true;

        // Check if there is a local debug interface specified
        elem = params.getChild("localOutput");
        if (elem != null) {

            // Get the debug output class
            ConfigElement debugClass = elem.getChild("class");
            if (debugClass == null)
                throw new InvalidConfigurationException("Class must be specified for debug output");

            //  Validate the local debug output class
            Object obj = Class.forName(debugClass.getValue()).newInstance();

            //  Check if the debug class implements the Debug interface
            if (obj instanceof DebugInterface) {

                //  Initialize the debug output class
                m_localInterface = (DebugInterface) obj;
                m_localInterface.initialize(elem, config);
            } else
                throw new InvalidConfigurationException("Local debug class " + debugClass.getValue() + " does not implement DebugInterface");
        }

        // Initialize the cluster
        //
        // Create/connect to the cluster debug message topic
        HazelcastInstance hzInstance = m_clusterConfig.getHazelcastInstance();
        m_debugTopic = hzInstance.getTopic(m_debugTopicName);

        if (m_debugTopic == null)
            throw new Exception("Failed to initialize cluster topic, " + m_debugTopicName);

        // Set the receiver, to get callbacks when a message is received
        m_localNode = hzInstance.getCluster().getLocalMember();
        m_localName = m_localNode.getSocketAddress().toString();

        // Register to receive cluster messages
        m_debugTopic.addMessageListener(this);

        // Build the local debug output prefix string
        m_prefix = "{" + m_localNode.getSocketAddress().getHostName() + ":" + m_localNode.getSocketAddress().getPort() + "} ";
    }

    /**
     * Close the debug output.
     */
    public void close() {

        // Clear the topic object to stop sending debug output to the cluster
        m_debugTopic = null;
    }

    /**
     * Output a debug string with a specific logging level
     *
     * @param str   String
     * @param level int
     */
    public void debugPrint(String str, int level) {

        // Check if the output should be logged
        if (level <= getLogLevel()) {

            boolean startOfLine = false;

            synchronized (m_printBuf) {

                // Check if we are at the start of a new line
                if (m_printBuf.length() == 0)
                    startOfLine = true;

                // Buffer the cluster output until a newline call
                m_printBuf.append(str);
            }

            // Log locally
            if (m_localInterface != null) {
                if (startOfLine == true)
                    m_localInterface.debugPrint(m_prefix);
                m_localInterface.debugPrint(str, level);
            }
        }
    }

    /**
     * Output a debug string, and a newline, with a specific logging level
     *
     * @param str String
     */
    public void debugPrintln(String str, int level) {

        // Check if the output should be logged
        if (level <= getLogLevel()) {

            // Check if there is data in the holding buffer
            boolean startOfLine = true;

            if (m_printBuf.length() > 0) {

                synchronized (m_printBuf) {

                    // Indicate we are not at the start of a line
                    startOfLine = false;

                    // Append the new string
                    m_printBuf.append(str);
                    logToCluster(m_printBuf.toString());

                    // Reset the holding buffer
                    m_printBuf.setLength(0);
                }
            } else {

                // Send to the cluster
                logToCluster(str);
            }

            // Log locally
            if (m_localInterface != null) {
                if (startOfLine == true)
                    m_localInterface.debugPrint(m_prefix);
                m_localInterface.debugPrintln(str, level);
            }
        }
    }

    /**
     * Output an exception
     *
     * @param ex    Exception
     * @param level int
     */
    public void debugPrintln(Exception ex, int level) {

        // Check if the output should be logged
        if (level <= getLogLevel()) {

            //	Write the exception stack trace records to an in-memory stream
            StringWriter strWrt = new StringWriter();
            ex.printStackTrace(new PrintWriter(strWrt, true));
            String traceString = strWrt.toString();

            // Check if there is data in the holding buffer
            if (m_printBuf.length() > 0) {

                synchronized (m_printBuf) {

                    // Append the exception trace string
                    m_printBuf.append(traceString);
                    logToCluster(m_printBuf.toString());

                    // Reset the holding buffer
                    m_printBuf.setLength(0);
                }
            } else {

                // Send to the cluster
                logToCluster(traceString);
            }

            // Log locally
            if (m_localInterface != null) {
                m_localInterface.debugPrint(m_prefix);
                m_localInterface.debugPrintln(traceString, level);
            }
        }
    }

    /**
     * Send debug output to the cluster
     *
     * @param str String
     */
    protected void logToCluster(String str) {

        // Do not send out if this node is receive only
        if (m_receiveOnly == true || m_debugTopic == null)
            return;

        // Create the message and send to the cluster
        m_debugTopic.publish(new DebugClusterMessage(m_localName, m_prefix + str));
    }

    /**
     * Cluster topic message listener
     *
     * @param msg DebugClusterMessage
     */
    public void onMessage(Message<DebugClusterMessage> msg) {

        // Output the message to the local debug interface
        if (m_localInterface != null) {

            // Output the debug string
            m_localInterface.debugPrintln(msg.getMessageObject().getDebugString());
        }
    }
}
