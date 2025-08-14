package org.eclipse.daanse.tooling.testcontainers.core;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {

        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        ClassLoader bundleCl = DockerClientProviderStrategy.class.getClassLoader();

        Thread.currentThread().setContextClassLoader(bundleCl);

        DockerClientFactory.lazyClient().pingCmd();

        Thread.currentThread().setContextClassLoader(originalCl);

    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // TODO Auto-generated method stub

    }

}
