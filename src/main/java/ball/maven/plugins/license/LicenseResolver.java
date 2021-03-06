package ball.maven.plugins.license;
/*-
 * ##########################################################################
 * License Maven Plugin
 * %%
 * Copyright (C) 2020 - 2022 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.spdx.compare.LicenseCompareHelper;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.DisjunctiveLicenseSet;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.LicenseSet;

/**
 * {@link AnyLicenseInfo} resolver with methods to parse, look-up, and
 * normalize.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@Named @Singleton
@ToString @Slf4j
public class LicenseResolver {
    /** @serial */ private final LicenseMap licenseMap;
    /** @serial */ private final URLLicenseInfoParser urlLicenseInfoParser;

    /**
     * Sole constructor.
     *
     * @param   licenseMap      The injected {@link LicenseMap}.
     * @param   urlLicenseInfoParser
     *                          The injected {@link URLLicenseInfoParser}.
     */
    @Inject
    public LicenseResolver(LicenseMap licenseMap,
                           URLLicenseInfoParser urlLicenseInfoParser) {
        this.licenseMap = Objects.requireNonNull(licenseMap);
        this.urlLicenseInfoParser = Objects.requireNonNull(urlLicenseInfoParser);
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() { }

    /**
     * Method to parse {@link URLLicenseInfo} and
     * {@link ExtractedLicenseInfo} ({@link TextLicenseInfo}) instances.
     *
     * @param   in              The {@link AnyLicenseInfo} to resolve.
     *
     * @return  Parsed {@link AnyLicenseInfo}.
     *
     */
    public AnyLicenseInfo parse(AnyLicenseInfo in) {
        AnyLicenseInfo out = in;

        if (in instanceof LicenseSet) {
            List<AnyLicenseInfo> list = Arrays.asList(((LicenseSet) in).getMembers());

            for (int i = 0, n = list.size(); i < n; i += 1) {
                list.set(i, parse(list.get(i)));
            }

            out = toLicense(list);
        } else if (in instanceof URLLicenseInfo) {
            out = parse((URLLicenseInfo) in);
        } else if (in instanceof TextLicenseInfo) {
            out = parse((TextLicenseInfo) in);
        }

        return out;
    }

    /**
     * Method to parse {@link URLLicenseInfo} instances.
     *
     * @param   in            The {@link URLLicenseInfo} to resolve.
     *
     * @return  Parsed {@link AnyLicenseInfo}.
     */
    public AnyLicenseInfo parse(URLLicenseInfo in) {
        return urlLicenseInfoParser.parse(this, in);
    }

    private AnyLicenseInfo parse(TextLicenseInfo in) {
        String[] ids = parseLicenseText(in.getExtractedText());
        AnyLicenseInfo out = toLicense(ids);

        if (out == null) {
            out = new ExtractedLicenseInfo(in.getLicenseId(), in.getExtractedText());

            TextLicenseInfo.addSeeAlso((ExtractedLicenseInfo) out, in.getSeeAlso());
        }

        return out;
    }

    protected AnyLicenseInfo parseLicenseString(String string) {
        AnyLicenseInfo license = null;

        try {
            license = LicenseInfoFactory.parseSPDXLicenseString(string);
        } catch (Exception exception) {
        }

        return license;
    }

    protected String[] parseLicenseText(String text) {
        String[] ids = new String[] { };

        try {
            ids = LicenseCompareHelper.matchingStandardLicenseIds(text);
        } catch (Exception exception) {
        }

        return ids;
    }

    /**
     * Method to convert a {@link Collection} of {@link AnyLicenseInfo}
     * instances to either a {@link DisjunctiveLicenseSet} (if {@code 0} or
     * {@code 2} or more members) or a single {@link AnyLicenseInfo}.
     *
     * @param   in            The {@link Collection} of
     *                        {@link AnyLicenseInfo} to process.
     *
     * @return  An {@link AnyLicenseInfo} representing the license(s).
     */
    public AnyLicenseInfo toLicense(Collection<AnyLicenseInfo> in) {
        AnyLicenseInfo out = null;

        if (in.size() == 1) {
            out = in.iterator().next();
        } else {
            AnyLicenseInfo[] members = in.stream().toArray(AnyLicenseInfo[]::new);

            Arrays.sort(members, Comparator.comparing(Objects::toString));
            out = new DisjunctiveLicenseSet(members);
        }

        return out;
    }

    private AnyLicenseInfo toLicense(String[] ids) {
        AnyLicenseInfo out = null;

        if (ids != null && ids.length > 0) {
            AnyLicenseInfo[] members = new AnyLicenseInfo[ids.length];

            for (int i = 0; i < members.length; i += 1) {
                members[i] = parseLicenseString(ids[i]);
            }

            out = toLicense(Arrays.asList(members));
        }

        return out;
    }
}
