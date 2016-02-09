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
      // TODO: Customise this generated block
   }

   @Override
   public void logout() {
      // TODO: Customise this generated block
   }

   @Override
   public void refreshCallback(RefreshableKeycloakSecurityContext securityContext) {
      // TODO: Customise this generated block
   }

   @Override
   public void saveRequest() {
      // TODO: Customise this generated block
   }

   @Override
   public boolean restoreRequest() {
      return false;  // TODO: Customise this generated block
   }
}
