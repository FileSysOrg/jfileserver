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

package org.filesys.client.demo;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

import org.filesys.client.SessionFactory;
import org.filesys.client.*;
import org.filesys.netbios.NetBIOSSession;
import org.filesys.smb.Protocol;
import org.filesys.smb.PCShare;
import org.filesys.util.IPAddress;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * Application Base Class
 *
 * @author gkspencer
 */
public abstract class BaseApp {

    //	Constants
    //
    //	Command line argument names
    public final static String CmdLineArg = "Arg";

    public final static String CmdLineArg1 = CmdLineArg + "1";
    public final static String CmdLineArg2 = CmdLineArg + "2";
    public final static String CmdLineArg3 = CmdLineArg + "3";
    public final static String CmdLineArg4 = CmdLineArg + "4";
    public final static String CmdLineArg5 = CmdLineArg + "5";
    public final static String CmdLineArg6 = CmdLineArg + "6";

    //	User name and password command line switches
    public final static String SwitchUserName   = "username";
    public final static String SwitchPassword   = "password";
    public final static String SwitchDomain     = "domain";

    //	Flag to indciate field should be right aligned
    protected final static int RightAlign = 0x8000;

    //	Properties file name
    protected final static String PropertiesFileName = "client.properties";

    //	Application property names
    protected final static String PropertyUserName      = "UserName";
    protected final static String PropertyPassword      = "Password";
    protected final static String PropertyDomain        = "Domain";
    protected final static String PropertyWINS          = "WINS";
    protected final static String PropertyBroadcast     = "Broadcast";
    protected final static String PropertyDebug         = "DebugEnable";
    protected final static String PropertyResolveOrder  = "ResolveOrder";
    protected final static String PropertyConnectOrder  = "ConnectOrder";
    protected final static String PropertyNameScope     = "NameScope";
    protected final static String PropertyWildcardName  = "WildcardServerName";
    protected final static String PropertyJCEProvider   = "JCEProvider";

    //	Command name and short description
    private String m_cmdName;
    private String m_cmdDesc;

    //	Details of the remote share to connect to
    private PCShare m_share;

    //	Session to the remote server
    private Session m_session;

    // 	Command line switches and parameters
    private NameValueList m_cmdLine;

    //	Flag to indicate if the command uses command line switches, if not then switch processing will
    //	be turned off in the command line parsing method
    private boolean m_cmdSwitches = true;

    //	Buffer for formatting output strings
    private StringBuffer m_fmtBuf;

    //	Properties
    private Properties m_properties;

    /**
     * Class constructor
     *
     * @param name String
     * @param desc String
     */
    public BaseApp(String name, String desc) {
        m_cmdName = name;
        m_cmdDesc = desc;
    }

    /**
     * Class constructor
     *
     * @param name     String
     * @param desc     String
     * @param switches boolean
     */
    public BaseApp(String name, String desc, boolean switches) {
        m_cmdName = name;
        m_cmdDesc = desc;

        m_cmdSwitches = switches;
    }

    /**
     * Return the command name
     *
     * @return String
     */
    public final String getCommandName() {
        return m_cmdName;
    }

    /**
     * Return the command short description
     *
     * @return String
     */
    public final String getCommandDescription() {
        return m_cmdDesc;
    }

    /**
     * Output the command help
     *
     * @param out PrintStream
     */
    protected abstract void outputCommandHelp(PrintStream out);

    /**
     * Perform the specific command processing
     *
     * @param out PrintStream
     * @throws Exception Error running command
     */
    protected abstract void doCommand(PrintStream out)
            throws Exception;

    /**
     * Validate the command line parameters
     *
     * @param cmdLine NameValueList
     * @param out     PrintStream
     * @return boolean
     */
    protected abstract boolean validateCommandLine(NameValueList cmdLine, PrintStream out);

    /**
     * Get a user name from the command line or properties file
     *
     * @return String
     */
    protected final String getUserName() {

        // Check if there is a user name parameter on the command line
        String userName = null;

        NameValue val = m_cmdLine.findItem(SwitchUserName);
        if (val != null) {

            // Use the command line user name value
            userName = val.getValue();
        } else {

            // Check if there is a user name property
            if (m_properties != null)
                userName = m_properties.getProperty(PropertyUserName);
        }

        // Return the user name string
        return userName;
    }

    /**
     * Get a domain name from the command line or properties file
     *
     * @return String
     */
    protected final String getDomain() {

        // Check if there is a domain parameter on the command line
        String domain = null;

        NameValue val = m_cmdLine.findItem(SwitchDomain);
        if (val != null) {

            // Use the command line domain value
            domain = val.getValue();
        } else {

            // Check if there is a domain property
            if (m_properties != null)
                domain = m_properties.getProperty(PropertyDomain);
        }

        // Return the domain string
        return domain;
    }

    /**
     * Get a password from the command line or properties file
     *
     * @return String
     */
    protected final String getPassword() {

        // Check if there is a password parameter on the command line
        String password = null;

        NameValue val = m_cmdLine.findItem(SwitchPassword);
        if (val != null) {

            // Use the command line password value
            password = val.getValue();
        } else {

            // Check if there is a password property
            if (m_properties != null)
                password = m_properties.getProperty(PropertyPassword);
        }

        // Return the password string
        return password;
    }

    /**
     * Load the properties file, if available
     *
     * @param out PrintStream
     * @throws IOException Error loading the properties
     */
    private final void loadProperties(PrintStream out)
            throws IOException {

        // Check if there is a properties file in the current directory
        String dir = System.getProperty("user.dir");
        File propFile = new File(dir + File.separator + PropertiesFileName);
        InputStream propStream = null;

        if (propFile.exists()) {

            // Open the properties file
            propStream = new FileInputStream(propFile);
        } else {

            // Check if properties file exists in the user home directory
            String userHome = System.getProperty("user.home");
            if (userHome != null && userHome.length() > 0) {

                // Check for a properties file
                propFile = new File(userHome + File.separator + PropertiesFileName);
                if (propFile.exists()) {

                    // Open the properties file in the user home directory
                    propStream = new FileInputStream(propFile);
                }
            }
        }

        // Check if the input stream is valid
        if (propStream == null)
            return;

        // Load the properties
        m_properties = new Properties();
        m_properties.load(propStream);
        propStream.close();

        // Check for global properties
        //
        // Debug enable
        String property = m_properties.getProperty(PropertyDebug);

        if (property != null && (property.equals("1") || property.equalsIgnoreCase("true"))) {

            // Enable debug output
            Session.setDebug(Session.DBGDumpPacket + Session.DBGPacketType);
        }

        // WINS server
        property = m_properties.getProperty(PropertyWINS);

        if (property != null && property.length() > 0) {

            // Enable NetBIOS name lookups via WINS
            NetBIOSSession.setDefaultWINSServer(InetAddress.getByName(property));
        }

        // Broadcast mask for NetBIOS name lookups via broadcast
        property = m_properties.getProperty(PropertyBroadcast);

        if (property != null && IPAddress.isNumericAddress(property)) {

            // Set the broadcast mask
            NetBIOSSession.setDefaultSubnetMask(property);
        }

        // Name lookup resolve order
        property = m_properties.getProperty(PropertyResolveOrder);

        if (property != null && property.length() > 0) {

            // Check if there are two values
            property = property.toUpperCase();
            int pos = property.indexOf(",");

            String resolve1 = null;
            String resolve2 = null;

            if (pos != -1) {

                // Split the resolve order strings
                resolve1 = property.substring(0, pos).trim();
                resolve2 = property.substring(pos + 1).trim();
            } else {

                // Only one string
                resolve1 = property.trim();
            }

            // Validate the resolve order string(s)
            if (resolve1.equals("NETBIOS") == false && resolve1.equals("DNS") == false) {

                // Invalid resolve order string
                out.println("%% Invalid ResolveOrder - " + property + " (use NetBIOS and DNS)");
            } else if (resolve2 != null && (resolve2.equals("NETBIOS") == false && resolve2.equals("DNS") == false)) {

                // Invalid resolve order string
                out.println("%% Invalid ResolveOrder - " + property + " (use NetBIOS and DNS)");
            } else {

                // Set the name resolve order values
                NetBIOSSession.LookupType resolveType = NetBIOSSession.LookupType.WINS_AND_DNS;

                if (resolve1.equals("NETBIOS") && resolve2 == null)
                    resolveType = NetBIOSSession.LookupType.WINS_ONLY;
                else if (resolve1.equals("DNS") && resolve2 == null)
                    resolveType = NetBIOSSession.LookupType.DNS_ONLY;

                // Set the resolve order
                NetBIOSSession.setDefaultLookupType(resolveType);
            }
        }

        // Session connection order
        property = m_properties.getProperty(PropertyConnectOrder);

        if (property != null && property.length() > 0) {

            // Check if there are two values
            property = property.toUpperCase();
            int pos = property.indexOf(",");

            String conn1 = null;
            String conn2 = null;

            if (pos != -1) {

                // Split the connection order strings
                conn1 = property.substring(0, pos).trim();
                conn2 = property.substring(pos + 1).trim();
            } else {

                // Only one string
                conn1 = property.trim();
            }

            // Validate the connection order string(s)
            if (conn1.equals("NETBIOS") == false && conn1.equals("TCPIP") == false) {

                // Invalid connection order string
                out.println("%% Invalid ConnectOrder - " + property + " (use NetBIOS and TCPIP)");
            } else if (conn2 != null && (conn2.equals("NETBIOS") == false && conn2.equals("TCPIP") == false)) {

                // Invalid connection order string
                out.println("%% Invalid ConnectOrder - " + property + " (use NetBIOS and TCPIP)");
            } else {

                // Set the session connection order values
                int c1 = conn1.equals("NETBIOS") ? Protocol.TCPNetBIOS : Protocol.NativeSMB;
                int c2 = Protocol.None;

                if (conn2 != null)
                    c2 = conn2.equals(("NETBIOS")) ? Protocol.TCPNetBIOS : Protocol.NativeSMB;

                if (c1 == c2) {

                    // Ignore the second connect order value
                    c2 = Protocol.None;
                }

                // Set the session connection order
                SessionFactory.setProtocolOrder(c1, c2);
            }
        }

        // NetBIOS name scope
        property = m_properties.getProperty(PropertyNameScope);

        if (property != null && property.length() > 0) {

            // Validate the name scope string, should be at least 'a.a'
            if (property.length() < 3 || property.indexOf('.') == -1)
                out.println("%% Invalid NetBIOS name scope - " + property);
            else
                SessionFactory.setNetBIOSNameScope(property);
        }

        // Use wildcard file server name when connecting NetBIOS sessions
        property = m_properties.getProperty(PropertyWildcardName);

        if (property != null && (property.equals("0") || property.equalsIgnoreCase("false"))) {

            // Disable use of the *SMBSERVER wildcard name when connecting to a file server with NetBIOS
            NetBIOSSession.setDefaultWildcardFileServerName(false);
        }

        // JCE provider class name
        property = m_properties.getProperty(PropertyJCEProvider);

        if (property != null && property.length() > 0) {

            // Try and create the JCE provider object and validate
            Provider jceProvider = null;

            try {

                // Create the JCE provider instance
                Object jceObj = Class.forName(property).newInstance();

                // Check if the class is a JCE provider
                if (jceObj instanceof Provider)
                    jceProvider = (Provider) jceObj;
                else
                    out.println("%% JCE provider not a valid provider, " + property);
            }
            catch (ClassNotFoundException ex) {

                // JCE provider class not found
                out.println("%% JCE provider class not found, " + property);
            }
            catch (InstantiationException ex) {

                // JCE provider class error
                out.println("%% JCE provider instantiation error, " + ex.getMessage());
            }
            catch (IllegalAccessException ex) {

                // JCE provider class error
                out.println("%% JCE provider class error, " + ex.getMessage());
            }

            // Set the JCE provider, if valid
            if (jceProvider != null)
                Security.addProvider(jceProvider);
        }
    }

    /**
     * Parse the command line
     *
     * @param args String[]
     * @return int
     */
    protected final int parseCommandLine(String[] args) {

        // Check if the arguments are valid
        if (args == null)
            return 0;

        // Create the command line parameter list
        m_cmdLine = new NameValueList();

        // Process the command line arguments
        int arg = 1;
        int idx = 0;

        while (idx < args.length) {

            // Get the current parameter
            String param = args[idx++];

            if (param.equalsIgnoreCase("-help") || param.equalsIgnoreCase("--help") || param.equalsIgnoreCase("-?")) {

                // Force the command help to be output
                return 0;
            } else if (hasCommandSwitches() && param.startsWith("-")) {

                // Check if the parameter has a value
                String name = null;
                String value = null;

                int pos = param.indexOf("=");
                if (pos != -1) {
                    name = param.substring(1, pos);
                    value = param.substring(pos + 1);
                } else {
                    name = param.substring(1);
                    value = "";
                }

                // Add the parameter details
                m_cmdLine.addItem(new NameValue(name, value));
            } else {

                // Add a new argument
                m_cmdLine.addItem(new NameValue(CmdLineArg + arg++, param));
            }
        }

        // Return the count of command line arguments/switches
        return m_cmdLine.numberOfItems();
    }

    /**
     * Run the command
     *
     * @param args String[]
     */
    public final void runCommand(String[] args) {

        // Get the output stream
        PrintStream out = System.out;
        PrintStream err = System.err;

        // Output the command startup banner
        out.print(getCommandName());
        out.print(" - ");
        out.println(getCommandDescription());

        out.println("Copyright (C) 2006-2010 Alfresco Software Limited.");
        out.println();

        // Load the default properties
        try {
            loadProperties(err);
        }
        catch (Exception ex) {

            // Fail quietly
        }

        // Parse the command line
        int sts = 0;

        if (parseCommandLine(args) == 0) {

            // Output the command help and exit
            outputCommandHelp(out);
            sts = 1;
        }

        // Validate the command line and perform initialization
        else if (validateCommandLine(m_cmdLine, err) == true) {

            // Run the command
            try {
                doCommand(out);
            }
            catch (Exception ex) {
                err.println("Error: " + ex.toString());
                ex.printStackTrace(err);
                sts = 2;
            }

            // Check if there is an active session
            if (hasSession()) {
                try {

                    // Close the session
                    getSession().CloseSession();
                    setSession(null);
                }
                catch (Exception ex) {
                    err.println("Failed to close network session, " + ex.toString());
                }
            }
        }

        // Return the command status
        System.exit(sts);
    }

    /**
     * Prompt for a password, do not echo to the console
     *
     * @return String
     */
    protected final String promptForPassword() {
        return null;
    }

    /**
     * Get the share details
     *
     * @return PCShare
     */
    protected final PCShare getShare() {
        return m_share;
    }

    /**
     * Check if there is an active session
     *
     * @return boolean
     */
    protected final boolean hasSession() {
        return m_session != null ? true : false;
    }

    /**
     * Return the session
     *
     * @return Session
     */
    protected final Session getSession() {
        return m_session;
    }

    /**
     * Check if the command uses command line switches
     *
     * @return boolean
     */
    protected final boolean hasCommandSwitches() {
        return m_cmdSwitches;
    }

    /**
     * Set the share details
     *
     * @param shr PCShare
     */
    protected final void setShare(PCShare shr) {

        // Set the share details
        m_share = shr;

        // Check if the user name and/or password should be set from the command line or properties file
        String user = getUserName();

        if (user != null && shr.getUserName().equals("GUEST"))
            shr.setUserName(user);

        String pass = getPassword();

        if (pass != null && shr.getPassword() == null)
            shr.setPassword(pass);

        // Set the domain from the command line or properties file
        shr.setDomain(getDomain());
    }

    /**
     * Set the active session
     *
     * @param sess Session
     */
    protected final void setSession(Session sess) {
        m_session = sess;
    }

    /**
     * Format an output string and output to the specified stream
     *
     * @param out    PrintStream
     * @param col1   String
     * @param width1 int
     * @param col2   String
     * @param width2 int
     * @param col3   String
     * @param width3 int
     * @param col4   String
     * @param width4 int
     * @param col5   String
     * @param width5 int
     */
    protected final void formatOutput(PrintStream out, String col1, int width1, String col2, int width2, String col3, int width3,
                                      String col4, int width4, String col5, int width5) {

        // Reset the output buffer
        if (m_fmtBuf == null)
            m_fmtBuf = new StringBuffer(100);
        m_fmtBuf.setLength(0);

        // Output the columns
        formatColumn(m_fmtBuf, col1, width1);

        if (col2 != null)
            formatColumn(m_fmtBuf, col2, width2);

        if (col3 != null)
            formatColumn(m_fmtBuf, col3, width3);

        if (col4 != null)
            formatColumn(m_fmtBuf, col4, width4);

        if (col5 != null)
            formatColumn(m_fmtBuf, col5, width5);

        // Output the formatted record
        out.println(m_fmtBuf.toString());
    }

    /**
     * Format an output string and output to the specified stream
     *
     * @param out    PrintStream
     * @param col1   String
     * @param width1 int
     * @param col2   String
     * @param width2 int
     * @param col3   String
     * @param width3 int
     * @param col4   String
     * @param width4 int
     */
    protected final void formatOutput(PrintStream out, String col1, int width1, String col2, int width2, String col3, int width3,
                                      String col4, int width4) {

        // Build and output the formatted record
        formatOutput(out, col1, width1, col2, width2, col3, width3, col4, width4, null, 0);
    }

    /**
     * Format an output string and output to the specified stream
     *
     * @param out    PrintStream
     * @param col1   String
     * @param width1 int
     * @param col2   String
     * @param width2 int
     * @param col3   String
     * @param width3 int
     */
    protected final void formatOutput(PrintStream out, String col1, int width1, String col2, int width2, String col3, int width3) {

        // Build and output the formatted record
        formatOutput(out, col1, width1, col2, width2, col3, width3, null, 0, null, 0);
    }

    /**
     * Format an output string and output to the specified stream
     *
     * @param out    PrintStream
     * @param col1   String
     * @param width1 int
     * @param col2   String
     * @param width2 int
     */
    protected final void formatOutput(PrintStream out, String col1, int width1, String col2, int width2) {

        // Build and output the formatted record
        formatOutput(out, col1, width1, col2, width2, null, 0, null, 0, null, 0);
    }

    /**
     * Format an output string and output to the specified stream
     *
     * @param out  PrintStream
     * @param col1 String
     * @param width1 int
     */
    protected final void formatOutput(PrintStream out, String col1, int width1) {

        // Build and output the formatted record
        formatOutput(out, col1, width1, null, 0, null, 0, null, 0, null, 0);
    }

    /**
     * Format a column of output
     *
     * @param buf   StringBuffer
     * @param str   String
     * @param width int
     */
    private final void formatColumn(StringBuffer buf, String str, int width) {

        // Check if the field should be padded
        if (width == -1) {
            buf.append(str);
            return;
        }

        // Check if the string is wider than the output column
        int fieldLen = width & 0x0FFF;
        boolean rightAlign = (width & RightAlign) != 0 ? true : false;
        if (rightAlign)
            fieldLen--;
        String field = str;

        if (str.length() > fieldLen) {

            // Truncate the string
            field = str.substring(0, fieldLen - 4) + "...";
        }

        // Output the string and pad to the required width
        int padding = fieldLen - field.length();

        if (rightAlign) {

            // Add padding to the front of the field for right alignment
            while (padding-- > 0)
                buf.append(" ");

            // Append the string
            buf.append(field);
            buf.append(" ");
        } else {

            // Left align, append the string first then pad to the required width
            buf.append(field);

            while (padding-- > 0)
                buf.append(" ");
        }
    }
}
