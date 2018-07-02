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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;

@Configuration
@ConfigurationProperties("matrix")
public class MatrixConfig {

    private transient final Logger log = LoggerFactory.getLogger(MatrixConfig.class);

    private String domain;
    private List<EntityTemplateConfig> users;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public List<EntityTemplateConfig> getUsers() {
        return users;
    }

    public void setUsers(List<EntityTemplateConfig> users) {
        this.users = users;
    }

    @PostConstruct
    public void validate() {
        if (StringUtils.isBlank(domain)) {
            throw new RuntimeException("Matrix domain must be configured");
        }

        if (Objects.isNull(users) || users.isEmpty()) {
            throw new RuntimeException("At least one Matrix user template must be configured");
        }

        log.info("Users:");
        for (EntityTemplateConfig p : getUsers()) {
            log.info("\t- {}", p.getTemplate());
        }
    }

}
