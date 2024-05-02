/*
 * Copyright (C) 2023 GK Spencer
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.FileOpenParams;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.server.filesys.clientapi.ClientAPIInterface;
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.ApiRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/**
 * JSON Client API Class
 *
 * <p>Implements a client API using JSON format request/response messages</p>
 *
 * @author gkspencer
 */
public abstract class JSONClientAPI implements ClientAPIInterface {

    // Special file path that is used to send a request to the client API and receives the response
    private static final String JSONAPIFileName = "__JSONAPI__";
    private static final String JSONAPIPath = "\\" + JSONAPIFileName;

    // GSON builder and interface
    private GsonBuilder m_builder;
    private Gson m_gson;

    // Debug output enable
    private boolean m_debug;

    /**
     * Default constructor
     */
    public JSONClientAPI() {

        // Crete the GSON builder
        m_builder= new GsonBuilder();
        m_builder.setPrettyPrinting();

        // Register the custom type
        m_builder.registerTypeAdapter( ClientAPIRequest.class, new ClientRequestJsonSerDeser());
        m_builder.registerTypeAdapter( ClientAPIResponse.class, new ClientResponseJsonSerDeser());

        m_gson = m_builder.create();
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() { return m_debug; }

    @Override
    public String getClientAPIPath() {
        return JSONAPIPath;
    }

    @Override
    public ClientAPINetworkFile openClientAPIFile(SrvSession<?> sess, TreeConnection tree, FileOpenParams params)
        throws IOException {

        // Create the client API file that will receive the requests from the client and return the response
        return new ClientAPINetworkFile( this, sess, tree, JSONAPIFileName);
    }

    @Override
    public void processRequest(ClientAPINetworkFile netFile) throws IOException {

        // Get the raw request data from the file
        byte[] reqByts = netFile.getRequestData();
        if ( reqByts == null || reqByts.length == 0) {
            sendErrorToClient(netFile, "Empty request");
            return;
        }

        try {
            // Convert the request bytes to a string
            String reqStr = new String(reqByts, StandardCharsets.UTF_8);

            // Parse the JSON data and create a request object
            ClientAPIRequest apiReq = m_gson.fromJson(reqStr, ClientAPIRequest.class);

            // DEBUG
            if ( hasDebug())
                Debug.println( "[JSONClientAPI] Received request=" + apiReq.toString());

            // Call the pre-processing hook to do any setup
            preProcessRequest( netFile, apiReq);

            // Process the request, generate a response object
            ClientAPIResponse apiResp = null;

            switch (apiReq.isType()) {

                // Get API information
                case GetApiInfo:
                    apiResp = processGetAPIInfo((GetAPIInfoRequest) apiReq);
                    break;

                // Check out files
                case CheckOutFile:
                    apiResp = processCheckOutFile((CheckOutFileRequest) apiReq);
                    break;

                // Check if files
                case CheckInFile:
                    apiResp = processCheckInFile((CheckInFileRequest) apiReq);
                    break;

                // Cancel check out of files
                case CancelCheckOut:
                    apiResp = processCancelCheckOut((CancelCheckOutFileRequest) apiReq);
                    break;

                // Get a URL for a file/folder
                case GetUrlForPath:
                    apiResp = processGetURLForPath((GetURLForPathRequest) apiReq);
                    break;

                // Get file status for a list of paths
                case GetPathStatus:
                    apiResp = processGetPathStatus((GetPathStatusRequest) apiReq);
                    break;
            }

            // DEBUG
            if ( hasDebug())
                Debug.println("[JSONClientAPI] Returning response=" + apiResp);

            // Convert the response to byte data for the client to read back from the file
            String respStr = m_gson.toJson(apiResp, ClientAPIResponse.class);

            // Get the response bytes and return via the client API network file
            byte[] errByts = respStr.getBytes();
            netFile.setResponseData(errByts);
        }
        catch ( ClientAPIException ex) {

            // Return the error/warning to the client
            sendErrorToClient( netFile, ex);
        }
        catch ( JsonParseException ex) {

            // Return the error to the client
            sendErrorToClient( netFile, "Error processing request - " + ex.getMessage());
        }
    }

    /**
     * Request pre-processing hook
     *
     * @param netFile ClientAPINetworkFile
     * @param req ClientAPIRequest
     */
    protected void preProcessRequest( ClientAPINetworkFile netFile, ClientAPIRequest req) {
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg boolean
     */
    public final void setDebug( boolean dbg) { m_debug = dbg; }

    /**
     * Get the list of supported requests by this client API implementation
     *
     * @return EnumSet&lt;RequestId&gt;
     */
    public abstract EnumSet<ApiRequest> getSupportedRequests();

    /**
     * Get the client API version
     *
     * @return String
     */
    public abstract String getClientAPIVersion();

    /**
     * Process a get API information request
     *
     * @param req GetAPIInfoRequest
     * @return ClientAPIResponse
     * @throws ClientAPIException If an error occurs
     */
    protected ClientAPIResponse processGetAPIInfo( GetAPIInfoRequest req)
        throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println("[JSONClientAPI] Process GetAPIInfo request");

        // Return details of the server client API version and supported request types
        return new GetAPIInfoResponse( getClientAPIVersion(), getSupportedRequests());
    }

    /**
     * Process a checkout file request
     *
     * @param req CheckOutFileRequest
     * @return ClientAPIResponse
     * @throws ClientAPIException If an error occurs
     */
    public ClientAPIResponse processCheckOutFile( CheckOutFileRequest req)
        throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println("[JSONClientAPI] Process CheckOutFile request");

        // Return an unsupported error
        return new ErrorResponse( "Not supported");
    }

    /**
     * Process a check in file request
     *
     * @param req CheckInFileRequest
     * @return ClientAPIResponse
     * @throws ClientAPIException If an error occurs
     */
    public ClientAPIResponse processCheckInFile( CheckInFileRequest req)
        throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println("[JSONClientAPI] Process CheckInFile request");

        // Return an unsupported error
        return new ErrorResponse( "Not supported");
    }

    /**
     * Process a cancel check out file request
     *
     * @param req CancelCheckOutFileRequest
     * @return ClientAPIResponse
     * @throws ClientAPIException If an error occurs
     */
    public ClientAPIResponse processCancelCheckOut( CancelCheckOutFileRequest req)
            throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println("[JSONClientAPI] Process CancelCheckOutFile request");

        // Return an unsupported error
        return new ErrorResponse( "Not supported");
    }

    /**
     * Process a get URL for path request
     *
     * @param req GetURLForPathRequest
     * @return ClientAPIResponse
     * @throws ClientAPIException If an error occurs
     */
    public ClientAPIResponse processGetURLForPath( GetURLForPathRequest req)
        throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println("[JSONClientAPI] Process GetURLForPath request");

        // Return an unsupported error
        return new ErrorResponse( "Not yet implemented", true);
    }

    /**
     * Process a get path status request
     *
     * @param req GePathStatusRequest
     * @return ClientAPIResponse
     * @throws ClientAPIException If an error occurs
     */
    public ClientAPIResponse processGetPathStatus( GetPathStatusRequest req)
            throws ClientAPIException {

        // DEBUG
        if (hasDebug())
            Debug.println("[JSONClientAPI] Process GetPathStatus request");

        // Return an unsupported error
        return new ErrorResponse("Not yet implemented", true);
    }

    /**
     * Return an error to the client via the client API file
     *
     * @param netFile ClientAPINetworkFile
     * @param errMsg String
     */
    protected void sendErrorToClient( ClientAPINetworkFile netFile, String errMsg) {

        // DEBUG
        if ( hasDebug())
            Debug.println("[JSONClientAPI] Return error msg=" + errMsg);

        // Build the error response
        ErrorResponse errResp = new ErrorResponse( errMsg);

        // Get the error response JSON
        String respStr = m_gson.toJson( errResp);

        // Get the response bytes and return via the client API network file
        byte[] errByts = respStr.getBytes();

        netFile.setResponseData( errByts);
    }

    /**
     * Return an error to the client via the client API file
     *
     * @param netFile ClientAPINetworkFile
     * @param apiEx ClientAPIException
     */
    protected void sendErrorToClient( ClientAPINetworkFile netFile, ClientAPIException apiEx) {

        // DEBUG
        if (hasDebug())
            Debug.println("[JSONClientAPI] Return error=" + apiEx);

        // Build the error response
        ErrorResponse errResp = new ErrorResponse(apiEx);

        // Get the error response JSON
        String respStr = m_gson.toJson(errResp);

        // Get the response bytes and return via the client API network file
        byte[] errByts = respStr.getBytes();

        netFile.setResponseData(errByts);
    }
}
