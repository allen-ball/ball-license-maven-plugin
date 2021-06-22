package ball.maven.plugins.license;
/*-
 * ##########################################################################
 * License Maven Plugin
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2020, 2021 Allen D. Ball
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
import java.io.InputStream;
import java.util.Properties;

/**
 * Common default utility methods.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
public interface DefaultMethods {

    default Properties getXMLProperties(String name) throws Exception {
        Properties properties = new Properties();
        String resource =
            String.format("%1$s.%2$s.xml", getClass().getSimpleName(), name);

        try (InputStream in = getClass().getResourceAsStream(resource)) {
            properties.loadFromXML(in);
        }

        return properties;
    }
}
