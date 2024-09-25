/*
 * Copyright (C) 2024 GK Spencer
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

package org.filesys.server.filesys.clientapi.json;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;

/**
 * Action Icon Class
 *
 * <p>Contains details of a client side icon to be used by a server action context menu</p>
 *
 * @author gkspencer
 */
public class ActionIcon {

    // Application icon indexes
    public static final int APPICON_FILESYSORG_ALFRESCO = -1;
    public static final int APPICON_FILESYSORG          = -2;
    public static final int APPICON_ALFRESCO_CHECKIN    = -3;
    public static final int APPICON_ALFRESCO_CHECKOUT   = -4;

    // Shell icon indexes
    public static final int SHELLICON_SCRIPT            = -16822;
    public static final int SHELLICON_WEBBROWSER        = -512;
    public static final int SHELLICON_FOLDER            = -4;
    public static final int SHELLICON_PRINTER           = -17;
    public static final int SHELLICON_MAGNIFY           = -23;
    public static final int SHELLICON_STAR              = -44;
    public static final int SHELLICON_LOCK              = -48;
    public static final int SHELLICON_MOVIE             = -224;
    public static final int SHELLICON_AUDIO             = -225;
    public static final int SHELLICON_CAMERA            = -226;
    public static final int SHELLICON_INFORMATION       = -16783;
    public static final int SHELLICON_HOME              = -51380;
    public static final int SHELLICON_GEAR              = -16826;

    // Mapping of icon name to action icon
    private static HashMap<String, ActionIcon> _iconMap;

    /**
     * Icon Types Enum Class
     */
    public enum IconType {
        App,        // built in icons, in the client side application DLL
        Shell,      // icon in the Windows shell32.dll
        Custom      // icon in another system DLL
    }

    // Icon type
    @SerializedName( value = "icon_type")
    private IconType m_iconType;

    // Icon index
    @SerializedName( value = "icon_index")
    private int m_iconIndex;

    // Optional icon DLL name/path
    @SerializedName( value = "icon_lib")
    private String m_iconLib;

    /**
     * Class constructor
     *
     * @param iconTyp IconType
     * @param iconIdx int
     * @param iconLib String
     */
    protected ActionIcon( IconType iconTyp, int iconIdx, String iconLib ) {
        m_iconType = iconTyp;
        m_iconIndex = iconIdx;
        m_iconLib = iconLib;
    }

    /**
     * Return the icon type
     *
     * @return IconType
     */
    public IconType getIconType() { return m_iconType; }

    /**
     * Return the icon index
     *
     * @return int
     */
    public int getIconIndex() { return m_iconIndex; }

    /**
     * Return the icon library name
     *
     * @return String
     */
    public final String getIconLib() { return m_iconLib; }

    /**
     * Create an application icon
     *
     * @param iconIdx int
     * @return ActionIcon
     */
    public static ActionIcon createAppIcon( int iconIdx) {
        return new ActionIcon( IconType.App, iconIdx, null);
    }

    /**
     * Create a shell icon
     *
     * @param iconIdx int
     * @return ActionIcon
     */
    public static ActionIcon createShellIcon( int iconIdx) {
        return new ActionIcon( IconType.Shell, iconIdx, null);
    }

    /**
     * Create a custom icon
     *
     * @param iconIdx int
     * @param iconLib String
     * @return ActionIcon
     */
    public static ActionIcon createCustomIcon( int iconIdx, String iconLib) {
        return new ActionIcon( IconType.Custom, iconIdx, iconLib);
    }

    /**
     * Create an action icon by name
     *
     * @param name String
     * @return ActionIcon
     */
    public static ActionIcon createByName( String name) {

        // Get the icon mapping, if valid
        return _iconMap.get( name.toUpperCase());
    }

    /**
     * Return the action icon details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Icon type=");
        str.append( getIconType());
        str.append(", index=");
        str.append( getIconIndex());

        if ( getIconType() == IconType.Custom) {
            str.append(", lib=");
            str.append( getIconLib());
        }
        str.append("]");

        return str.toString();
    }

    /**
     * Static initializer
     */
    static {

        // Initialise the icon map
        _iconMap = new HashMap<String, ActionIcon>( 32);

        _iconMap.put( "FILESYSORGALFRESCO", createAppIcon( APPICON_FILESYSORG_ALFRESCO));
        _iconMap.put( "FILESYSORG", createAppIcon( APPICON_FILESYSORG));
        _iconMap.put( "ALFRESCOCHECKIN", createAppIcon( APPICON_ALFRESCO_CHECKIN));
        _iconMap.put( "ALFRESCOCHECKOUT", createAppIcon( APPICON_ALFRESCO_CHECKOUT));

        _iconMap.put( "SHELLSCRIPT", createShellIcon( SHELLICON_SCRIPT));
        _iconMap.put( "SHELLWEBBROWSER", createShellIcon( SHELLICON_WEBBROWSER));
        _iconMap.put( "SHELLFOLDER", createShellIcon( SHELLICON_FOLDER));
        _iconMap.put( "SHELLPRINTER", createShellIcon( SHELLICON_PRINTER));
        _iconMap.put( "SHELLMAGNIFY", createShellIcon( SHELLICON_MAGNIFY));
        _iconMap.put( "SHELLSTAR", createShellIcon( SHELLICON_STAR));
        _iconMap.put( "SHELLOCK", createShellIcon( SHELLICON_LOCK));
        _iconMap.put( "SHELLMOVIE", createShellIcon( SHELLICON_MOVIE));
        _iconMap.put( "SHELLAUDIO", createShellIcon( SHELLICON_AUDIO));
        _iconMap.put( "SHELLCAMERA", createShellIcon( SHELLICON_CAMERA));
        _iconMap.put( "SHELLINFORMATION", createShellIcon( SHELLICON_INFORMATION));
        _iconMap.put( "SHELLHOME", createShellIcon( SHELLICON_HOME));
        _iconMap.put( "SHELLGEAR", createShellIcon( SHELLICON_GEAR));
    }
}
