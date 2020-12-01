/*
 * Copyright (C) 2020 GK Spencer
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

package org.filesys.oncrpc;

import org.filesys.oncrpc.nfs.RpcSessionProcessor;
import org.filesys.oncrpc.nfs.v3.NFS3;
import org.filesys.oncrpc.nfs.v3.NFS3RpcProcessor;
import org.filesys.smb.Dialect;
import org.filesys.smb.server.*;

import java.util.*;

/**
 * RPC Processor Factory Class
 *
 *  * <p>The processor factory class generates RPC handlers for NFS and associated RPC processing.
 *
 * @author gkspencer
 */
public class RpcProcessorFactory {

    // RPC processor handler implementations map
    private static HashMap<Integer, HashMap<Integer, Class>> _processorMap;

    /**
     * RPC Processor Factory constructor
     */
    private RpcProcessorFactory() {
    }

    /**
     * Check if the specified RPC program id is supported
     *
     * @param progId int
     * @return boolean
     */
    public static final boolean supportsRpcProgram(int progId) {

        // Make sure the RPC processor class map is valid
        if ( _processorMap == null)
            return false;

        // Find the required version mappings
        return _processorMap.containsKey( progId);
    }

    /**
     * Check if the specified RPC program id and version is supported
     *
     * @param progId int
     * @param verId int
     * @return boolean
     */
    public static final boolean supportsRpcVersion(int progId, int verId) {

        // Make sure the RPC processor class map is valid
        if ( _processorMap == null)
            return false;

        // Find the required version mappings
        HashMap<Integer, Class> versionMap = _processorMap.get( progId);

        if ( versionMap != null) {

            // Check if the required version is supported
            return versionMap.containsKey( verId);
        }

        return false;
    }

    /**
     * Return the supported version range for the specified RPC program id
     *
     * @param progId int
     * @return int[]
     */
    public static final int[] getSupportedVersionRange( int progId) {

        // Allocate the return version low/high array
        int[] vers = new int[2];

        // Make sure the RPC processor class map is valid
        if ( _processorMap != null) {

            // Find the required version mappings
            HashMap<Integer, Class> versionMap = _processorMap.get( progId);

            if ( versionMap != null) {

                // Get the list of available versions
                Set<Integer> availVers = versionMap.keySet();
                Iterator<Integer> verIter = availVers.iterator();

                // It will only be one or two versions at present
                if ( availVers.size() == 1) {
                    vers[0] = verIter.next();
                    vers[1] = vers[0];
                }
                else {
                    vers[0] = verIter.next();

                    while ( verIter.hasNext())
                        vers[1] = verIter.next();
                }
            }
        }
        else {
            vers[0] = -1;
            vers[1] = -1;
        }

        return vers;
    }

    /**
     * Return an RPC session processor for the specified program and version ids, or null if there is no appropriate processor
     *
     * @param progId int
     * @param verId int
     * @return RpcSessionProcessor
     */
    public static RpcSessionProcessor getRpcSessionProcessor(int progId, int verId) {

        // Make sure the RPC processor class map is valid
        if ( _processorMap == null)
            return null;

        // Find the required version mappings
        HashMap<Integer, Class> versionMap = _processorMap.get( progId);

        if ( versionMap != null) {

            try {

                // Get the RPC processor for the required version
                Class rpcProcClass = versionMap.get( verId);

                // Create the RPC processor
                if ( rpcProcClass != null) {
                    RpcSessionProcessor rpcProc = (RpcSessionProcessor) rpcProcClass.newInstance();
                    return rpcProc;
                }
            }
            catch (Exception ex) {
            }
        }

        return null;
    }

    /**
     * Add an RPC processor class for a particular program id and version
     *
     * @param progId int
     * @param verId int
     * @param rpcProcClass Class
     */
    public static void addRpcProcessorClass( int progId, int verId, Class rpcProcClass) {

        try {

            // Check if the RPC processor class is valid
            if ( rpcProcClass == null || (rpcProcClass.newInstance() instanceof RpcSessionProcessor) == false)
                throw new RuntimeException( "Invalid RPC processor class");
        }
        catch ( Exception ex) {
            throw new RuntimeException( "Error checking RPC processor class type");
        }

        // Check that the RPC processor map is valid
        if ( _processorMap == null)
            throw new RuntimeException( "Invalid RPC processor map");

        // Check if the program id already has an entry
        HashMap<Integer, Class> versionMap = _processorMap.get( progId);

        if ( versionMap == null) {
            versionMap = new HashMap<Integer, Class>(4);
            _processorMap.put(progId, versionMap);
        }

        // Add the RPC processor
        versionMap.put( verId, rpcProcClass);
    }

    /**
     * Remove an RPC processor class for a particular program id and version
     *
     * @param progId int
     * @param verId int
     */
    public static Class removeRpcProcessorClass( int progId, int verId) {

        // Check that the RPC processor map is valid
        if ( _processorMap == null)
            return null;

        // Check if the program id has an entry
        HashMap<Integer, Class> versionMap = _processorMap.get( progId);

        if ( versionMap != null)
            return versionMap.remove( verId);

        // No entry for the specified program id/version id
        return null;
    }

    /**
     * Static initializer
     */
    static {

        // Allocate the RPC processor map
        _processorMap = new HashMap<>(4);
    }
}
