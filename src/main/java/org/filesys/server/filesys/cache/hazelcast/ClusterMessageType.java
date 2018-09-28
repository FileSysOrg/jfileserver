/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache.hazelcast;

/**
 * Cluster Message Types Class
 *
 * <p>Contains the cluster message type ids.
 *
 * @author gkspencer
 */
public class ClusterMessageType {

    // Cluster message types
    public final static int EchoString          = 0;
    public final static int OpLockBreakRequest  = 1;
    public final static int OpLockBreakNotify   = 2;
    public final static int FileStateUpdate     = 3;
    public final static int RenameState         = 4;
    public final static int DataUpdate          = 5;
    public final static int OplockTypeChange    = 6;

    /**
     * Return a message type as a string
     *
     * @param typ int
     * @return String
     */
    public static String getTypeAsString(int typ) {
        String typStr = "";

        switch (typ) {
            case EchoString:
                typStr = "EchoTest";
                break;
            case OpLockBreakRequest:
                typStr = "OpLockBreakRequest";
                break;
            case OpLockBreakNotify:
                typStr = "OpLockBreakNotify";
                break;
            case FileStateUpdate:
                typStr = "FileStateUpdate";
                break;
            case RenameState:
                typStr = "RenameState";
                break;
            case DataUpdate:
                typStr = "DataUpdate";
                break;
            case OplockTypeChange:
                typStr = "OplockTypeChange";
                break;
        }

        return typStr;
    }
}
