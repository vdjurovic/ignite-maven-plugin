/*
 *
 *  * Copyright (c) 2022  Bitshift D.O.O (http://bitshifted.co)
 *  *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package co.bitshifted.appforge.ignite.util;

import co.bitshifted.appforge.ignite.model.IgniteConfig;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationLoaderTest {

    @Test
    void testConfigurationLoadSuccess() {
        Log log = Mockito.mock(Log.class);
        InputStream in = getClass().getResourceAsStream("/test-config.yaml");
        ConfigurationLoader loader = new ConfigurationLoader();
        IgniteConfig result = loader.loadConfiguration(in, log);
        assertNotNull(result);
        assertEquals("module1,module2/test", result.getJvmConfiguration().getAddModules());
        assertEquals(2, result.getJvmConfiguration().getJlinkIgnoreModules().size());
        assertTrue(result.getJvmConfiguration().getJlinkIgnoreModules().contains("ignore-module-1"));
    }
}
