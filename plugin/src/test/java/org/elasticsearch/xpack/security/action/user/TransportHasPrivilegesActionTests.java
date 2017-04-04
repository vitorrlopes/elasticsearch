/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.action.user;

import java.util.Collections;
import java.util.LinkedHashMap;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthAction;
import org.elasticsearch.action.delete.DeleteAction;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.mock.orig.Mockito;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.security.action.user.HasPrivilegesResponse.IndexPrivileges;
import org.elasticsearch.xpack.security.authc.Authentication;
import org.elasticsearch.xpack.security.authz.AuthorizationService;
import org.elasticsearch.xpack.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.security.authz.permission.Role;
import org.elasticsearch.xpack.security.authz.privilege.ClusterPrivilege;
import org.elasticsearch.xpack.security.authz.privilege.IndexPrivilege;
import org.elasticsearch.xpack.security.user.User;
import org.hamcrest.Matchers;
import org.junit.Before;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransportHasPrivilegesActionTests extends ESTestCase {

    private User user;
    private Role role;
    private TransportHasPrivilegesAction action;

    @Before
    public void setup() {
        final Settings settings = Settings.builder().build();
        user = new User(randomAlphaOfLengthBetween(4, 12));
        final ThreadPool threadPool = mock(ThreadPool.class);
        final ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        final TransportService transportService = new TransportService(Settings.EMPTY, null, null, TransportService
                .NOOP_TRANSPORT_INTERCEPTOR,
                x -> null, null);

        final Authentication authentication = mock(Authentication.class);
        threadContext.putTransient(Authentication.AUTHENTICATION_KEY, authentication);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        when(authentication.getRunAsUser()).thenReturn(user);

        AuthorizationService authorizationService = mock(AuthorizationService.class);
        Mockito.doAnswer(invocationOnMock -> {
            ActionListener<Role> listener = (ActionListener<Role>) invocationOnMock.getArguments()[1];
            listener.onResponse(role);
            return null;
        }).when(authorizationService).roles(eq(user), any(ActionListener.class));

        action = new TransportHasPrivilegesAction(settings, threadPool, transportService,
                mock(ActionFilters.class), mock(IndexNameExpressionResolver.class), authorizationService);
    }

    /**
     * This tests that action names in the request are considered "matched" by the relevant named privilege
     * (in this case that {@link DeleteAction} and {@link IndexAction} are satisfied by {@link IndexPrivilege#WRITE}).
     */
    public void testNamedIndexPrivilegesMatchApplicableActions() throws Exception {
        role = Role.builder("test1").cluster(ClusterPrivilege.ALL).add(IndexPrivilege.WRITE, "academy").build();

        final HasPrivilegesRequest request = new HasPrivilegesRequest();
        request.username(user.principal());
        request.clusterPrivileges(ClusterHealthAction.NAME);
        request.indexPrivileges(RoleDescriptor.IndicesPrivileges.builder()
                .indices("academy")
                .privileges(DeleteAction.NAME, IndexAction.NAME)
                .build());
        final PlainActionFuture<HasPrivilegesResponse> future = new PlainActionFuture();
        action.doExecute(request, future);

        final HasPrivilegesResponse response = future.get();
        assertThat(response, notNullValue());
        assertThat(response.isCompleteMatch(), is(true));

        assertThat(response.getClusterPrivileges().size(), equalTo(1));
        assertThat(response.getClusterPrivileges().get(ClusterHealthAction.NAME), equalTo(true));

        assertThat(response.getIndexPrivileges(), Matchers.iterableWithSize(1));
        final IndexPrivileges result = response.getIndexPrivileges().get(0);
        assertThat(result.getIndex(), equalTo("academy"));
        assertThat(result.getPrivileges().size(), equalTo(2));
        assertThat(result.getPrivileges().get(DeleteAction.NAME), equalTo(true));
        assertThat(result.getPrivileges().get(IndexAction.NAME), equalTo(true));
    }

    /**
     * This tests that the action responds correctly when the user/role has some, but not all
     * of the privileges being checked.
     */
    public void testMatchSubsetOfPrivileges() throws Exception {
        role = Role.builder("test2")
                .cluster(ClusterPrivilege.MONITOR)
                .add(IndexPrivilege.INDEX, "academy")
                .add(IndexPrivilege.WRITE, "initiative")
                .build();

        final HasPrivilegesRequest request = new HasPrivilegesRequest();
        request.username(user.principal());
        request.clusterPrivileges("monitor", "manage");
        request.indexPrivileges(RoleDescriptor.IndicesPrivileges.builder()
                .indices("academy", "initiative", "school")
                .privileges("delete", "index", "manage")
                .build());
        final PlainActionFuture<HasPrivilegesResponse> future = new PlainActionFuture();
        action.doExecute(request, future);

        final HasPrivilegesResponse response = future.get();
        assertThat(response, notNullValue());
        assertThat(response.isCompleteMatch(), is(false));
        assertThat(response.getClusterPrivileges().size(), equalTo(2));
        assertThat(response.getClusterPrivileges().get("monitor"), equalTo(true));
        assertThat(response.getClusterPrivileges().get("manage"), equalTo(false));
        assertThat(response.getIndexPrivileges(), Matchers.iterableWithSize(3));

        final IndexPrivileges academy = response.getIndexPrivileges().get(0);
        final IndexPrivileges initiative = response.getIndexPrivileges().get(1);
        final IndexPrivileges school = response.getIndexPrivileges().get(2);

        assertThat(academy.getIndex(), equalTo("academy"));
        assertThat(academy.getPrivileges().size(), equalTo(3));
        assertThat(academy.getPrivileges().get("index"), equalTo(true)); // explicit
        assertThat(academy.getPrivileges().get("delete"), equalTo(false));
        assertThat(academy.getPrivileges().get("manage"), equalTo(false));

        assertThat(initiative.getIndex(), equalTo("initiative"));
        assertThat(initiative.getPrivileges().size(), equalTo(3));
        assertThat(initiative.getPrivileges().get("index"), equalTo(true)); // implied by write
        assertThat(initiative.getPrivileges().get("delete"), equalTo(true)); // implied by write
        assertThat(initiative.getPrivileges().get("manage"), equalTo(false));

        assertThat(school.getIndex(), equalTo("school"));
        assertThat(school.getPrivileges().size(), equalTo(3));
        assertThat(school.getPrivileges().get("index"), equalTo(false));
        assertThat(school.getPrivileges().get("delete"), equalTo(false));
        assertThat(school.getPrivileges().get("manage"), equalTo(false));
    }

    /**
     * This tests that the action responds correctly when the user/role has none
     * of the privileges being checked.
     */
    public void testMatchNothing() throws Exception {
        role = Role.builder("test3")
                .cluster(ClusterPrivilege.MONITOR)
                .build();

        final HasPrivilegesRequest request = new HasPrivilegesRequest();
        request.username(user.principal());
        request.clusterPrivileges(Strings.EMPTY_ARRAY);
        request.indexPrivileges(RoleDescriptor.IndicesPrivileges.builder()
                .indices("academy")
                .privileges("read", "write")
                .build());
        final PlainActionFuture<HasPrivilegesResponse> future = new PlainActionFuture();
        action.doExecute(request, future);

        final HasPrivilegesResponse response = future.get();
        assertThat(response, notNullValue());
        assertThat(response.isCompleteMatch(), is(false));
        assertThat(response.getIndexPrivileges(), Matchers.iterableWithSize(1));
        final IndexPrivileges result = response.getIndexPrivileges().get(0);
        assertThat(result.getIndex(), equalTo("academy"));
        assertThat(result.getPrivileges().size(), equalTo(2));
        assertThat(result.getPrivileges().get("read"), equalTo(false));
        assertThat(result.getPrivileges().get("write"), equalTo(false));
    }

    /**
     * We intentionally ignore wildcards in the request. This tests that
     * <code>log*</code> in the request isn't granted by <code>logstash-*</code>
     * in the role, but <code>logstash-2016-*</code> is, because it's just
     * treated as the name of an index.
     */
    public void testWildcardsInRequestAreIgnored() throws Exception {
        role = Role.builder("test3")
                .add(IndexPrivilege.ALL, "logstash-*")
                .build();

        final HasPrivilegesRequest request = new HasPrivilegesRequest();
        request.username(user.principal());
        request.clusterPrivileges(Strings.EMPTY_ARRAY);
        request.indexPrivileges(
                RoleDescriptor.IndicesPrivileges.builder()
                        .indices("logstash-2016-*")
                        .privileges("write")
                        .build(),
                RoleDescriptor.IndicesPrivileges.builder()
                        .indices("log*")
                        .privileges("read")
                        .build()
        );
        final PlainActionFuture<HasPrivilegesResponse> future = new PlainActionFuture();
        action.doExecute(request, future);

        final HasPrivilegesResponse response = future.get();
        assertThat(response, notNullValue());
        assertThat(response.isCompleteMatch(), is(false));
        assertThat(response.getIndexPrivileges(), Matchers.iterableWithSize(2));
        assertThat(response.getIndexPrivileges(), containsInAnyOrder(
                new IndexPrivileges("logstash-2016-*", Collections.singletonMap("write", true)),
                new IndexPrivileges("log*", Collections.singletonMap("read", false))
        ));
    }

    public void testCheckingIndexPermissionsDefinedOnDifferentPatterns() throws Exception {
        role = Role.builder("test-write")
                .add(IndexPrivilege.INDEX, "apache-*")
                .add(IndexPrivilege.DELETE, "apache-2016-*")
                .build();

        final HasPrivilegesRequest request = new HasPrivilegesRequest();
        request.username(user.principal());
        request.clusterPrivileges(Strings.EMPTY_ARRAY);
        request.indexPrivileges(
                RoleDescriptor.IndicesPrivileges.builder()
                        .indices("apache-2016-12", "apache-2017-01")
                        .privileges("index", "delete")
                        .build()
        );
        final PlainActionFuture<HasPrivilegesResponse> future = new PlainActionFuture();
        action.doExecute(request, future);

        final HasPrivilegesResponse response = future.get();
        assertThat(response, notNullValue());
        assertThat(response.isCompleteMatch(), is(false));
        assertThat(response.getIndexPrivileges(), Matchers.iterableWithSize(2));
        assertThat(response.getIndexPrivileges(), containsInAnyOrder(
                new IndexPrivileges("apache-2016-12",
                        MapBuilder.newMapBuilder(new LinkedHashMap<String, Boolean>())
                                .put("index", true).put("delete", true).map()),
                new IndexPrivileges("apache-2017-01",
                        MapBuilder.newMapBuilder(new LinkedHashMap<String, Boolean>())
                                .put("index", true).put("delete", false).map()
                )
        ));
    }
}