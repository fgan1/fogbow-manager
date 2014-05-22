package org.fogbowcloud.manager.occi.plugins;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.resource.ResourceException;

public class TestIdentityOpenStack {

	private final String KEYSTONE_URL = "http://localhost:" + PluginHelper.PORT_ENDPOINT;
	private OpenStackIdentityPlugin identityOpenStack;
	private PluginHelper pluginHelper;

	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put("identity_openstack_url", KEYSTONE_URL);
		this.identityOpenStack = new OpenStackIdentityPlugin(properties);
		this.pluginHelper = new PluginHelper();
		this.pluginHelper.initializeKeystoneComponent();
	}

	@After
	public void tearDown() throws Exception {
		this.pluginHelper.disconnectComponent();
	}

	@Test
	public void testValidToken() {
		Assert.assertEquals(PluginHelper.USERNAME_FOGBOW,
				this.identityOpenStack.getToken(PluginHelper.ACCESS_ID).getUser());
	}

	@Test(expected = ResourceException.class)
	public void testInvalidToken() {
		identityOpenStack.getToken("Invalid Token");
	}

	@Test
	public void testGetNameUserFromToken() {
		Assert.assertEquals(PluginHelper.USERNAME_FOGBOW,
				this.identityOpenStack.getToken(PluginHelper.ACCESS_ID).getUser());
	}

	@Test(expected = ResourceException.class)
	public void testGetNameUserFromTokenInvalid() {
		this.identityOpenStack.getToken("invalid_token");
	}

	@Test
	public void testGetToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, PluginHelper.USERNAME_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, PluginHelper.PASSWORD_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, PluginHelper.TENANT_NAME);
		Token token = this.identityOpenStack.createToken(tokenAttributes);
		String authToken = token.getAccessId();
		String tenantID = token.get(OCCIHeaders.X_TOKEN_TENANT_ID);
		Date expirationDate = token.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, expirationDate);
	}

	@Test
	public void testUpgradeToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, PluginHelper.USERNAME_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, PluginHelper.PASSWORD_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, PluginHelper.TENANT_NAME);
		Token token = this.identityOpenStack.createToken(tokenAttributes);
		String authToken = token.getAccessId();
		String tenantID = token.get(OCCIHeaders.X_TOKEN_TENANT_ID);
		Date expirationDate = token.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, expirationDate);

		Token token2 = this.identityOpenStack.createToken(token);
		authToken = token2.getAccessId();
		tenantID = token2.get(OCCIHeaders.X_TOKEN_TENANT_ID);
		expirationDate = token2.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, expirationDate);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongUsername() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, "wrong");
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, PluginHelper.PASSWORD_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, "");
		this.identityOpenStack.createToken(tokenAttributes);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongPassword() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, PluginHelper.USERNAME_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, "worng");
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, "");
		this.identityOpenStack.createToken(tokenAttributes);
	}
}