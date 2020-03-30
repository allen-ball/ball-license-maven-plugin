package ball.maven.plugins.license;

import java.util.Objects;
import java.util.stream.Stream;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.DisjunctiveLicenseSet;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * {@link URLLicenseInfo} {@link org.spdx.rdfparser.license.LicenseSet}
 * implementation.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
public class URLLicenseSet extends DisjunctiveLicenseSet {

    /**
     * Construct a {@link org.spdx.rdfparser.license.LicenseSet} consisting
     * of {@link URLLicenseInfo} instances from {@link URLLicenseInfo}
     * {@link Stream}.
     *
     * @param   id              The specified license ID (may be null).
     *                          Overrides the ID of the first specified
     *                          license or creates a license if the stream
     *                          is empty.
     * @param   url             The specified license URL (may be null).
     *                          Overrides the name of the first specified
     *                          license or creates a license if the stream
     *                          is empty.
     * @param   stream          The {@link URLLicenseInfo} {@link Stream}.
     */
    public URLLicenseSet(String id, String url,
                         Stream<URLLicenseInfo> stream) {
        this(id, url, stream.toArray(URLLicenseInfo[]::new));
    }

    /**
     * Construct a {@link org.spdx.rdfparser.license.LicenseSet} consisting
     * of {@link URLLicenseInfo} instances from {@link URLLicenseInfo}
     * {@link Stream}.
     *
     * @param   stream          The {@link URLLicenseInfo} {@link Stream}.
     */
    public URLLicenseSet(Stream<URLLicenseInfo> stream) {
        this(null, null, stream);
    }

    /**
     * Construct a {@link org.spdx.rdfparser.license.LicenseSet} consisting
     * of {@link URLLicenseInfo} instances from {@link URLLicenseInfo}
     * arguments.
     *
     * @param   id              The specified license ID (may be null).
     *                          Overrides the ID of the first specified
     *                          license or creates a license if the stream
     *                          is empty.
     * @param   url             The specified license URL (may be null).
     *                          Overrides the name of the first specified
     *                          license or creates a license if the stream
     *                          is empty.
     * @param   licenses        The {@link URLLicenseInfo}s.
     */
    public URLLicenseSet(String id, String url, URLLicenseInfo... licenses) {
        super(new AnyLicenseInfo[] { });

        if (licenses != null && licenses.length > 0) {
            if (isNotBlank(id)) {
                licenses[0].setLicenseId(id);
            }

            if (isNotBlank(url)) {
                licenses[0].setSeeAlso(new String[] { url });
            }
        } else {
            if (isNotBlank(id) || isNotBlank(url)) {
                licenses =
                    new URLLicenseInfo[] { new URLLicenseInfo(id, url) };
            }
        }

        try {
            setMembers(licenses);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
