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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.cloudbeaver.DBWUserIdentity;
import io.cloudbeaver.auth.SMAuthProviderAssigner;
import io.cloudbeaver.auth.SMAuthProviderExternal;
import io.cloudbeaver.auth.SMAutoAssign;
import io.cloudbeaver.model.session.WebSession;
import io.cloudbeaver.model.user.WebUser;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.auth.SMSession;
import org.jkiss.dbeaver.model.data.json.JSONUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.security.SMAuthProviderCustomConfiguration;
import org.jkiss.dbeaver.model.security.SMController;
import org.jkiss.utils.CommonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OidcAuthProvider implements SMAuthProviderExternal<SMSession>, SMAuthProviderAssigner {
    private static final Log log = Log.getLog(OidcAuthProvider.class);
    public static final String OIDC_AUTH_PROVIDER_ID = "oidc";

    private static final Gson gson = new Gson();

    public OidcAuthProvider() {
    }

    @NotNull
    @Override
    public Map<String, Object> authExternalUser(
        @NotNull DBRProgressMonitor monitor,
        @Nullable SMAuthProviderCustomConfiguration providerConfig,
        @NotNull Map<String, Object> authParameters
    ) throws DBException {
        if (providerConfig == null) {
            throw new DBException("OIDC provider config is null");
        }

        String accessToken = JSONUtils.getString(authParameters, OidcConstants.CRED_ACCESS_TOKEN);
        if (CommonUtils.isEmpty(accessToken)) {
            throw new DBException("OIDC access token is empty");
        }

        OidcSettings oidcSettings = new OidcSettings(providerConfig);

        // Get user info from OIDC provider
        Map<String, Object> userInfo = getUserInfo(accessToken, oidcSettings);

        // Extract username and groups
        String userName = extractUserName(userInfo, oidcSettings);
        List<String> groups = extractGroups(userInfo, oidcSettings);

        Map<String, Object> result = new HashMap<>();
        result.put(OidcConstants.CRED_USER_NAME, userName);
        result.put(OidcConstants.CRED_ACCESS_TOKEN, accessToken);
        result.put(OidcConstants.CRED_USER_GROUPS, groups);
        
        // Add email if available
        String email = JSONUtils.getString(userInfo, "email");
        if (CommonUtils.isNotEmpty(email)) {
            result.put(OidcConstants.CRED_USER_EMAIL, email);
        }

        return result;
    }

    @NotNull
    @Override
    public SMAutoAssign detectAutoAssignments(
        @NotNull DBRProgressMonitor monitor,
        @NotNull SMAuthProviderCustomConfiguration providerConfig,
        @NotNull Map<String, Object> authParameters
    ) throws DBException {
        List<String> externalTeamIds = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) authParameters.get(OidcConstants.CRED_USER_GROUPS);
        
        if (groups != null) {
            // Map Keycloak groups to DBeaver teams
            // Expected mapping: admin -> admin, team1 -> team1, team2 -> team2
            for (String group : groups) {
                // Remove leading slash if present (Keycloak format)
                String groupName = group.startsWith("/") ? group.substring(1) : group;
                
                // Only map known groups (admin, team1, team2)
                if ("admin".equals(groupName) || "team1".equals(groupName) || "team2".equals(groupName)) {
                    externalTeamIds.add(groupName);
                }
            }
        }

        SMAutoAssign smAutoAssign = new SMAutoAssign();
        externalTeamIds.forEach(smAutoAssign::addExternalTeamId);
        return smAutoAssign;
    }

    @Nullable
    @Override
    public String getExternalTeamIdMetadataFieldName() {
        return OidcConstants.OIDC_META_GROUP_NAME;
    }

    @Override
    public boolean isAutoUserProvisioningEnabled(@Nullable SMAuthProviderCustomConfiguration providerConfig) {
        // Auto-registration is disabled as per requirements
        return false;
    }

    @NotNull
    @Override
    public DBWUserIdentity getUserIdentity(
        @NotNull DBRProgressMonitor monitor,
        @Nullable SMAuthProviderCustomConfiguration customConfiguration,
        @NotNull Map<String, Object> authParameters
    ) throws DBException {
        String userName = JSONUtils.getString(authParameters, OidcConstants.CRED_USER_NAME);
        if (CommonUtils.isEmpty(userName)) {
            throw new DBException("OIDC user name is empty");
        }
        
        String email = JSONUtils.getString(authParameters, OidcConstants.CRED_USER_EMAIL);
        String displayName = CommonUtils.isNotEmpty(email) ? email : userName;

        return new DBWUserIdentity(userName, displayName);
    }

    @Nullable
    @Override
    public DBPObject getUserDetails(
        @NotNull DBRProgressMonitor monitor,
        @NotNull WebSession webSession,
        @NotNull SMSession session,
        @NotNull WebUser user,
        boolean selfIdentity
    ) throws DBException {
        return null;
    }

    @NotNull
    @Override
    public String validateLocalAuth(
        @NotNull DBRProgressMonitor monitor,
        @NotNull SMController securityController,
        @NotNull SMAuthProviderCustomConfiguration providerConfig,
        @NotNull Map<String, Object> userCredentials,
        @Nullable String activeUserId
    ) throws DBException {
        String userId = JSONUtils.getString(userCredentials, OidcConstants.CRED_USER_NAME);
        if (CommonUtils.isEmpty(userId)) {
            throw new DBException("OIDC user name is empty");
        }
        return userId;
    }

    /**
     * Get sign-in link for OIDC provider
     */
    @NotNull
    public String getSignInLink(@NotNull String providerId, @NotNull String origin) throws DBException {
        // This method will be called by the federated auth flow
        // The actual implementation depends on how the redirect is handled
        SMAuthProviderCustomConfiguration config = new SMAuthProviderCustomConfiguration(providerId);
        OidcSettings settings = new OidcSettings(config);
        
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        
        StringBuilder authUrl = new StringBuilder(settings.getAuthorizationUrl());
        authUrl.append("?client_id=").append(URLEncoder.encode(settings.getClientId(), StandardCharsets.UTF_8));
        authUrl.append("&redirect_uri=").append(URLEncoder.encode(settings.getRedirectUri(), StandardCharsets.UTF_8));
        authUrl.append("&response_type=code");
        authUrl.append("&scope=").append(URLEncoder.encode(settings.getScope(), StandardCharsets.UTF_8));
        authUrl.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        authUrl.append("&nonce=").append(URLEncoder.encode(nonce, StandardCharsets.UTF_8));
        
        return authUrl.toString();
    }

    /**
     * Get sign-out link for OIDC provider
     */
    public String getCommonSignOutLink(
        @NotNull String providerId,
        @NotNull Map<String, Object> providerConfig,
        @NotNull String origin
    ) throws DBException {
        SMAuthProviderCustomConfiguration config = new SMAuthProviderCustomConfiguration(providerId);
        OidcSettings settings = new OidcSettings(config);
        return settings.getEndSessionUrl();
    }

    /**
     * Exchange authorization code for tokens
     */
    public Map<String, Object> exchangeCodeForTokens(
        String code,
        OidcSettings settings,
        String redirectUri
    ) throws DBException {
        try {
            URL url = new URL(settings.getTokenUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String requestBody = "grant_type=authorization_code" +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&client_id=" + URLEncoder.encode(settings.getClientId(), StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(settings.getClientSecret(), StandardCharsets.UTF_8);

            try (var os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorResponse = readResponse(conn.getErrorStream());
                throw new DBException("Token exchange failed: " + responseCode + " - " + errorResponse);
            }

            String response = readResponse(conn.getInputStream());
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = gson.fromJson(response, Map.class);
            
            return tokenResponse;
        } catch (IOException e) {
            throw new DBException("Failed to exchange code for tokens", e);
        }
    }

    /**
     * Get user info from OIDC provider
     */
    private Map<String, Object> getUserInfo(String accessToken, OidcSettings settings) throws DBException {
        try {
            URL url = new URL(settings.getUserInfoUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorResponse = readResponse(conn.getErrorStream());
                throw new DBException("User info request failed: " + responseCode + " - " + errorResponse);
            }

            String response = readResponse(conn.getInputStream());
            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = gson.fromJson(response, Map.class);
            
            return userInfo;
        } catch (IOException e) {
            throw new DBException("Failed to get user info", e);
        }
    }

    /**
     * Extract username from user info based on configuration
     */
    private String extractUserName(Map<String, Object> userInfo, OidcSettings settings) {
        String claimName = settings.getUsernameClaim();
        Object userNameObj = userInfo.get(claimName);
        
        if (userNameObj != null) {
            return userNameObj.toString();
        }
        
        // Fallback to sub claim
        Object subObj = userInfo.get("sub");
        if (subObj != null) {
            return subObj.toString();
        }
        
        throw new RuntimeException("Cannot extract username from OIDC response");
    }

    /**
     * Extract groups from user info based on configuration
     */
    @SuppressWarnings("unchecked")
    private List<String> extractGroups(Map<String, Object> userInfo, OidcSettings settings) {
        String claimName = settings.getGroupsClaim();
        Object groupsObj = userInfo.get(claimName);
        
        if (groupsObj instanceof List) {
            List<?> groupsList = (List<?>) groupsObj;
            List<String> result = new ArrayList<>();
            for (Object item : groupsList) {
                if (item instanceof String) {
                    result.add((String) item);
                } else if (item instanceof Map) {
                    // Handle nested group objects (e.g., Keycloak resource representation)
                    Map<?, ?> groupMap = (Map<?, ?>) item;
                    Object nameObj = groupMap.get("name");
                    if (nameObj != null) {
                        result.add(nameObj.toString());
                    }
                }
            }
            return result;
        }
        
        // Try alternative claim names for Keycloak
        if ("groups".equals(claimName)) {
            // Try realm_access.roles
            Object realmAccessObj = userInfo.get("realm_access");
            if (realmAccessObj instanceof Map) {
                Map<?, ?> realmAccess = (Map<?, ?>) realmAccessObj;
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List) {
                    List<?> rolesList = (List<?>) rolesObj;
                    List<String> result = new ArrayList<>();
                    for (Object role : rolesList) {
                        if (role instanceof String) {
                            result.add((String) role);
                        }
                    }
                    return result;
                }
            }
            
            // Try resource_access for client-specific roles
            Object resourceAccessObj = userInfo.get("resource_access");
            if (resourceAccessObj instanceof Map) {
                Map<?, ?> resourceAccess = (Map<?, ?>) resourceAccessObj;
                // Try to find roles in any client
                for (Object key : resourceAccess.keySet()) {
                    Object clientObj = resourceAccess.get(key);
                    if (clientObj instanceof Map) {
                        Map<?, ?> clientData = (Map<?, ?>) clientObj;
                        Object rolesObj = clientData.get("roles");
                        if (rolesObj instanceof List) {
                            List<?> rolesList = (List<?>) rolesObj;
                            List<String> result = new ArrayList<>();
                            for (Object role : rolesList) {
                                if (role instanceof String) {
                                    result.add((String) role);
                                }
                            }
                            return result;
                        }
                    }
                }
            }
        }
        
        return new ArrayList<>();
    }

    private String readResponse(java.io.InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
