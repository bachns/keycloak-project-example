package com.github.thomasdarimont.keycloakx.custom.security;

import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import io.vertx.core.http.HttpServerRequest;
import lombok.Data;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.Config;
import org.keycloak.configuration.Configuration;
import org.keycloak.utils.StringUtil;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@JBossLog
@Provider
public class AccessFilter implements ContainerRequestFilter {

    public static final String DEFAULT_IP_FILTER_RULES = "127.0.0.1/24,192.168.80.1/16,172.0.0.1/8";
    public static final String ADMIN_IP_FILTER_RULES_ALLOW = "acme.keycloak.admin.ip-filter-rules.allow";
    public static final ForbiddenException FORBIDDEN_EXCEPTION = new ForbiddenException();

    private final PathIpFilterRules adminPathIpFilterRules;

    @Context
    private HttpServerRequest httpServerRequest;

    public AccessFilter() {
        this.adminPathIpFilterRules = createAdminIpFilterRules(Configuration.getConfig());
    }

    private PathIpFilterRules createAdminIpFilterRules(Config config) {

        var contextPath = config.getValue("quarkus.http.root-path", String.class);
        var adminPath = makeContextPath(contextPath, "admin");
        var filterRules = config //
                .getOptionalValue(ADMIN_IP_FILTER_RULES_ALLOW, String.class) //
                .orElse(DEFAULT_IP_FILTER_RULES);

        if (StringUtil.isBlank(filterRules)) {
            return null;
        }

        var rules = new LinkedHashSet<IpSubnetFilterRule>();
        var ruleType = IpFilterRuleType.ACCEPT;
        var ruleDefinitions = List.of(filterRules.split(","));

        for (var rule : ruleDefinitions) {
            var ipAndCidrPrefix = rule.split("/");
            var ip = ipAndCidrPrefix[0];
            var cidrPrefix = Integer.parseInt(ipAndCidrPrefix[1]);
            rules.add(new IpSubnetFilterRule(ip, cidrPrefix, ruleType));
        }

        var ruleDescription = adminPath + " " + ruleType + " from " + String.join(",", ruleDefinitions);
        var pathIpFilterRules = new PathIpFilterRules(ruleDescription, adminPath, Set.copyOf(rules));
        log.infof("Created Security Filter rules for %s", pathIpFilterRules);
        return pathIpFilterRules;
    }

    private String makeContextPath(String contextPath, String subPath) {
        if (contextPath.endsWith("/")) {
            return contextPath + subPath;
        }
        return contextPath + "/" + subPath;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {

        if (adminPathIpFilterRules == null) {
            return;
        }

        var requestUri = requestContext.getUriInfo().getRequestUri();
        log.tracef("Processing request: %s", requestUri);

        var requestPath = requestUri.getPath();
        if (requestPath.startsWith(adminPathIpFilterRules.getPathPrefix())) {
            if (!isAdminRequestAllowed()) {
                throw FORBIDDEN_EXCEPTION;
            }
        }
    }

    private boolean isAdminRequestAllowed() {

        var remoteIp = httpServerRequest.connection().remoteAddress();
        var address = new InetSocketAddress(remoteIp.host(), remoteIp.port());
        for (var filterRule : adminPathIpFilterRules.getIpFilterRules()) {
            if (filterRule.matches(address)) {
                return true;
            }
        }

        return false;
    }

    @Data
    static class PathIpFilterRules {

        private final String ruleDescription;

        private final String pathPrefix;

        private final Set<IpSubnetFilterRule> ipFilterRules;

        public String toString() {
            return ruleDescription;
        }
    }
}
