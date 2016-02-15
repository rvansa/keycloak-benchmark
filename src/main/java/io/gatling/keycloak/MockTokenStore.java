package io.gatling.keycloak;

import org.keycloak.adapters.AdapterTokenStore;
import org.keycloak.adapters.OidcKeycloakAccount;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.RequestAuthenticator;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class MockTokenStore implements AdapterTokenStore {
   @Override
   public void checkCurrentToken() {
         }

   @Override
   public boolean isCached(RequestAuthenticator authenticator) {
      return false;
   }

   @Override
   public void saveAccountInfo(OidcKeycloakAccount account) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void logout() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void refreshCallback(RefreshableKeycloakSecurityContext securityContext) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void saveRequest() {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean restoreRequest() {
      throw new UnsupportedOperationException();
   }
}
