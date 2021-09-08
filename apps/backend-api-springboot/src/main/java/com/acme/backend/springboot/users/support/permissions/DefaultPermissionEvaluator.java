package com.acme.backend.springboot.users.support.permissions;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
class DefaultPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        log.info("check permission user={} target={} permission={}", auth.getName(), targetDomainObject, permission);

        // TODO implement sophisticated permission check here
        return true;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        DomainObjectReference dor = new DomainObjectReference(targetType, targetId.toString());
        return hasPermission(auth, dor, permission);
    }
}
