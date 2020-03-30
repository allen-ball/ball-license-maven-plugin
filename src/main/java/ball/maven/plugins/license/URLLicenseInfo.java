package ball.maven.plugins.license;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * URL {@link org.spdx.rdfparser.license.ExtractedLicenseInfo}
 * implementation.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
public class URLLicenseInfo extends TextLicenseInfo {

    /**
     * Sole constructor.
     *
     * @param   id              The observed license ID.
     * @param   urls            The URLs to add.
     */
    public URLLicenseInfo(String id, String... urls) {
        super(isNotBlank(id) ? id : urls[0],
              isNotBlank(id) ? id : urls[0], urls);
    }
}
