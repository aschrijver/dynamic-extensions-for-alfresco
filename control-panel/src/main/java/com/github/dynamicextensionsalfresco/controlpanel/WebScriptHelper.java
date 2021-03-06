package com.github.dynamicextensionsalfresco.controlpanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.dynamicextensionsalfresco.controlpanel.template.TemplateWebScript;
import com.github.dynamicextensionsalfresco.webscripts.WebScriptUriRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.extensions.webscripts.WebScript;
import org.springframework.stereotype.Component;

@Component
public class WebScriptHelper {

	@Autowired
	private BundleHelper bundleHelper;

	/* Main operations */

	public List<TemplateWebScript> getWebScripts() {
		final WebScriptUriRegistry registry = getApplicationContextBean(WebScriptUriRegistry.class);
		if (registry != null) {
			final List<WebScript> webScripts = registry.getWebScripts();
			final List<TemplateWebScript> templateWebScripts = new ArrayList<TemplateWebScript>(webScripts.size());
			for (final WebScript webScript : webScripts) {
				templateWebScripts.add(new TemplateWebScript(webScript));
			}
			return templateWebScripts;
		} else {
			return Collections.emptyList();
		}
	}

	/* Utility operations */

	protected <T> T getApplicationContextBean(final Class<T> clazz) {
		final ApplicationContext applicationContext = bundleHelper.getService(ApplicationContext.class);
		if (applicationContext != null) {
			return applicationContext.getBean(clazz);
		} else {
			return null;
		}
	}

}
