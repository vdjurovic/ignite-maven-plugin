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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ConfigurationLoader {

    private final ObjectMapper yamlObjectMapper;

    public ConfigurationLoader() {
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    }

    public IgniteConfig loadConfiguration(InputStream in, Log logger) {
        try {
            return yamlObjectMapper.readValue(in, IgniteConfig.class);
        } catch (IOException ex) {
            logger.error("Failed to read configuration file", ex);
            return null;
        }
    }

    public IgniteConfig loadConfiguration(File configFile, Log logger) {
        try{
            InputStream in = new FileInputStream(configFile);
            return loadConfiguration(in, logger);
        } catch(IOException ex) {
            logger.error("Failed to load configuration file", ex);
            return null;
        }
    }
}
