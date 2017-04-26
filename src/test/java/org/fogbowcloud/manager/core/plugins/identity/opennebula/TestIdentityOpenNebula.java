package org.fogbowcloud.manager.core.plugins.identity.opennebula;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.compute.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;

public class TestIdentityOpenNebula {

	private static final String OPEN_NEBULA_URL = "http://localhost:2633/RPC2";
	private static final String USER_POOL_ERROR_AUTH_MESSAGE = "User couldn't be authenticated, aborting call.";
	// only two users
	private static String USER_POOL_DEFAULT_RESPONSE;

	OpenNebulaIdentityPlugin identityOpenNebula;
	private static Properties properties;

	@BeforeClass
	public static void setUp() throws IOException {
		properties = new Properties();
		properties.put("identity_url", OPEN_NEBULA_URL);
		properties.put("local_proxy_account_user_name", PluginHelper.FED_USERNAME);
		properties.put("local_proxy_account_password", PluginHelper.FED_USER_PASS);
		
		USER_POOL_DEFAULT_RESPONSE = PluginHelper.getContentFile(
				"src/test/resources/opennebula/userpool.response").replaceAll("#USERNAME#",
				PluginHelper.USERNAME).replaceAll("#FED_USERNAME#", PluginHelper.FED_USERNAME);
	}

	@Test
	public void testCreateToken() throws ClientConfigurationException {
		// mocking client and factory
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(oneClient.call("userpool.info")).thenReturn(
				new OneResponse(true, USER_POOL_DEFAULT_RESPONSE));

		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		identityOpenNebula = new OpenNebulaIdentityPlugin(properties, clientFactory);

		// creating token
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(OpenNebulaIdentityPlugin.USERNAME, PluginHelper.USERNAME);
		userCredentials.put(OpenNebulaIdentityPlugin.USER_PASSWORD, PluginHelper.USER_PASS);
		Token userToken = identityOpenNebula.createToken(userCredentials);
		Assert.assertEquals(PluginHelper.USERNAME, userToken.getUser().getId());
		Assert.assertEquals(null, userToken.getExpirationDate());
		Assert.assertEquals(accessId, userToken.getAccessId());
	}

	@Test
	public void testReissueToken() throws ClientConfigurationException {
		// mocking client and factory
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(oneClient.call("userpool.info")).thenReturn(
				new OneResponse(true, USER_POOL_DEFAULT_RESPONSE));
		
		String tokenAccessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(tokenAccessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		
		identityOpenNebula = new OpenNebulaIdentityPlugin(properties, clientFactory);

		// test reissuing token
		Token token = new Token(tokenAccessId, new Token.User(PluginHelper.USERNAME, PluginHelper.USERNAME),
				null, new HashMap<String, String>());

		Token tokenReissued = identityOpenNebula.reIssueToken(token);
		Assert.assertEquals(token.getAccessId(), tokenReissued.getAccessId());
		Assert.assertEquals(token.getUser(), tokenReissued.getUser());
		Assert.assertEquals(token.getExpirationDate(), tokenReissued.getExpirationDate());
		Assert.assertEquals(token.getAttributes(), tokenReissued.getAttributes());
	}

	@Test
	public void testGetToken() throws ClientConfigurationException {
		// mocking client and factory
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(oneClient.call("userpool.info")).thenReturn(
				new OneResponse(true, USER_POOL_DEFAULT_RESPONSE));
		
		String tokenAccessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(tokenAccessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		
		identityOpenNebula = new OpenNebulaIdentityPlugin(properties, clientFactory);

		// getting user token
		Token userToken = identityOpenNebula.getToken(tokenAccessId);
		Assert.assertEquals(tokenAccessId, userToken.getAccessId());
		Assert.assertEquals(PluginHelper.USERNAME, userToken.getUser().getId());
		Assert.assertNull(userToken.getExpirationDate());
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenInvalidAccessId() throws ClientConfigurationException {
		// mocking client and factory
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(oneClient.call("userpool.info")).thenReturn(
				new OneResponse(false, USER_POOL_ERROR_AUTH_MESSAGE));
		
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient("invalidToken", OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		
		identityOpenNebula = new OpenNebulaIdentityPlugin(properties, clientFactory);

		// getting user token
		identityOpenNebula.getToken("invalidToken");
	}

	@Test
	public void testIsValid() throws ClientConfigurationException {
		// mocking client and factory
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(oneClient.call("userpool.info")).thenReturn(
				new OneResponse(true, USER_POOL_DEFAULT_RESPONSE));
		
		String tokenAccessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(tokenAccessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		
		identityOpenNebula = new OpenNebulaIdentityPlugin(properties, clientFactory);

		// testing is valid
		Assert.assertTrue(identityOpenNebula.isValid(tokenAccessId));
	}

	@Test
	public void testIsNotValid() throws ClientConfigurationException {
		// mocking client and factory
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(oneClient.call("userpool.info")).thenReturn(
				new OneResponse(false, USER_POOL_ERROR_AUTH_MESSAGE));
		
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient("invalidAccessId", OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		
		identityOpenNebula = new OpenNebulaIdentityPlugin(properties, clientFactory);

		// testing is not valid
		Assert.assertFalse(identityOpenNebula.isValid("invalidAccessId"));
	}
}
