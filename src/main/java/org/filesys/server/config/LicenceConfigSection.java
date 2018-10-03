/*
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.server.config;

/**
 * Licence Configuration Section Class
 *
 * <p>Contains the licence details for an Enterprise file server setup</p>
 *
 * @author gkspencer
 */
public class LicenceConfigSection extends ConfigSection {

    // Core server configuration section name
    public static final String SectionName = "Licence";

    // Licence key
    private String m_licenceKey;

    // Product edition
    private String m_edition = "Standalone";

    // Default public key
    private String m_publicKey =
                    "30819f300d06092a864886f70d010101050003818d003081893032301006" +
                    "072a8648ce3d02002EC311215SHA512withECDSA106052b81040006031e0" +
                    "00440959090c00e7c987e0d61a22793d77bec53c4d181fd728f0b027ce2G" +
                    "028181009c795ec860acd30f84a647ed0feb3ff7a7283eb1252db4d51de2" +
                    "04df23eb768c0e8cab25ba3f2ab55dc91edcb85bb87e6d6b2144aeffe5c0" +
                    "9301e8757f0b14a9cb5447d4bfe4211349dda148b8658d3868597557be4e" +
                    "32cdd4444e1122fa625703RSA4102413SHA512withRSA8b861357437b318" +
                    "64a70085232410832f47159a0b05d7ff71baf4b8258246ef70203010001";

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public LicenceConfigSection(ServerConfiguration config) {
        super( SectionName, config);
    }

    /**
     * Return the licence key
     *
     * @return String
     */
    public final String getLicenceKey() {
        return m_licenceKey;
    }

    /**
     * Get the product edition
     *
     * @return String
     */
    public final String getProductEdition() { return m_edition; }

    /**
     * Get the public key
     *
     * @return String
     */
    public final String getPublicKey() { return m_publicKey; }

    /**
     * Set the licence key
     *
     * @param licKey String
     */
    public final void setLicenceKey(String licKey) {
        m_licenceKey = licKey;
    }

    /**
     * Set the product edition
     *
     * @param edition String
     */
    public final void setProductEdition(String edition) { m_edition = edition; }

    /**
     * Set the public key
     *
     * @param pubKey String
     */
    public final void setPublicKey(String pubKey) { m_publicKey = pubKey; }
}
