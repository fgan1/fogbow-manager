package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.occi.core.Token;

public interface AuthorizationPlugin {
	
	public boolean isAutorized(Token token);
	
}