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

package org.filesys.smb.server;

import org.filesys.smb.Dialect;

import java.util.HashMap;

/**
 * SMB Protocol Factory Class.
 *
 * <p>The protocol factory class generates protocol handlers for SMB dialects.
 *
 * @author gkspencer
 */
public class ProtocolFactory {

    // Protocol handler implementations map
    private static HashMap<Integer, Class> _handlerMap;

    /**
     * Protocol Factory constructor
     */
    private ProtocolFactory() {
    }

    /**
     * Return a protocol handler for the specified SMB dialect type, or null if there is no appropriate protocol handler
     *
     * @param dialect int
     * @return ProtocolHandler
     */
    protected static ProtocolHandler getHandler(int dialect) {

        // Make sure the handler class map is valid
        if ( _handlerMap == null)
            return null;

        // Find the required handler class
        Class handlerClass = _handlerMap.get( dialect);

        if ( handlerClass != null) {

            try {
                ProtocolHandler protocolHandler = (ProtocolHandler) handlerClass.newInstance();
                return protocolHandler;
            }
            catch (Exception ex) {
            }
        }

        return null;
    }

    /**
     * Add a protocol handler class for a particular dialect
     *
     * @param dialect int
     * @param handlerClass Class
     */
    public static void addHandlerClass( int dialect, Class handlerClass) {

        try {

            // Check if the handler class is valid
            if ( handlerClass == null || (handlerClass.newInstance() instanceof ProtocolHandler) == false)
                throw new RuntimeException( "Invalid protocol handler class");
        }
        catch ( Exception ex) {
            throw new RuntimeException( "Error checking handler class type");
        }

        // Check that the handler map is valid
        if ( _handlerMap == null)
            throw new RuntimeException( "Invalid protocol handler map");

        // Add the handler
        _handlerMap.put( dialect, handlerClass);
    }

    /**
     * Static initializer
     */
    static {

        // Allocate the handler class map
        _handlerMap = new HashMap<>(16);

        // Add the SMB v1 Core handler
        Class handlerClass = CoreProtocolHandler.class;

        ProtocolFactory.addHandlerClass( Dialect.Core, handlerClass);
        ProtocolFactory.addHandlerClass( Dialect.CorePlus, handlerClass);

        // Add the SMB v1 LanMan handler
        handlerClass = LanManProtocolHandler.class;

        ProtocolFactory.addHandlerClass( Dialect.DOSLanMan1, handlerClass);
        ProtocolFactory.addHandlerClass( Dialect.DOSLanMan2, handlerClass);
        ProtocolFactory.addHandlerClass( Dialect.LanMan1, handlerClass);
        ProtocolFactory.addHandlerClass( Dialect.LanMan2, handlerClass);
        ProtocolFactory.addHandlerClass( Dialect.LanMan2_1, handlerClass);

        // Add the SMB v1 NT handler
        ProtocolFactory.addHandlerClass( Dialect.NT, NTProtocolHandler.class);
    }
}
