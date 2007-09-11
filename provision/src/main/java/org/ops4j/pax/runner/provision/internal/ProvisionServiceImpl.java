/*
 * Copyright 2007 Alin Dreghiciu.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.runner.provision.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ops4j.pax.runner.commons.Assert;
import org.ops4j.pax.runner.provision.BundleReference;
import org.ops4j.pax.runner.provision.InstallableBundle;
import org.ops4j.pax.runner.provision.InstallableBundles;
import org.ops4j.pax.runner.provision.MalformedSpecificationException;
import org.ops4j.pax.runner.provision.ProvisionService;
import org.ops4j.pax.runner.provision.Scanner;
import org.ops4j.pax.runner.provision.ScannerException;
import org.ops4j.pax.runner.provision.ServiceConstants;
import org.ops4j.pax.runner.provision.UnsupportedSchemaException;
import org.osgi.framework.BundleContext;
import org.osgi.service.startlevel.StartLevel;

/**
 * Implementation of Provision Service.
 *
 * @author Alin Dreghiciu
 * @see org.ops4j.pax.runner.provision.ProvisionService
 * @since August 17, 2007
 */
public class ProvisionServiceImpl
    implements ProvisionService
{

    /**
     * Logger.
     */
    private static final Log LOGGER = LogFactory.getLog( ProvisionServiceImpl.class );
    /**
     * Scheme -> scanner relation.
     */
    private final Map<String, Scanner> m_scanners;
    /**
     * Bundle context where the bundle is installed.
     */
    private final BundleContext m_bundleContext;
    /**
     * Start Level service.
     */
    private StartLevel m_startLevelService;

    /**
     * Creates a new provision service implementation.
     *
     * @param bundleContext a bundle context
     */
    public ProvisionServiceImpl( final BundleContext bundleContext )
    {
        Assert.notNull( "Bundle context", bundleContext );
        m_bundleContext = bundleContext;
        m_scanners = new HashMap<String, Scanner>();
    }

    /**
     * @see org.ops4j.pax.runner.provision.ProvisionService#scan(String)
     */
    public InstallableBundles scan( final String spec )
        throws MalformedSpecificationException, ScannerException
    {
        LOGGER.info( "Provision from [" + spec + "]" );
        if( spec == null || spec.trim().length() == 0 )
        {
            throw new MalformedSpecificationException( "Specification cannot be null or empty" );
        }
        if( !spec.contains( ServiceConstants.SCHEME_SEPARATOR ) )
        {
            throw new UnsupportedSchemaException( "Provisioning scheme is not specified" );
        }
        String scheme = spec.substring( 0, spec.indexOf( ServiceConstants.SCHEME_SEPARATOR ) );
        String path = spec.substring( spec.indexOf( ServiceConstants.SCHEME_SEPARATOR ) + 1 );
        Scanner scanner = m_scanners.get( scheme );
        if( scanner == null )
        {
            throw new UnsupportedSchemaException( "Unknown provisioning scheme [" + scheme + "]" );
        }
        return wrap( scan( scanner, path ) );
    }

    /**
     * Wraps a list of bundle refrences as installables. The methods could be overrided by subclasses.
     *
     * @param bundleReferences a list of bundle references
     *
     * @return a set of installables
     */
    InstallableBundles wrap( final List<BundleReference> bundleReferences )
    {
        List<InstallableBundle> installables = new ArrayList<InstallableBundle>();
        if( bundleReferences != null )
        {
            for( BundleReference reference : bundleReferences )
            {
                installables.add( wrap( reference ) );
            }
        }
        return createSet( installables );
    }

    /**
     * Creates a new installable set. The methods could be overrided by subclasses.
     *
     * @param installables a list of installables that makes up the set.
     * @return The installable bundles as an Iterable.
     */
    InstallableBundles createSet( final List<InstallableBundle> installables )
    {
        return new InstallableBundlesImpl( installables );
    }

    /**
     * Wrap a bundle reference as installable.
     *
     * @param reference the bundle reference to be wrapped
     *
     * @return an installable
     */
    InstallableBundle wrap( final BundleReference reference )
    {
        return new InstallableBundleImpl( m_bundleContext, reference, m_startLevelService );
    }

    /**
     * Uses the scanner to scan the bundles.
     *
     * @param scanner the scanner to use
     * @param path    the path part of the specification
     * @return A List of bundle references found by the scanner.
     * @throws ScannerException TODO
     * @throws MalformedSpecificationException TODO
     */
    private List<BundleReference> scan( final Scanner scanner, final String path )
        throws ScannerException, MalformedSpecificationException
    {
        List<BundleReference> references = scanner.scan( path );
        if( LOGGER.isInfoEnabled() )
        {
            if( references != null )
            {
                for( BundleReference reference : references )
                {
                    LOGGER.info( "Installing bundle [" + reference + "]" );
                }
            }
            else
            {
                LOGGER.warn( "Scanner did not return any bundle to install for [" + references + "]" );
            }
        }
        return references;
    }

    /**
     * Adds a new scanner.
     *
     * @param scheme  the scheme the scanner handles
     * @param scanner the scanner
     */
    public void addScanner( final Scanner scanner, final String scheme )
    {
        Assert.notNull( "Scheme", scheme );
        Assert.notNull( "Scanner", scanner );
        synchronized( m_scanners )
        {
            m_scanners.put( scheme, scanner );
        }
        LOGGER.debug( "Added scheme [" + scheme + "] from scanner [" + scanner + "]" );
    }

    /**
     * Removes a scanner.
     *
     * @param scanner the scanner
     */
    public void removeScanner( final Scanner scanner )
    {
        Assert.notNull( "Scanner", scanner );
        synchronized( m_scanners )
        {
            for( Map.Entry<String, Scanner> entry : m_scanners.entrySet() )
            {
                if( scanner == entry.getValue() )
                {
                    m_scanners.remove( entry.getKey() );
                    LOGGER.debug( "Removed scheme [" + entry.getKey() + "] scanner [" + scanner + "]" );
                }
            }
        }
    }

    /**
     * Sets the start level service.
     *
     * @param startLevelService a start level service
     */
    public void setStartLevelService( final StartLevel startLevelService )
    {
        m_startLevelService = startLevelService;
    }

}
