/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cloudbeaver.service.oidc.auth;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.security.SMAuthProviderCustomConfiguration;
import org.jkiss.utils.CommonUtils;

public class OidcSettings {
    @NotNull
    private final SMAuthProviderCustomConfiguration providerConfiguration;
    @NotNull
    private final String issuerUrl;
    @NotNull
    private final String clientId;
    @NotNull
    private final String clientSecret;
    @NotNull
    private final String scope;
    @NotNull
    private final String redirectUri;
    @NotNull
    private final String groupsClaim;
    @NotNull
    private final String usernameClaim;
    @NotNull
    private final String nameClaim;
    @NotNull
    private final String logoutUrl;

    public OidcSettings(SMAuthProviderCustomConfiguration providerConfiguration) {
        this.providerConfiguration = providerConfiguration;
        this.issuerUrl = providerConfiguration.getParameter(OidcConstants.PARAM_ISSUER_URL);
        this.clientId = providerConfiguration.getParameter(OidcConstants.PARAM_CLIENT_ID);
        this.clientSecret = providerConfiguration.getParameter(OidcConstants.PARAM_CLIENT_SECRET);
        this.scope = providerConfiguration.getParameterOrDefault(OidcConstants.PARAM_SCOPE, "openid profile email");
        this.redirectUri = providerConfiguration.getParameter(OidcConstants.PARAM_REDIRECT_URI);
        this.groupsClaim = providerConfiguration.getParameterOrDefault(OidcConstants.PARAM_GROUPS_CLAIM, "groups");
        this.usernameClaim = providerConfiguration.getParameterOrDefault(OidcConstants.PARAM_USERNAME_CLAIM, "preferred_username");
        this.nameClaim = providerConfiguration.getParameterOrDefault(OidcConstants.PARAM_NAME_CLAIM, "name");
        this.logoutUrl = providerConfiguration.getParameterOrDefault(OidcConstants.PARAM_LOGOUT_URL, "");
    }

    @NotNull
    public String getIssuerUrl() {
        return issuerUrl;
    }

    @NotNull
    public String getClientId() {
        return clientId;
    }

    @NotNull
    public String getClientSecret() {
        return clientSecret;
    }

    @NotNull
    public String getScope() {
        return scope;
    }

    @NotNull
    public String getRedirectUri() {
        return redirectUri;
    }

    @NotNull
    public String getGroupsClaim() {
        return groupsClaim;
    }

    @NotNull
    public String getUsernameClaim() {
        return usernameClaim;
    }

    @NotNull
    public String getNameClaim() {
        return nameClaim;
    }

    @NotNull
    public String getLogoutUrl() {
        return logoutUrl;
    }

    @NotNull
    public SMAuthProviderCustomConfiguration getProviderConfiguration() {
        return providerConfiguration;
    }

    /**
     * Returns the discovery endpoint URL for OIDC
     */
    @NotNull
    public String getDiscoveryUrl() {
        String baseUrl = issuerUrl;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + ".well-known/openid-configuration";
    }

    /**
     * Returns the authorization endpoint URL
     */
    @NotNull
    public String getAuthorizationUrl() {
        return issuerUrl.endsWith("/") ? issuerUrl + "auth" : issuerUrl + "/auth";
    }

    /**
     * Returns the token endpoint URL
     */
    @NotNull
    public String getTokenUrl() {
        return issuerUrl.endsWith("/") ? issuerUrl + "token" : issuerUrl + "/token";
    }

    /**
     * Returns the user info endpoint URL
     */
    @NotNull
    public String getUserInfoUrl() {
        return issuerUrl.endsWith("/") ? issuerUrl + "userinfo" : issuerUrl + "/userinfo";
    }

    /**
     * Returns the end session (logout) endpoint URL
     */
    @NotNull
    public String getEndSessionUrl() {
        if (CommonUtils.isNotEmpty(logoutUrl)) {
            return logoutUrl;
        }
        return issuerUrl.endsWith("/") ? issuerUrl + "logout" : issuerUrl + "/logout";
    }
}
