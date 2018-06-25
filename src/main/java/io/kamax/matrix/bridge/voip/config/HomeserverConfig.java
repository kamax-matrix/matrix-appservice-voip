/*
 * matrix-appservice-voip - Matrix Bridge to VoIP/SMS
 * Copyright (C) 2018 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.matrix.bridge.voip.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URL;
import java.util.List;
import java.util.Objects;

@Configuration
@ConfigurationProperties("matrix.home")
public class HomeserverConfig implements InitializingBean {

    private final Logger log = LoggerFactory.getLogger(HomeserverConfig.class);

    private MatrixConfig mxCfg;
    private URL host;
    private String asToken;
    private String hsToken;
    private String localpart;
    private List<EntityTemplateConfig> users;

    @Autowired
    public HomeserverConfig(MatrixConfig mxCfg) {
        this.mxCfg = mxCfg;
    }

    public String getDomain() {
        return mxCfg.getDomain();
    }

    public URL getHost() {
        return host;
    }

    public void setHost(URL host) {
        this.host = host;
    }

    public String getAsToken() {
        return asToken;
    }

    public void setAsToken(String asToken) {
        this.asToken = asToken;
    }

    public String getHsToken() {
        return hsToken;
    }

    public void setHsToken(String hsToken) {
        this.hsToken = hsToken;
    }

    public String getLocalpart() {
        return localpart;
    }

    public void setLocalpart(String localpart) {
        this.localpart = localpart;
    }

    public List<EntityTemplateConfig> getUsers() {
        return users;
    }

    public void setUsers(List<EntityTemplateConfig> users) {
        this.users = users;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (Objects.isNull(host)) {
            throw new RuntimeException("Matrix HS client endpoint must be configured");
        }

        if (StringUtils.isBlank(asToken)) {
            throw new RuntimeException("Matrix AS token must be configured");
        }

        if (StringUtils.isBlank(hsToken)) {
            throw new RuntimeException("Matrix HS token must be configured");
        }

        if (StringUtils.isBlank(localpart)) {
            throw new RuntimeException("Matrix AS localpart must be configured");
        }

        if (Objects.isNull(users) || users.isEmpty()) {
            throw new RuntimeException("At least one Matrix user template must be configured");
        }

        log.info("Domain: {}", getDomain());
        log.info("Host: {}", getHost());
        log.info("AS Token: {}", getAsToken());
        log.info("HS Token: {}", getHsToken());
        log.info("Localpart: {}", getLocalpart());
        log.info("Users:");
        for (EntityTemplateConfig p : getUsers()) {
            log.info("\t- {}", p.getTemplate());
        }
    }

}
