/*
Copyright (c) 2012, Runnable
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
 * Neither the name of Runnable nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package nl.runnable.alfresco.blueprint;

import java.lang.annotation.Annotation;
import java.util.Map;

import javax.inject.Named;

import nl.runnable.alfresco.annotations.AlfrescoService;
import nl.runnable.alfresco.annotations.ServiceType;

import org.alfresco.service.cmr.repository.NodeService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;

/**
 * {@link BeanFactory} that resolves autowire candidates against OSGI services.
 * 
 * @author Laurens Fridael
 * 
 */
class OsgiAutowireServiceBeanFactory extends DefaultListableBeanFactory {

	private static final String HOST_APPLICATION_CONTEXT_BEAN_NAME = "HostApplicationContext";

	private static final String OSGI_SERVICE_BLUEPRINT_COMPNAME = "osgi.service.blueprint.compname";

	private static final String ALFRESCO_SERVICE_TYPE = "alfresco.service.type";

	private final BundleContext bundleContext;

	OsgiAutowireServiceBeanFactory(final BeanFactory parentBeanFactory, final BundleContext bundleContext) {
		super(parentBeanFactory);
		this.bundleContext = bundleContext;
	}

	/* Main operations */

	/**
	 * Finds autowire candidate beans by resolving OSGi services using required type's Java class name.
	 * <p>
	 * For example, if the required type is {@link NodeService}, this implementation looks for a service named
	 * <code>org.alfresco.service.cmr.repository.NodeService</code>.
	 * <p>
	 * This implementation also supports the {@link Named} annotation on a dependency to directly reference named beans
	 * in the Alfresco application context. This support enables you to distinguish between multiple implementations of
	 * the same interface. One example would be referring the <code>categoryService</code> or the
	 * <code>CategoryService</code>. (The latter bean is a proxy over the first bean, with additional support for
	 * auditing.)
	 */
	@Override
	@SuppressWarnings("rawtypes")
	protected Map<String, Object> findAutowireCandidates(final String beanName, final Class requiredType,
			final DependencyDescriptor descriptor) {
		final Map<String, Object> candidateBeansByName = super.findAutowireCandidates(beanName, requiredType,
				descriptor);
		if (candidateBeansByName.isEmpty()) {
			final String typeName = requiredType.getName();
			if (BundleContext.class.isAssignableFrom(requiredType)) {
				candidateBeansByName.put(typeName, bundleContext);
			} else {
				final ServiceReference<?> serviceReference = findServiceReference(descriptor, typeName);
				if (serviceReference != null) {
					candidateBeansByName.put(typeName, bundleContext.getService(serviceReference));
				} else {
					final Named named = getAnnotation(descriptor, Named.class);
					if (named != null) {
						final Object bean = findBeanInAlfrescoApplicationContext(named.value());
						if (bean != null) {
							candidateBeansByName.put(named.value(), bean);
						}
					}
				}
			}
		}
		return candidateBeansByName;
	}

	/**
	 * Attempts to find a named bean in the Alfresco {@link ApplicationContext}.
	 * 
	 * @param beanName
	 * @return
	 */
	protected Object findBeanInAlfrescoApplicationContext(final String beanName) {
		Object bean = null;
		final ServiceReference<?> serviceReference = getServiceReferenceWithBeanName(
				ApplicationContext.class.getName(), HOST_APPLICATION_CONTEXT_BEAN_NAME);
		if (serviceReference != null) {
			final ApplicationContext applicationContext = (ApplicationContext) bundleContext
					.getService(serviceReference);
			bean = applicationContext.getBean(beanName);
		}
		return bean;
	}

	/* Utility operations */

	protected ServiceReference<?> findServiceReference(final DependencyDescriptor descriptor, final String serviceName) {
		final ServiceReference<?> serviceReference;
		final AlfrescoService alfrescoService = getAnnotation(descriptor, AlfrescoService.class);
		final Named named = getAnnotation(descriptor, Named.class);
		if (alfrescoService != null || named != null) {
			if (alfrescoService != null) {
				serviceReference = getServiceReferenceForServiceType(serviceName, alfrescoService.value());
			} else {
				serviceReference = getServiceReferenceWithBeanName(serviceName, named.value());
			}
		} else {
			serviceReference = getServiceReferenceByName(serviceName);
		}
		return serviceReference;
	}

	@SuppressWarnings("unchecked")
	private <T extends Annotation> T getAnnotation(final DependencyDescriptor descriptor, final Class<T> annotationType) {
		for (final Annotation annotation : descriptor.getAnnotations()) {
			if (annotationType.isAssignableFrom(annotation.annotationType())) {
				return (T) annotation;
			}
		}
		return null;
	}

	private ServiceReference<?> getServiceReferenceForServiceType(final String serviceName,
			final ServiceType serviceType) {
		try {
			final ServiceReference<?>[] serviceReferences = bundleContext.getServiceReferences(serviceName,
					filterServiceForServiceType(serviceType));
			if (serviceReferences != null && serviceReferences.length > 0) {
				return serviceReferences[0];
			} else {
				return null;
			}
		} catch (final InvalidSyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private String filterServiceForServiceType(final ServiceType serviceType) {
		final String filter;
		switch (serviceType) {
		case DEFAULT:
			// Fall through
		default:
			filter = String.format("(%s=%s)", ALFRESCO_SERVICE_TYPE, "default");
			break;
		case LOW_LEVEL:
			filter = String.format("(%s=%s)", ALFRESCO_SERVICE_TYPE, "low-level");
			break;
		}
		return filter;
	}

	private ServiceReference<?> getServiceReferenceWithBeanName(final String serviceName, final String beanName) {
		try {
			final String filter = String.format("(%s=%s)", OSGI_SERVICE_BLUEPRINT_COMPNAME, beanName);
			final ServiceReference<?>[] serviceReferences = bundleContext.getServiceReferences(serviceName, filter);
			if (serviceReferences != null && serviceReferences.length > 0) {
				return serviceReferences[0];
			} else {
				return null;
			}
		} catch (final InvalidSyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private ServiceReference<?> getServiceReferenceByName(final String serviceName) {
		ServiceReference<?> serviceReference = getServiceReferenceForServiceType(serviceName, ServiceType.DEFAULT);
		if (serviceReference == null) {
			serviceReference = bundleContext.getServiceReference(serviceName);
		}
		return serviceReference;
	}

}