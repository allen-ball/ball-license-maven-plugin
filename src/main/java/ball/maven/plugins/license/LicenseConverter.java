package ball.maven.plugins.license;
/*-
 * ##########################################################################
 * License Maven Plugin
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2020 Allen D. Ball
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
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.basic.AbstractBasicConverter;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;

/**
 * {@link AnyLicenseInfo}
 * {@link org.codehaus.plexus.component.configurator.converters.ConfigurationConverter}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@NoArgsConstructor @ToString
public class LicenseConverter extends AbstractBasicConverter {
    private static final Class<?> TYPE = AnyLicenseInfo.class;

    @Override
    public boolean canConvert(Class<?> type) { return type.equals(TYPE); }

    @Override
    public Object fromString(String string) throws ComponentConfigurationException {
        Object object = null;

        try {
            object =
                TYPE.cast(LicenseInfoFactory.parseSPDXLicenseString(string));
        } catch (Exception exception) {
            String message =
                "Unable to convert '" + string + "' to " + TYPE.getName();

            throw new ComponentConfigurationException(message, exception);
        }

        return object;
    }
}
