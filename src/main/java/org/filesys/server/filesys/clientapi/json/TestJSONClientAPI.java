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

import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.ApiRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/**
 * JSON Client API Test Class
 *
 * @author gkspencer
 */
public class TestJSONClientAPI extends JSONClientAPI {

    /**
     * Test application
     *
     * @param args String[]
     */
    public static void main(String[] args) {

        System.out.println( "JSON Client API Test");
        System.out.println( "--------------------");

        try {

            // Create the JSON client API handler
            JSONClientAPI clientAPI = new TestJSONClientAPI();

            // Open an API file
            ClientAPINetworkFile apiFile = clientAPI.openClientAPIFile( null, null, null);

            // Write a request to the client API file
            String jsonReq = "{ \"type\" = \"GetApiInfo\", \"client_version\" : \"1.0.0\"}";
            System.out.println("Send GetAPIInfo request - response = " + sendRequest( jsonReq, apiFile));

            // Send an unsupported request
            jsonReq = "{ \"type\" = \"CheckOutFile\", \"relative_path\" : \"test\"}";
            System.out.println("Send CheckOut request - response = " + sendRequest( jsonReq, apiFile));

            // Send an empty request
            System.out.println("Send empty request - response = " + sendRequest( "", apiFile));
        }
        catch ( Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Send a request to the client API and read back the response
     *
     * @param req String
     * @param apiFile ClientAPINetworkFile
     * @return String
     */
    public static String sendRequest( String req, ClientAPINetworkFile apiFile)
        throws IOException {

        // Convert the request string to raw bytes
        byte[] jsonByts = req.getBytes();
        apiFile.writeFile( jsonByts, jsonByts.length, 0, 0L);

        // Read the response back from the API file
        byte[] respByts = apiFile.getResponseData();

        String respStr = "";
        if ( respByts != null) {

            // Convert the response to a string and dump out
            respStr = new String(respByts, StandardCharsets.UTF_8);
        }

        return respStr;
    }

    @Override
    public EnumSet<ApiRequest> getSupportedRequests() {
        return EnumSet.of ( ApiRequest.GetApiInfo);
    }

    @Override
    public String getClientAPIVersion() {
        return "1.0.0";
    }

    @Override
    public ContextMenu getContextMenu() {
        return new ContextMenu( "Test Menu", "Test API context menu", ActionIcon.createAppIcon( ActionIcon.APPICON_FILESYSORG_ALFRESCO));
    }
}
