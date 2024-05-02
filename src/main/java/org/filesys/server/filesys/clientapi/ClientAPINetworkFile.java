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

package org.filesys.server.filesys.clientapi;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.smb.SeekType;

import java.io.IOException;

/**
 * Client API Network File Class
 *
 * <p>Receives a request from the client via data written to the file which triggers processing of the request which
 * then returns the response when the file is read by the client.</p>
 *
 * @author gkspencer
 */
public class ClientAPINetworkFile extends NetworkFile {

    // Details of the client API, session and connection
    private ClientAPIInterface m_apiInterface;
    private SrvSession<?> m_sess;
    private TreeConnection m_tree;

    // Current file position
    private long m_filePos;

    // File data
    private byte[] m_data;

    /**
     * Class constructor
     *
     * @param apiInterface ClientAPIInterface
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param fname String
     */
    public ClientAPINetworkFile(ClientAPIInterface apiInterface, SrvSession<?> sess, TreeConnection tree, String fname) {
        super( 0);

        m_apiInterface = apiInterface;
        m_sess = sess;
        m_tree = tree;

        setName( fname);
        setFullName( fname);
        setAttributes(FileAttribute.NTNormal);

        long timeNow = System.currentTimeMillis();

        setCreationDate( timeNow);
        setModifyDate( timeNow);

        // Mark the file as a special client API file
        setStatusFlag( Flags.CLIENT_API, true);
    }

    /**
     * Return the session
     *
     * @return SrvSession
     */
    public final SrvSession<?> getSession() { return m_sess; }

    /**
     * Return the tree connection that the file belongs to
     *
     * @return TreeConnection
     */
    public final TreeConnection getTree() { return m_tree; }

    /**
     * Check if the client API file has any request data
     *
     * @return boolean
     */
    public final boolean hasRequestData() { return m_data != null; }

    /**
     * Return the request data that has been written to the file
     *
     * @return byte[]
     */
    public final byte[] getRequestData() { return m_data; }

    /**
     * Return the response data that has been returned by the client API
     *
     * @return byte[]
     */
    public final byte[] getResponseData() { return m_data; }

    /**
     * Set the response data to be returned to the client when the file is read
     *
     * @param responseData byte[]
     */
    public final void setResponseData( byte[] responseData) {
        m_data = responseData;
    }

    /**
     * Return the current file position.
     *
     * @return long
     */
    public long currentPosition() {
        return m_filePos;
    }

    /**
     * Determine if the end of file has been reached.
     *
     * @return boolean
     * @exception IOException Error during end of file check
     */
    public boolean isEndOfFile()
            throws IOException {

        // Check if we reached end of file
        return m_filePos == m_data.length;
    }

    @Override
    public void openFile(boolean createFlag) throws IOException {

        // Indicate that the file is open
        setClosed(false);
    }

    @Override
    public int readFile(byte[] buf, int len, int pos, long fileOff) throws IOException {

        // Check if the read is within the file data range
        long fileLen = m_data.length;

        if (fileOff >= fileLen)
            return 0;

        // Calculate the actual read length
        if ((fileOff + len) > fileLen)
            len = (int) (fileLen - fileOff);

        // Copy the data to the user buffer
        System.arraycopy(m_data, (int) fileOff, buf, pos, len);

        // Update the current file position
        m_filePos = fileOff + len;

        // Return the actual length of data read
        return len;
    }

    @Override
    public void writeFile(byte[] buf, int len, int pos, long fileOff) throws IOException {

        // Create a buffer for the request data
        m_data = new byte[ len];
        System.arraycopy( buf, pos, m_data, 0, len);

        // Call the client API interface to process the request
        m_apiInterface.processRequest( this);
    }

    @Override
    public long seekFile(long pos, int typ) throws IOException {

        // Seek to the required file position
        switch (typ) {

            // From start of file
            case SeekType.StartOfFile:
                if (currentPosition() != pos)
                    m_filePos = pos;
                break;

            // From current position
            case SeekType.CurrentPos:
                m_filePos += pos;
                break;

            // From end of file
            case SeekType.EndOfFile:
                m_filePos += pos;
                if (m_filePos < 0)
                    m_filePos = 0L;
                break;
        }

        // Return the new file position
        return currentPosition();
    }

    @Override
    public void flushFile()
            throws IOException {

        // Nothing to do
    }

    @Override
    public void truncateFile(long siz) throws IOException {

        // If truncating to zero length then release the current buffer
        if ( siz == 0) {
            m_data = null;
            m_filePos = 0;
        }
        else {

            // Allocate a new buffer, reset the file position
            m_data = new byte[(int) siz];
            m_filePos = 0;
        }
    }

    @Override
    public void closeFile() throws IOException {

        // Mark the file as closed
        setClosed( true);
    }
}
