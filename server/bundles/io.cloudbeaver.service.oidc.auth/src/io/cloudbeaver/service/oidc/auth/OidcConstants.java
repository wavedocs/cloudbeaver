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

public interface OidcConstants {
    // Configuration parameters
    String PARAM_ISSUER_URL = "oidc-issuer-url";
    String PARAM_CLIENT_ID = "oidc-client-id";
    String PARAM_CLIENT_SECRET = "oidc-client-secret";
    String PARAM_SCOPE = "oidc-scope";
    String PARAM_REDIRECT_URI = "oidc-redirect-uri";
    String PARAM_GROUPS_CLAIM = "oidc-groups-claim";
    String PARAM_USERNAME_CLAIM = "oidc-username-claim";
    String PARAM_NAME_CLAIM = "oidc-name-claim";
    String PARAM_LOGOUT_URL = "oidc-logout-url";
    
    // Credential keys
    String CRED_ACCESS_TOKEN = "access_token";
    String CRED_ID_TOKEN = "id_token";
    String CREFRESH_TOKEN = "refresh_token";
    String CRED_USER_NAME = "user_name";
    String CRED_USER_EMAIL = "user_email";
    String CRED_USER_GROUPS = "user_groups";
    String CRED_SESSION_STATE = "session_state";
    
    // Metadata
    String OIDC_META_GROUP_NAME = "oidc.group-name";
    String OIDC_AUTH_PROVIDER_ID = "oidc";
}
