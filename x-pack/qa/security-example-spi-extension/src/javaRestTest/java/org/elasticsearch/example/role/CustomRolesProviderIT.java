/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.example.role;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.security.PutUserRequest;
import org.elasticsearch.client.security.RefreshPolicy;
import org.elasticsearch.client.security.user.User;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.example.realm.CustomRealm;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.xpack.core.security.authc.support.UsernamePasswordToken;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.example.role.CustomInMemoryRolesProvider.INDEX;
import static org.elasticsearch.example.role.CustomInMemoryRolesProvider.ROLE_A;
import static org.elasticsearch.example.role.CustomInMemoryRolesProvider.ROLE_B;
import static org.hamcrest.Matchers.is;

/**
 * Integration test for custom roles providers.
 */
public class CustomRolesProviderIT extends ESRestTestCase {
    private static final String TEST_USER = "test_user";
    private static final String TEST_PWD = "change_me";

    private static final RequestOptions AUTH_OPTIONS;
    static {
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.addHeader(UsernamePasswordToken.BASIC_AUTH_HEADER,
                UsernamePasswordToken.basicAuthHeaderValue(TEST_USER, new SecureString(TEST_PWD.toCharArray())));
        AUTH_OPTIONS = options.build();
    }

    @Override
    protected Settings restClientSettings() {
        return Settings.builder()
            .put(ThreadContext.PREFIX + "." + CustomRealm.USER_HEADER, CustomRealm.KNOWN_USER)
            .put(ThreadContext.PREFIX + "." + CustomRealm.PW_HEADER, CustomRealm.KNOWN_PW.toString())
            .build();
    }

    public void setupTestUser(String role) throws IOException {
        new TestRestHighLevelClient().security().putUser(
            PutUserRequest.withPassword(new User(TEST_USER, List.of(role)), TEST_PWD.toCharArray(), true, RefreshPolicy.IMMEDIATE),
            RequestOptions.DEFAULT);
    }

    public void testAuthorizedCustomRoleSucceeds() throws Exception {
        setupTestUser(ROLE_B);
        // roleB has all permissions on index "foo", so creating "foo" should succeed
        Request request = new Request("PUT", "/" + INDEX);
        request.setOptions(AUTH_OPTIONS);
        Response response = client().performRequest(request);
        assertThat(response.getStatusLine().getStatusCode(), is(200));
    }

    public void testFirstResolvedRoleTakesPrecedence() throws Exception {
        // the first custom roles provider has set ROLE_A to only have read permission on the index,
        // the second custom roles provider has set ROLE_A to have all permissions, but since
        // the first custom role provider appears first in order, it should take precedence and deny
        // permission to create the index
        setupTestUser(ROLE_A);
        Request request = new Request("PUT", "/" + INDEX);
        request.setOptions(AUTH_OPTIONS);
        ResponseException e = expectThrows(ResponseException.class, () -> client().performRequest(request));
        assertThat(e.getResponse().getStatusLine().getStatusCode(), is(403));
    }

    public void testUnresolvedRoleDoesntSucceed() throws Exception {
        setupTestUser("unknown");
        Request request = new Request("PUT", "/" + INDEX);
        request.setOptions(AUTH_OPTIONS);
        ResponseException e = expectThrows(ResponseException.class, () -> client().performRequest(request));
        assertThat(e.getResponse().getStatusLine().getStatusCode(), is(403));
    }

    private class TestRestHighLevelClient extends RestHighLevelClient {
        TestRestHighLevelClient() {
            super(client(), restClient -> {}, Collections.emptyList());
        }
    }
}