package com.dazo66.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ServerRoutesTest {
    @Test public void networkChangeRetriesPrimaryBeforeRememberedBackup() throws Exception {
        ServerRoutes routes = new ServerRoutes("https://main.example", Collections.singletonList("https://backup.example"));
        routes.execute("/api/auth/me", (address, trusted) -> { if (address.startsWith("https://main.")) throw new ServerRoutes.Failure("offline", null, true); return true; });
        assertTrue(routes.addresses("/api/status").get(0).startsWith("https://backup."));
        routes.resetToPrimary();
        assertEquals("https://main.example/api/status", routes.addresses("/api/status").get(0));
    }
    @Test public void anOldNetworkResponseCannotRestoreTheOldBackup() throws Exception {
        ServerRoutes routes = new ServerRoutes("https://main.example", Collections.singletonList("https://backup.example"));
        routes.execute("/api/auth/me", (address, trusted) -> {
            if (address.startsWith("https://main.")) throw new ServerRoutes.Failure("offline", null, true);
            routes.resetToPrimary(); return true;
        });
        assertEquals("https://main.example", routes.active());
    }
    @Test public void brokenStreamsMoveToAnotherOriginWithoutLosingPrimaryIdentity() throws Exception {
        ServerRoutes routes = new ServerRoutes("https://main.example", Arrays.asList("https://a.example", "https://b.example"));
        routes.failed("https://main.example/api/file", routes.generation());
        assertEquals("https://a.example/api/file", routes.addresses("/api/file").get(0));
        routes.failed("https://a.example/api/file", routes.generation());
        assertEquals("https://b.example/api/file", routes.addresses("/api/file").get(0));
        assertEquals("https://main.example", routes.primary());
    }
    @Test public void normalizesAndDeduplicatesRoots() {
        ServerRoutes routes = new ServerRoutes(" HTTPS://Main.Example/ ", Arrays.asList("https://main.example", "http://127.0.0.1:8080/", ""));
        assertEquals("https://main.example", routes.primary());
        assertEquals(Collections.singletonList("http://127.0.0.1:8080"), routes.backups());
    }
    @Test public void rejectsAddressesThatCouldLeakCredentialsOrChangeTheApiRoot() {
        for (String address : Arrays.asList("ftp://host", "https://user:secret@host", "https://host/api", "https://host?token=a", "https://host/#fragment", "https://host:70000")) {
            try { ServerRoutes.normalize(address); fail(address); } catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void switchesToBackupAndUsesItForFollowingCalls() throws Exception {
        ServerRoutes routes = new ServerRoutes("https://main.example", Collections.singletonList("https://backup.example"));
        List<String> calls = new ArrayList<>();
        String result = routes.execute("/api/auth/me", (address, trusted) -> {
            assertTrue(trusted); calls.add(address);
            if (address.startsWith("https://main.")) throw new ServerRoutes.Failure("connect", null, true);
            return "same logged-in user";
        });
        assertEquals("same logged-in user", result);
        assertEquals(2, calls.size());
        assertEquals("https://backup.example/api/gallery", routes.addresses("/api/gallery").get(0));
    }
    @Test public void cacheAndStateIdentityRemainBoundToPrimary() throws Exception {
        ServerRoutes routes = new ServerRoutes("https://main.example", Collections.singletonList("https://backup.example"));
        assertEquals("https://main.example/api/file?path=a%20b", routes.canonical("https://backup.example/api/file?path=a%20b"));
        assertEquals("https://main.example", routes.primary());
    }
    @Test public void neverReplaysAWrittenRequestWithAnUncertainResult() {
        ServerRoutes routes = new ServerRoutes("https://main.example", Collections.singletonList("https://backup.example"));
        List<String> calls = new ArrayList<>();
        try {
            routes.execute("/api/fanbox", (address, trusted) -> { calls.add(address); throw new ServerRoutes.Failure("read timeout after POST", null, false); });
            fail();
        } catch (Exception expected) { assertEquals(1, calls.size()); }
    }
    @Test public void applicationErrorsDoNotTriggerAnotherLoginOrWrite() {
        ServerRoutes routes = new ServerRoutes("https://main.example", Collections.singletonList("https://backup.example"));
        List<String> calls = new ArrayList<>();
        try {
            routes.execute("/api/auth/login", (address, trusted) -> { calls.add(address); throw new IllegalArgumentException("wrong password"); });
            fail();
        } catch (Exception expected) { assertEquals(1, calls.size()); }
    }
    @Test public void externalHostsAndLookalikeOriginsNeverGetSharedCredentials() throws Exception {
        ServerRoutes routes = new ServerRoutes("https://main.example", Collections.singletonList("https://backup.example"));
        for (String address : Arrays.asList("https://cdn.example/image", "https://main.example.evil/image", "https://main.example@evil/image")) {
            assertFalse(routes.trusted(address));
            routes.execute(address, (target, trusted) -> { assertFalse(trusted); assertEquals(address, target); return true; });
        }
    }
    @Test(expected = IllegalArgumentException.class) public void limitsBackupCount() {
        new ServerRoutes("https://main.example", Arrays.asList("https://a.example", "https://b.example", "https://c.example", "https://d.example", "https://e.example", "https://f.example"));
    }
}
